# PieCloak entity visibility hardening audit — 2026-09-22

Base reviewed: `main` at `6b552bda324f5240967dab333e4a14d76bebe235`.
Upstream reference reviewed: `Cubicake/RaycastedAntiESP` through `1fcec23a57cad64886713e67dcc6b0e16bdbfa98`, plus targeted review of accepted upstream issues #88 and #94.

## Confirmed findings

### 1. Entity raycast radius was effectively one step too large — fixed on this branch

`RaycastUtil.raycast(...)` subtracted the one-block ray step from the real target distance before comparing the result with `always-show-radius` and `raycast-radius`.

With a one-block step this allowed a target slightly beyond a configured 48-block maximum (for example 48.5 blocks away) to pass the radius gate. It also enlarged the always-show radius by the same mechanism.

The hardening branch now performs both radius decisions against the real start-to-target distance. Regression tests cover both boundaries.

This is directly relevant to reports of entities being visible just beyond 48 blocks. It does not explain entities visible substantially farther than roughly one extra step.

### 2. A managed passenger riding a bypassed vehicle was force-shown — fixed on this branch

A managed target could escape normal anti-ESP policy by becoming a passenger of an entity type which PieCloak intentionally bypasses. The clearest normal-gameplay example is an allowlisted villager riding a non-managed boat.

The old relationship path explicitly called `forceVisibleBecauseAttached(...)` for tracked passengers of bypassed vehicles, and the async visibility engine separately treated a bypassed vehicle ID as an always-visible attachment reason. This meant the passenger could be shown regardless of normal radius/occlusion state.

The hardening branch now:

- retains the full authoritative passenger relationship internally;
- rewrites a bypassed vehicle's outgoing passenger list to include only passengers already client-visible;
- does not directly reveal a hidden managed passenger merely because its vehicle is bypassed;
- removes the async-engine bypassed-vehicle visibility exception;
- keeps the existing legitimate SHOW replay path, which remounts the passenger when it later becomes independently visible.

Regression coverage exercises immediate, passenger-late, and bypassed-vehicle-late relationship ordering without force-revealing the managed passenger.

### 3. Approximate one-block ray sampling could false-show diagonal/corner targets — fixed on this branch

The previous raycast was a one-block stepping sampler whose contract explicitly accepted missing corner blocks and treated those misses as visible. That was a performance tradeoff, but it meant the visibility system was not mathematically strict for all diagonal/corner geometries.

The hardening branch now uses exact voxel traversal of the center ray between viewer and target. The viewer's start voxel and the target voxel are intentionally excluded so neither endpoint can occlude itself, and simultaneous axis crossings are handled together. The configured `max-occluding-count` now applies directly to real intermediate occluding voxels, so block targets no longer need the old caller-side `+1` compensation.

Regression coverage includes a diagonal voxel skipped by the old one-block sampler, target-voxel self-occlusion, and the block-target occluder threshold. This is a stricter algorithm and must still be profiled under realistic live entity counts before merge because correctness has been prioritized over the old approximate sampler's speed tradeoff.

### 4. Glowing entities bypass normal radius/occlusion hiding

Managed entities with the Minecraft glowing metadata flag are force-shown before the normal raycast/radius decision. This is consistent with vanilla glowing semantics, but it is an explicit exception to a policy such as “anything beyond 48 blocks is always hidden.”

This refers to the entity metadata glowing flag, not the `glow_item_frame` entity type by itself.

Diagnostics should report this state so a glow-related exception is immediately visible during testing.

### 5. Configured positional-sound protection is not currently enforced — configuration made fail-honest on this branch

PieCloak exposes a `checks.sound-effects` policy with occlusion/radius fields, but no active packet controller consumes that policy. PacketEvents' clientbound positional sound packet contains explicit effect coordinates.

As a result, a hidden entity or block entity can still have activity inferred through positional sounds when the server emits an ordinary coordinate-based sound packet. This is an information side channel rather than a direct entity-spawn leak, but it matters for a strict anti-ESP threat model.

The bundled configuration now sets `checks.sound-effects.enabled: false` instead of implying protection that is not actually implemented. A dedicated packet-semantic implementation and tests are still needed before this protection can truthfully be enabled; network sound coordinates must be interpreted correctly before applying ray policy.

### 6. Visibility repair packets could be emitted inside an unrelated protocol bundle — fixed on this branch

Upstream issue #88 documented client disconnects caused by anti-ESP repair packets being emitted while Minecraft was inside another packet bundle. PieCloak initially lacked upstream's bundle-state guard, and the fork also has direct SHOW/HIDE paths, bounded entity retries, block retries, and tile-mode repairs which upstream's smaller fix does not fully cover.

The hardening branch now tracks per-viewer clientbound bundle state and prevents PieCloak repair traffic from being injected into an unrelated open bundle:

- ordinary entity transition drains are deferred until the outer bundle closes;
- direct entity SHOW/HIDE repairs are coalesced and deferred while a bundle is open;
- entity retry work does not run inside the unrelated bundle;
- tile-check mode changes, block transition retries, and block SHOW/HIDE repairs are held until the closing delimiter has actually been sent;
- deferred block repair callbacks are fenced to the world epoch that scheduled them, so a respawn/world transition cannot make an old-world callback write stale block state.

The moving-entity exemption boundary path was reviewed alongside this change: if a hidden entity is directly repaired after a relative movement update, the triggering movement packet is suppressed so the client does not apply the same movement twice.

Focused core, PacketEvents, and Paper tests passed for the adapted bundle behavior and the stale callback fence before temporary validation tooling was removed.

### 7. `COLLECT_ITEM` could expose hidden entities — fixed on this branch

Upstream issue #94 is an accepted, reproducible Java-client side channel: when a hidden collector picks up an item, the clientbound `COLLECT_ITEM` packet can reference a collector entity that is absent from the viewer's client. On Java this can produce the misleading item-pickup animation toward the local viewer and reveals activity associated with the hidden player.

PieCloak now suppresses `COLLECT_ITEM` for managed references when either the collected entity or collector is:

- logically hidden by PieCloak; or
- not currently client-visible, including a SHOW transition which has not completed yet.

This deliberately checks both engine/logical visibility and client visibility. That matters when `keep-client-entity-when-hidden` retains an entity client-side: the entity ID may still exist locally even though activity associated with that hidden target must not be forwarded. Unknown/unmanaged references are not guessed hidden, and the viewer's own entity remains valid as a collector.

Focused core, PacketEvents, and Paper tests passed before the fix was promoted to the branch.

### 8. Bypass permission is cached for the connected session — unresolved by design in this pass

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

## WorldGuard `piecloak-skip` exemption — implemented on this branch

The hardening branch adds an optional WorldGuard target-location exemption using the custom `piecloak-skip` state flag. Targets in an effective flagged region bypass normal PieCloak hiding and raycast work.

The integration is designed around the target's location, not the viewer's region:

- the flag is registered during WorldGuard's flag-registration phase;
- WorldGuard remains a soft/optional dependency, and the main plugin class no longer exposes concrete WorldGuard types which could break class loading when WorldGuard is absent;
- entities entering an exempt region are directly revealed and their triggering movement packet is suppressed when necessary to avoid double-applying movement after respawn/sync;
- entities leaving an exempt region immediately return to the normal visibility decision path;
- stationary visible entities are re-evaluated at a bounded interval so removing the region flag does not leave them exempt for an arbitrarily long configured visible-recheck interval;
- exempt checks short-circuit the raycast itself;
- initial chunk/block parsing preserves exempt managed block entities as their real state instead of hiding them first and repairing them afterward;
- an absent-WorldGuard classloading regression test covers the optional dependency boundary.

This implementation still deserves realistic production profiling in regions with high target density. Region lookups are now on visibility/block-target decision paths, so correctness is established by tests but live cost must be measured on the actual server workload.

## Hardening candidates requiring more proof or design

### Missing chunk section data is fail-open for occlusion

An untracked/missing occlusion section currently answers “not occluding.” Hidden-on-spawn behavior limits the obvious race window, so this audit does not classify that alone as a reproduced leak. A dedicated packet-order/concurrency test should establish whether a visible entity can be rechecked while required section data is absent before changing the policy.

### Exact raycasting and WorldGuard runtime validation

Exact voxel traversal and WorldGuard exemption handling are implemented and covered by correctness tests. Before merge, benchmark/profile them under realistic production player/entity/block-entity counts and representative flagged regions. Do not trade correctness back to approximate sampling merely to recover a synthetic microbenchmark result; if the exact implementation is too expensive, optimize state representation and hot-path allocation/profile behavior while preserving the exact traversal contract.

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
- bypass state **and reason/source**;
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

For useful bypass diagnostics, evolve `EntityBypassRegistry` so diagnostic state records why an ID is bypassed (for example target-filter exclusion, entity-type exclusion, FancyHolograms, FancyNPCs, or relationship support) while preserving a cheap hot-path membership check. WorldGuard exemption should be reported separately because it is a location policy rather than an entity-ID bypass source.

The earlier diagnostic-command prototype was intentionally removed from this hardening PR after it expanded scope and static-analysis complexity. Diagnostics should be implemented as a separate focused package rather than coupled to the visibility fixes.

## Upstream sync policy

The reviewed upstream baseline is substantially ahead of PieCloak's historical import point. The delta contains meaningful fixes, but also large refactors and behavior changes that conflict with fork-specific design. Continue selective provenance-based ports rather than merging upstream `main` wholesale.

High-value selective results from this review:

1. strict radius semantics — ported and regression-tested in this branch;
2. issue #88 bundle-boundary transition handling — selectively ported and extended across PieCloak's direct/retry/block repair paths;
3. issue #94 `COLLECT_ITEM` hidden-reference handling — selectively implemented using PieCloak's logical and client visibility state;
4. allocation/performance improvements — evaluate against the exact traversal without weakening its correctness contract;
5. large upstream raycast/chunk-parser rewrites — do not import blindly because PieCloak's target filtering and reliability behavior diverge.

## Validation status

Focused validation has passed for the WorldGuard exemption hardening, the adapted #88 bundle-boundary behavior, the stale block callback world-epoch fence, and the #94 `COLLECT_ITEM` suppression. Each focused lane ran the relevant core, PacketEvents, and Paper tests plus Paper compilation before its production changes were promoted.

Temporary patch scripts and one-shot validation workflows used during review were removed after their product commits landed. The final merge decision must use the normal Build/Static/external checks from the cleaned exact PR head; older green checks are not treated as evidence for later heads. Runtime profiling/manual testing of exact ray traversal and WorldGuard behavior on a realistic server remains a pre-merge requirement.
