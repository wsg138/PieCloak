# PieCloak entity visibility hardening audit — 2026-09-22

Base reviewed: `main` at `6b552bda324f5240967dab333e4a14d76bebe235`.
Upstream reference reviewed: `Cubicake/RaycastedAntiESP` through `1fcec23a57cad64886713e67dcc6b0e16bdbfa98`, plus targeted review of accepted upstream issues #88 and #94.

## Confirmed findings

### 1. Entity raycast radius was effectively one step too large — fixed

The old raycast subtracted its one-block step from the real target distance before applying `always-show-radius` and `raycast-radius`. A target at 48.5 blocks could therefore pass a configured 48-block boundary.

The hardening branch now applies both radius decisions to the real viewer-to-target distance. Regression tests cover the strict maximum-radius and always-show-radius boundaries.

### 2. A managed passenger riding a bypassed vehicle was force-shown — fixed

A managed target could escape normal visibility policy by riding an entity type PieCloak intentionally bypassed. The old relationship path could force-reveal that passenger, and the async engine separately treated a bypassed vehicle as an attachment-based visibility exception.

The branch now retains the authoritative relationship internally but sends bypassed-vehicle passenger state only for passengers already valid on that client. Hidden managed passengers are no longer revealed merely because their vehicle is bypassed, and normal SHOW replay remounts them once independently visible.

Regression coverage includes vehicle-known, passenger-late, and bypassed-vehicle-late ordering.

### 3. Approximate one-block ray sampling could false-show diagonal/corner targets — fixed

The prior one-block sampler deliberately accepted missed corner voxels as visible. The branch now performs exact center-ray voxel traversal, excludes viewer/target endpoint voxels from self-occlusion, handles simultaneous axis crossings, and applies `max-occluding-count` directly to real intermediate occluders.

Regression tests cover a diagonal voxel missed by the old sampler, target-voxel exclusion, and block-target threshold semantics.

### 4. Glowing entities bypass normal radius/occlusion hiding — intentional exception

Minecraft's entity metadata glowing flag is still an explicit force-visible condition before the ordinary radius/raycast decision. This is a policy exception, not the same thing as the `glow_item_frame` entity type.

### 5. Coordinate-based positional-sound protection is not implemented — configuration made fail-honest

PieCloak exposes `checks.sound-effects`, but ordinary coordinate-based clientbound sound packets do not provide a reliable managed-entity identity for the existing controller. That means hidden activity can still sometimes be inferred through positional sound even though the entity itself is hidden.

The bundled configuration now sets `checks.sound-effects.enabled: false` rather than claiming protection which is not implemented. Entity-bound sound packets are handled separately below.

### 6. Visibility repair packets could be emitted inside an unrelated protocol bundle — fixed

Upstream issue #88 described disconnects caused by injecting repair traffic while another Minecraft packet bundle was open. PieCloak also had fork-specific direct SHOW/HIDE paths, retries, block retries and tile repairs which needed the same invariant.

The branch now defers/coalesces entity and block repair traffic until the outer bundle closes and fences delayed block callbacks to the world epoch that scheduled them. The direct movement-boundary path also suppresses a triggering movement packet when a direct SHOW already respawns/synchronizes the entity at its new position, avoiding double movement client-side.

### 7. `COLLECT_ITEM` could expose hidden entities — fixed

Upstream issue #94 is a real Java-client side channel: the packet can reference an entity absent from the client's entity set and reveal hidden activity.

PieCloak now suppresses `COLLECT_ITEM` when either managed reference is logically hidden or not yet client-visible, including SHOW-transition races. Self and unmanaged/bypassed references remain valid.

The continuation review did not broaden this into blanket suppression for completely unknown, non-bypassed IDs. The current null-reference behavior is explicit in regression coverage, and no packet-order reproduction established that an otherwise unmanaged unknown ID should be treated as a hidden managed target.

### 8. Benchmark command sender binding was incorrect — fixed during runtime validation

The initial runtime benchmark appeared to hang, but JVM evidence showed the raycaster was not stuck. The command method's direct `Player` binding was the defect.

The benchmark now accepts `CommandSender`, explicitly checks/casts to `Player`, and has binding regression coverage. Subsequent isolated runs completed both small and 1,000-ray benchmarks.

### 9. WorldGuard platform access happened before WorldGuard was enabled — fixed during live integration validation

PieCloak correctly registered `piecloak-skip` during plugin load, but the initial integration constructor immediately accessed `WorldGuard.getPlatform()` before WorldGuard was enabled and triggered WorldGuard's lifecycle guard.

The integration now registers the flag during `onLoad` but resolves the WorldGuard platform/region container lazily only after the plugin is enabled. The later real WorldGuard fixture booted cleanly and passed all exemption transitions.

### 10. Entity-reference packet side channels — hardened after protocol review

The protocol pass found additional packets which could expose a hidden or not-yet-spawned entity by ID even when ordinary visibility packets were suppressed.

The branch now applies these rules:

- `ENTITY_SOUND_EFFECT`: suppress when the referenced managed entity is hidden or not yet client-visible; target-bypassed/self references remain valid, and a viewer whose connected session has `raycastedantiesp.bypass` is not filtered.
- `DAMAGE_EVENT`: always enforce visibility for the damaged target. When the packet has **no explicit source position**, vanilla resolves the optional cause/direct entity IDs, so PieCloak normalizes PacketEvents 2.12's wire values with `packetValue - 1` and checks those references too. When an explicit source position is present, vanilla uses that position and ignores cause/direct entity IDs, so PieCloak does not incorrectly suppress the event based on client-irrelevant IDs. Viewer bypass likewise leaves these references untouched.
- `SET_PASSENGERS`: withhold unresolved vehicle/passenger IDs from the client while preserving core unresolved state; filtered replacement packets contain only client-valid passengers and post-spawn replay restores the authoritative relationship.
- `ATTACH_ENTITY`: withhold unresolved leash endpoints while preserving the deferred relationship state needed for post-spawn reconstruction.

A final review caught a viewer-bypass regression in the new entity-reference policy: while viewer bypass is active, normal spawn tracking is deliberately skipped, so an entity which spawned during that interval could be untracked. The side-channel helper initially interpreted that untracked ID as hidden and could cancel a legitimate entity-bound sound or damage event for the bypassed viewer. The policy now takes viewer bypass into account before suppressing a reference.

The review also established two non-fixes:

- ordinary `PARTICLE` packets expose particle data plus coordinates, not a managed entity ID, so blanket entity-based suppression would be guesswork;
- server `VEHICLE_MOVE` synchronizes the receiving player's controlled vehicle and does not expose an arbitrary remote entity ID. PieCloak already force-shows a vehicle when required by the viewer's own attachment state.

Regression coverage includes hidden/not-client-visible entity-reference decisions, viewer-bypass handling for an untracked reference, damage-source offset decoding, explicit-position damage semantics, and unresolved relationship decisions.

### 11. Bypass permission is cached for the connected session — unresolved by design in this pass

The join path snapshots `raycastedantiesp.bypass` into `PlayerData`. The packet-reference hardening respects that cached viewer-bypass state, including entities which are intentionally not tracked while bypass is active.

A simple live permission poll is still not a safe fix because packet interception is skipped while bypassed, so entities which spawn during that interval may never enter the viewer's managed view. Revoking bypass in-place could therefore leave incomplete authoritative state.

A correct live grant/revoke feature needs continuous shadow tracking while bypassed or an explicit full client/view resynchronization contract. Until then, live bypass permission changes should be treated as reconnect-required.

### 12. PieCloak's target-filter fork accidentally bypassed modern player spawns — fixed

A continuation review found a pre-existing fork regression in `shouldBypassSpawn`. PieCloak's target filter intentionally returns `false` for players because configured entity-type filtering applies only to non-player entities; player visibility has its own `checks.player` policy. The forked spawn classifier nevertheless required `!isPlayer && managedByPieCloak` for the managed path.

On modern protocol versions where players arrive through `SPAWN_ENTITY`, a player therefore fell through to `EntityBypassRegistry.addEntity(...)`. Once that happened, normal movement/metadata/visibility packet handling for that player ID was bypassed. In practical terms this could disable player anti-ESP independently of the ordinary entity filter.

This defect is present on the reviewed `main` base and was not introduced by PR #13. Current upstream keeps players managed independently of normal entity exclusions. The branch now restores that separation: player spawns always enter the managed player path, while non-player entities are managed only when selected by PieCloak's entity target filter and not excluded upstream.

A regression test locks the classification contract for players, configured non-player targets, upstream-excluded targets, and unconfigured non-player entities.

## Item frame / armor stand conclusion

The configured entity names match PacketEvents registry names, and the bundled exclusions do not exclude item frames, glow item frames, or armor stands. A normal non-glowing, non-attached, non-plugin-bypassed instance should therefore enter the managed entity view.

After the radius and exact-traversal fixes, a remaining report of ordinary frames/stands visible far beyond policy should be investigated for exceptional state (glowing, attachment, plugin bypass), packet ordering, or reproduced directly against the hardened build. FancyHolograms/FancyNPCs intentionally bypass their own internal entity IDs and can explain plugin-owned armor stands, but not ordinary vanilla frames at a base.

## Block-entity audit conclusion

The normal 1.21.11 block-entity paths are comparatively defensive:

- chunk data is parsed/rewritten before send;
- single and multi-block changes substitute hidden managed state;
- standalone block-entity data fails closed when cached block/packet type establishes a managed target;
- SHOW/HIDE repairs use bounded retries;
- WorldGuard-exempt block entities are preserved in initial output rather than briefly hidden and repaired;
- deferred repairs are fenced by world epoch.

No normal 1.21.11 player-controlled block-entity reveal equivalent to the bypassed-vehicle flaw was proven. `MAP_CHUNK_BULK` remains a pass-through warning path but is not a normal 1.21.11 server packet and was not classified as a practical exploit without evidence it occurs on the production baseline.

## WorldGuard `piecloak-skip` exemption — implemented and live-tested

The branch adds an optional target-location exemption using a custom WorldGuard `piecloak-skip` state flag. Targets remain tracked so live region/flag changes and boundary exits retain authoritative state, while exempt checks short-circuit normal hiding/raycast work.

The implementation covers:

- flag registration during plugin load before WorldGuard locks the registry;
- soft-dependency classloading safety when WorldGuard is absent;
- lazy platform access after WorldGuard enable;
- moving entity enter/exit transitions;
- bounded stationary re-evaluation after flag changes;
- initial chunk/block preservation for exempt managed block entities;
- raycast short-circuiting for exempt targets.

### Real WorldGuard transition proof

The complete real WorldGuard fixture was run against PieCloak SHA `8b2197a0169664dad1b78e886b59867652292627` on Paper 1.21.11 + PacketEvents 2.12.0 + WorldEdit 7.4.2 + WorldGuard 7.0.17.

Observed stages:

1. initial `ALLOW`: entities `3 (2 visible / 1 hidden)`, block entities `2 (1 visible / 1 hidden)`;
2. hidden villager moved into flagged region: entities `3 (3 visible / 0 hidden)`;
3. villager moved back outside: entities `3 (2 visible / 1 hidden)`;
4. flag changed to `DENY`: entities `3 (1 visible / 2 hidden)`, block entities `2 (0 visible / 2 hidden)`;
5. `ALLOW` restored: entities `3 (2 visible / 1 hidden)`, block entities `2 (1 visible / 1 hidden)`.

That exact-head run also completed `/raesp benchmark 48 1000` in 15.728 ms total, config reload, client disconnect, clean plugin shutdown and graceful Paper shutdown. These Pi timing numbers are staging measurements, not production-capacity guarantees.

Later changes alter packet-reference handling and player spawn classification but do not alter WorldGuard query/transition logic. The final candidate still receives a separate exact-head Paper/PacketEvents smoke.

## Additional runtime cleanup

The obsolete generic `minecraft:potion` exclusion was removed. Paper 1.21.11 uses separate splash/lingering potion entity types, both already present in the exclusion set. Existing customized configs containing the old name remain safe because unknown names are warned and skipped.

## Remaining hardening/design work

### Missing occlusion-section data is fail-open

An absent occlusion section currently answers “not occluding.” Hidden-on-spawn behavior limits the obvious race window, so this was not classified as a reproduced leak. A dedicated packet-order/concurrency fixture should prove whether a visible target can be evaluated while required section data is absent before changing this policy.

### Coordinate-only activity side channels

The concrete entity-ID surfaces reviewed here are covered: `COLLECT_ITEM`, entity-bound sounds, `DAMAGE_EVENT`, and pre-spawn passenger/leash references. `VEHICLE_MOVE` was reviewed and is not an arbitrary remote-entity reference.

The remaining known surface is primarily coordinate-only activity, especially ordinary positional sounds. Particle packets are similarly coordinate/data based. Do not suppress these blindly; future protection needs a reliable source-attribution contract so unrelated world effects are not broken.

### Diagnostics

Per-target diagnostics should be a separate focused package, not coupled to this hardening PR. Useful entity diagnostics should expose viewer/world epoch, target identity/type, real distance, managed/bypass state and reason, engine/client visibility, glowing, WorldGuard exemption, relationships, radius/raycast result and pending transition/retry state. Equivalent block diagnostics should include material/state ID, tracking/classification, exemption, section availability, visibility/raycast result and pending repair state.

## Upstream sync policy

Continue selective provenance-based ports rather than merging upstream `main` wholesale. High-value results from this review were strict radius semantics, issue #88 bundle handling and issue #94 hidden-reference handling. Large upstream raycast/chunk-parser refactors still need PieCloak-specific evaluation because this fork's filtering/reliability contracts differ.

## Validation status

Successive product heads before the player-spawn classification repair passed repository Build/tests/staging-JAR inspection, PMD/Semgrep/Trivy, external Codacy, CodeRabbit, isolated Paper runtime smoke, and the real WorldGuard fixture above.

The latest product-code head before this report reconciliation is `775f0b83b6a7efafcd30c7c8e515418fb24e4dfb`. It restores managed modern player spawns and adds a dependency-free classification regression test. This report commit intentionally freezes the documented candidate after that code change.

Temporary staging workflows/scripts are test infrastructure only and must be removed after evidence capture. The final report-reconciled head must pass the normal exact-head Build/static/external checks, the ordinary Paper 1.21.11 + PacketEvents runtime smoke, and a targeted two-client player-visibility runtime probe before final handoff.