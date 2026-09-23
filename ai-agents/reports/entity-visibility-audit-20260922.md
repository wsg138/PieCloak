# PieCloak entity visibility hardening audit — 2026-09-22

Base reviewed: `main` at `6b552bda324f5240967dab333e4a14d76bebe235`.
Upstream reference reviewed: `Cubicake/RaycastedAntiESP` through `1fcec23a57cad64886713e67dcc6b0e16bdbfa98`, plus targeted review of accepted upstream issues #88 and #94.

## Confirmed findings

### 1. Entity raycast radius was effectively one step too large — fixed

`RaycastUtil.raycast(...)` subtracted the one-block ray step from the real target distance before comparing the result with `always-show-radius` and `raycast-radius`.

With a one-block step this allowed a target slightly beyond a configured 48-block maximum (for example 48.5 blocks away) to pass the radius gate. It also enlarged the always-show radius by the same mechanism.

The hardening branch now performs both radius decisions against the real start-to-target distance. Regression tests cover both boundaries.

This is directly relevant to reports of entities being visible just beyond 48 blocks. It does not explain entities visible substantially farther than roughly one extra step.

### 2. A managed passenger riding a bypassed vehicle was force-shown — fixed

A managed target could escape normal anti-ESP policy by becoming a passenger of an entity type which PieCloak intentionally bypasses. The clearest normal-gameplay example is an allowlisted villager riding a non-managed boat.

The old relationship path explicitly called `forceVisibleBecauseAttached(...)` for tracked passengers of bypassed vehicles, and the async visibility engine separately treated a bypassed vehicle ID as an always-visible attachment reason. This meant the passenger could be shown regardless of normal radius/occlusion state.

The hardening branch now:

- retains the full authoritative passenger relationship internally;
- rewrites a bypassed vehicle's outgoing passenger list to include only passengers already client-visible;
- does not directly reveal a hidden managed passenger merely because its vehicle is bypassed;
- removes the async-engine bypassed-vehicle visibility exception;
- keeps the legitimate SHOW replay path, which remounts the passenger when it later becomes independently visible.

Regression coverage exercises immediate, passenger-late, and bypassed-vehicle-late relationship ordering without force-revealing the managed passenger.

### 3. Approximate one-block ray sampling could false-show diagonal/corner targets — fixed

The previous raycast was a one-block stepping sampler whose contract explicitly accepted missing corner blocks and treated those misses as visible. That was a performance tradeoff, but it meant the visibility system was not mathematically strict for all diagonal/corner geometries.

The hardening branch now uses exact voxel traversal of the center ray between viewer and target. The viewer's start voxel and the target voxel are intentionally excluded so neither endpoint can occlude itself, and simultaneous axis crossings are handled together. The configured `max-occluding-count` now applies directly to real intermediate occluding voxels, so block targets no longer need the old caller-side `+1` compensation.

Regression coverage includes a diagonal voxel skipped by the old one-block sampler, target-voxel self-occlusion, and the block-target occluder threshold.

### 4. Glowing entities bypass normal radius/occlusion hiding — intentional exception

Managed entities with the Minecraft glowing metadata flag are force-shown before the normal raycast/radius decision. This is consistent with vanilla glowing semantics, but it is an explicit exception to a policy such as “anything beyond 48 blocks is always hidden.”

This refers to the entity metadata glowing flag, not the `glow_item_frame` entity type by itself.

### 5. Configured positional-sound protection is not currently enforced — configuration made fail-honest

PieCloak exposes a `checks.sound-effects` policy with occlusion/radius fields, but no active packet controller consumes that policy. PacketEvents' clientbound positional sound packet contains explicit effect coordinates.

As a result, a hidden entity or block entity can still have activity inferred through positional sounds when the server emits an ordinary coordinate-based sound packet. This is an information side channel rather than a direct entity-spawn leak.

The bundled configuration now sets `checks.sound-effects.enabled: false` instead of implying protection that is not actually implemented. A dedicated packet-semantic implementation and tests are still needed before this protection can truthfully be enabled.

### 6. Visibility repair packets could be emitted inside an unrelated protocol bundle — fixed

Upstream issue #88 documented client disconnects caused by anti-ESP repair packets being emitted while Minecraft was inside another packet bundle. PieCloak initially lacked upstream's bundle-state guard, and the fork also has direct SHOW/HIDE paths, bounded entity retries, block retries, and tile-mode repairs which upstream's smaller fix does not fully cover.

The hardening branch now tracks per-viewer clientbound bundle state and prevents PieCloak repair traffic from being injected into an unrelated open bundle:

- ordinary entity transition drains are deferred until the outer bundle closes;
- direct entity SHOW/HIDE repairs are coalesced and deferred while a bundle is open;
- entity retry work does not run inside the unrelated bundle;
- tile-check mode changes, block transition retries, and block SHOW/HIDE repairs are held until the closing delimiter has actually been sent;
- deferred block repair callbacks are fenced to the world epoch that scheduled them, so a respawn/world transition cannot make an old-world callback write stale block state.

The moving-entity exemption boundary path was reviewed alongside this change: if a hidden entity is directly repaired after a relative movement update, the triggering movement packet is suppressed so the client does not apply the same movement twice.

### 7. `COLLECT_ITEM` could expose hidden entities — fixed

Upstream issue #94 is an accepted, reproducible Java-client side channel: when a hidden collector picks up an item, the clientbound `COLLECT_ITEM` packet can reference a collector entity that is absent from the viewer's client. On Java this can produce a misleading item-pickup animation toward the local viewer and reveals activity associated with the hidden target.

PieCloak now suppresses `COLLECT_ITEM` for managed references when either the collected entity or collector is:

- logically hidden by PieCloak; or
- not currently client-visible, including a SHOW transition which has not completed yet.

This deliberately checks both engine/logical visibility and client visibility. Unknown/unmanaged references are not guessed hidden, and the viewer's own entity remains valid as a collector.

### 8. Benchmark command sender binding was incorrect — fixed during runtime validation

The first isolated runtime probes appeared to show even tiny raycast benchmarks hanging. A JVM thread dump showed that the raycaster itself was not stuck. The actual defect was command binding: the generated command method accepted `Player` directly, which did not reliably bind/execute as intended in the live Paper command path.

The benchmark command now accepts `CommandSender`, explicitly requires a `Player`, and casts only after the runtime type check. A command-binding regression test covers this signature.

After the fix, the isolated Pi staging host completed:

- 10 rays within 48 blocks in 0.539 ms total (53,935 ns/ray) without WorldGuard; and
- 1,000 rays within 48 blocks in 24.901 ms total (24,901 ns/ray) during the real WorldGuard integration probe.

Those numbers are environment-specific measurements on a Raspberry Pi staging host, not production capacity guarantees.

### 9. WorldGuard platform access happened before WorldGuard was enabled — fixed during live integration validation

The first real WorldGuard 7.0.17 integration boot found a lifecycle defect which unit tests did not expose. PieCloak correctly registered `piecloak-skip` during plugin load, but the WorldGuard exemption constructor immediately called `WorldGuard.getPlatform().getRegionContainer()`. At that point WorldGuard was loaded but not yet enabled, and WorldGuard threw `NullPointerException: WorldGuard is not enabled, unable to access the platform.`

The integration now deliberately separates the two lifecycle requirements:

- custom flag registration still happens during PieCloak `onLoad`, before WorldGuard locks its flag registry;
- the WorldGuard platform/region container is resolved lazily only after the WorldGuard plugin reports enabled.

A subsequent real integration run with Paper 1.21.11, WorldEdit 7.4.2, WorldGuard 7.0.17, PacketEvents 2.12.0, and the synthetic protocol-774 Java client booted cleanly and passed the complete exemption transition fixture.

### 10. Bypass permission is cached for the connected session — unresolved by design in this pass

The Paper join handler snapshots `raycastedantiesp.bypass` into `PlayerData`, and no safe live permission-change refresh path was found.

A simple periodic permission poll is not a complete fix. While bypass is active, managed packet interception is deliberately skipped, so entities which spawn during that bypass window may never enter the viewer's managed entity view. If bypass were then revoked in-place, forcing a visibility recheck would still lack authoritative tracked state for those entities. In addition, a configured visible recheck interval of `-1` can leave entities revealed during bypass visible indefinitely unless a one-time recomputation is explicitly forced.

A correct live grant/revoke feature therefore needs either continuous shadow tracking while bypassed or an explicit full client/view resynchronization contract. Until that larger state-machine change exists, live bypass changes should be treated as requiring reconnect rather than partially refreshing only the cached boolean.

## Item frame / armor stand conclusion

The configured entity names match PacketEvents' registry names, and the bundled exclusions do not exclude item frames, glow item frames, or armor stands. The live server configuration was also confirmed by the operator to include them. A normal non-glowing, non-attached, non-plugin-bypassed instance of one of these types should enter the managed entity view.

Once managed, the normal state machine starts distant spawns hidden and repeatedly rechecks hidden entities. After the radius-boundary fix, the configured maximum radius is strict, and the exact voxel traversal removes the old sampler's deliberate diagonal/corner misses. Therefore a remaining report of ordinary frames/stands visible much farther away requires checking exceptional state (glowing, attachment, explicit plugin bypass), packet-order behavior, or reproducing the report against the hardened build.

FancyHolograms and FancyNPCs intentionally register their own entity IDs in the bypass registry. This can explain plugin-owned armor stands or hologram internals, but it does not explain ordinary vanilla item frames at a normal base.

The bypassed-vehicle exploit is a genuine separate entity visibility flaw, but it does not explain ordinary wall-mounted item frames.

## Block-entity audit conclusion

The normal 1.21.11 block-entity paths are comparatively defensive:

- chunk data is parsed and rewritten before send;
- single block changes hide newly tracked managed tile entities before the real state reaches the client;
- multi-block changes replace hidden managed state IDs;
- standalone block-entity data without tracked tile state fails closed when either the cached block or packet type establishes that the target is managed;
- SHOW/HIDE repairs use bounded transition retries;
- WorldGuard-exempt block entities are preserved as real block state in initial chunk/block output rather than being briefly replaced and repaired later;
- deferred repair callbacks are rejected after world-epoch changes.

No normal-protocol player-controlled block-entity reveal equivalent to the bypassed-vehicle entity flaw was proven in this audit.

`MAP_CHUNK_BULK` is currently passed through unchanged with a warning, but that is not a normal 1.21.11 server packet path and is not classified here as a practical exploit without evidence that it can occur on the production baseline.

## WorldGuard `piecloak-skip` exemption — implemented and live-tested

The hardening branch adds an optional WorldGuard target-location exemption using the custom `piecloak-skip` state flag. Targets in an effective flagged region bypass normal PieCloak hiding and raycast work.

The integration is designed around the target's location, not the viewer's region:

- the flag is registered during plugin load before WorldGuard locks its flag registry;
- WorldGuard remains a soft/optional dependency, and the main plugin class does not expose concrete WorldGuard types which would break class loading when WorldGuard is absent;
- WorldGuard platform access is lazy, so registration during `onLoad` does not require WorldGuard to be enabled yet;
- entities entering an exempt region are directly revealed and their triggering movement packet is suppressed when necessary to avoid double-applying movement after respawn/sync;
- entities leaving an exempt region immediately return to the normal visibility decision path;
- stationary visible entities are re-evaluated at a bounded interval so removing/changing the region flag does not leave them exempt for an arbitrarily long configured visible-recheck interval;
- exempt checks short-circuit the raycast itself;
- initial chunk/block parsing preserves exempt managed block entities as their real state instead of hiding them first and repairing them afterward;
- an absent-WorldGuard classloading regression test covers the optional dependency boundary.

Targets intentionally remain tracked inside the region. Completely dropping them from tracking would make moving entities and live flag changes unsafe because PieCloak would lose the authoritative state needed when a target exits the exempt region. The implemented design avoids raycast work while exempt while preserving enough state for safe boundary transitions.

### Real WorldGuard transition proof

A disposable loopback-only Paper 1.21.11 staging run used WorldEdit 7.4.2 and WorldGuard 7.0.17 with a real op player creating a region through WorldEdit/WorldGuard commands:

- `//pos1 25,-64,-4`
- `//pos2 35,20,4`
- `/rg define piecloak_probe`
- `/rg flag piecloak_probe piecloak-skip allow`

The fixture placed a near visible villager, a wall, a flagged villager and campfire behind the wall, and equivalent unflagged targets outside the region. The exact observed stages were:

1. initial `ALLOW`: entities `3 (2 visible / 1 hidden)`, block entities `2 (1 visible / 1 hidden)`;
2. normally hidden villager moved into region: entities `3 (3 visible / 0 hidden)`;
3. villager moved back outside: entities `3 (2 visible / 1 hidden)`;
4. flag changed to `DENY`: entities `3 (1 visible / 2 hidden)`, block entities `2 (0 visible / 2 hidden)`;
5. `ALLOW` restored: entities `3 (2 visible / 1 hidden)`, block entities `2 (1 visible / 1 hidden)`.

The same run completed the player-backed 1,000-ray benchmark, config reload, client disconnect, and graceful Paper shutdown.

This establishes the integration behavior on the isolated staging fixture. Production-load profiling can still be useful before deployment, but WorldGuard correctness is no longer an untested pre-merge assumption.

## Additional cleanup from runtime review

The bundled entity exclusions contained an obsolete generic `minecraft:potion` entry. Paper 1.21.11 exposes separate splash and lingering potion entity types, both of which were already present in the exclusions. The invalid redundant entry was removed so fresh 1.21.11 startup no longer emits an avoidable “unknown entity type” warning.

Existing customized configs which still contain that old entry remain safe: the resolver warns and skips unknown entries.

## Hardening candidates requiring more proof or design

### Missing chunk section data is fail-open for occlusion

An untracked/missing occlusion section currently answers “not occluding.” Hidden-on-spawn behavior limits the obvious race window, so this audit does not classify that alone as a reproduced leak. A dedicated packet-order/concurrency test should establish whether a visible entity can be rechecked while required section data is absent before changing the policy.

### Remaining packet side channels

`COLLECT_ITEM` is now covered, but packet coverage remains part of the anti-information-leak surface. Continue reviewing packets that reference entity IDs or reveal entity-associated location/activity without going through the managed visibility gate, especially entity sounds, coordinate-based sounds, damage/particle events, pre-spawn passenger/leash relationships, and vehicle-specific synchronization.

Do not suppress these blindly: each packet needs its protocol semantics and client behavior established first, particularly where an unknown/unmanaged entity ID may legitimately pass through PieCloak.

## Diagnostics design

Add a per-target inspection command rather than relying only on aggregate `/raesp stats` and `/raesp debugplayer`.

Recommended entity output:

- viewer, world and world epoch;
- entity ID, UUID and type;
- real viewer distance;
- allowlist result and upstream exclusion result;
- bypass state and reason/source;
- managed view membership;
- engine-visible and client-visible state;
- last-checked tick;
- glowing state;
- WorldGuard exemption state;
- vehicle/passenger/leash relationships;
- current radius result and raycast/occlusion result;
- pending visibility transition/retry state.

Recommended block-entity output:

- material and PacketEvents state ID;
- target-filter decision;
- block-entity classification/status;
- tracked tile state and last-checked tick;
- visibility state;
- WorldGuard exemption state;
- whether the containing section is loaded in the occlusion view;
- current radius and raycast result;
- pending block transition/retry state.

For useful bypass diagnostics, evolve `EntityBypassRegistry` so diagnostic state records why an ID is bypassed while preserving a cheap hot-path membership check. WorldGuard exemption should be reported separately because it is a location policy rather than an entity-ID bypass source.

The earlier diagnostic-command prototype was intentionally removed from this hardening PR after it expanded scope and static-analysis complexity. Diagnostics should be implemented as a separate focused package rather than coupled to the visibility fixes.

## Upstream sync policy

The reviewed upstream baseline is substantially ahead of PieCloak's historical import point. The delta contains meaningful fixes, but also large refactors and behavior changes that conflict with fork-specific design. Continue selective provenance-based ports rather than merging upstream `main` wholesale.

High-value selective results from this review:

1. strict radius semantics — ported and regression-tested;
2. issue #88 bundle-boundary transition handling — selectively ported and extended across PieCloak's direct/retry/block repair paths;
3. issue #94 `COLLECT_ITEM` hidden-reference handling — selectively implemented using PieCloak's logical and client visibility state;
4. allocation/performance improvements — evaluate against the exact traversal without weakening its correctness contract;
5. large upstream raycast/chunk-parser rewrites — do not import blindly because PieCloak's target filtering and reliability behavior diverge.

## Validation status

The hardening work has passed:

- normal repository Build, tests, staging-JAR inspection and artifact generation on successive exact heads;
- PMD, Semgrep CE, Trivy and external Codacy quality gates;
- targeted core/PacketEvents/Paper regression suites for radius, exact traversal, relationship ordering, bundle boundaries, block retries, world-epoch fencing, WorldGuard policy and `COLLECT_ITEM`;
- isolated Paper 1.21.11 + PacketEvents runtime smoke with a synthetic protocol-774 Java player;
- real WorldGuard 7.0.17 + WorldEdit 7.4.2 integration with five exact exemption/flag-transition states;
- player-backed raycast benchmarks on the isolated ARM64 Pi staging host.

The runtime process itself found and caused fixes for two defects which ordinary unit/CI coverage did not expose: benchmark command sender binding and WorldGuard platform access before WorldGuard enable. That is why the live integration gate was retained instead of treating green unit tests as sufficient.

Temporary staging workflows/scripts are test infrastructure only and are removed before final handoff. The final merge candidate must still be judged by the normal checks on its exact cleaned PR head and, because build metadata embeds the commit SHA, one final exact-head smoke of the produced JAR.
