# PieCloak entity visibility hardening audit — 2026-09-22

Base reviewed: `main` at `6b552bda324f5240967dab333e4a14d76bebe235`.
Upstream reference reviewed: `Cubicake/RaycastedAntiESP` through `1fcec23a57cad64886713e67dcc6b0e16bdbfa98`.

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

### 6. PieCloak has not yet incorporated upstream bundle-boundary reliability fixes

Upstream issue #88 reported client disconnects when anti-ESP show packets were emitted inside an unrelated existing protocol bundle. Upstream fixed entity and block transition emission so pending transitions are deferred until the outer bundle ends.

The current PieCloak controllers do not contain upstream's bundle-state guard. This is a genuine reliability difference worth selectively porting. A wholesale upstream merge is not appropriate because PieCloak has substantial fork-specific target filtering, transition hardening, and chunk/block-entity behavior.

### 7. Packet coverage remains an anti-information-leak surface

Upstream issue #45 still tracks clientbound packets that are not fully visibility-aware. Upstream issue #94 is an accepted concrete example: `COLLECT_ITEM` can expose a visible effect when its collector is hidden from a Java client.

PieCloak currently ships with player checks disabled, so the exact #94 hidden-player scenario is not active under the bundled production configuration. Entity-side equivalents and other unhandled packets should nevertheless be reviewed if the goal is strict leak resistance.

Relationship packets also deserve explicit packet-order testing: packets such as `SET_PASSENGERS` or leash state can reference an entity ID before every endpoint has spawned. PieCloak retains and replays unresolved relationships, so a strict anti-information-leak pass should verify whether any pre-spawn relationship packet can be safely suppressed rather than exposing an otherwise-hidden endpoint ID.

### 8. Bypass permission is cached for the session

The Paper join handler snapshots `raycastedantiesp.bypass` into `PlayerData`, and no corresponding permission-change refresh path was found in this review. If a player's bypass permission is granted or revoked while they remain connected, the anti-ESP decision can remain stale until reconnect.

This does not explain the reported player if they never had the permission, but it is an authorization-state hardening gap.

## Item frame / armor stand conclusion

The configured entity names match PacketEvents' registry names, and the bundled exclusions do not exclude item frames, glow item frames, or armor stands. The live server configuration was also confirmed by the operator to include them. A normal non-glowing, non-attached, non-plugin-bypassed instance of one of these types should enter the managed entity view.

Once managed, the normal state machine starts distant spawns hidden and repeatedly rechecks hidden entities. After the radius-boundary fix, the configured maximum radius is strict, and the new exact voxel traversal removes the old sampler's deliberate diagonal/corner misses. Therefore a remaining report of ordinary frames/stands visible much farther away requires checking exceptional state (glowing, attachment, explicit plugin bypass), packet-order behavior, or reproducing the report against the hardened build.

FancyHolograms and FancyNPCs intentionally register their own entity IDs in the bypass registry. This can explain plugin-owned armor stands or hologram internals, but it does not explain ordinary vanilla item frames at a normal base.

The bypassed-vehicle exploit is a genuine separate entity visibility flaw, but it does not explain ordinary wall-mounted item frames.

## Block-entity audit conclusion

The normal 1.21.11 block-entity paths are comparatively defensive:

- chunk data is parsed and rewritten before send;
- single block changes hide newly tracked managed tile entities before the real state reaches the client;
- multi-block changes replace hidden managed state IDs;
- standalone block-entity data without tracked tile state fails closed when either the cached block or packet type establishes that the target is managed;
- SHOW/HIDE repairs use bounded transition retries.

No normal-protocol player-controlled block-entity reveal equivalent to the bypassed-vehicle entity flaw was proven in this audit.

`MAP_CHUNK_BULK` is currently passed through unchanged with a warning, but that is not a normal 1.21.11 server packet path and is not classified here as a practical exploit without evidence that it can occur on the production baseline.

## Hardening candidates requiring more proof or design

### Missing chunk section data is fail-open for occlusion

An untracked/missing occlusion section currently answers “not occluding.” Hidden-on-spawn behavior limits the obvious race window, so this audit does not classify that alone as a reproduced leak. A dedicated packet-order/concurrency test should establish whether a visible entity can be rechecked while required section data is absent before changing the policy.

### Exact raycasting runtime validation

Exact voxel traversal is now implemented on this branch and covered by correctness tests. Before merge, benchmark and profile it under realistic production entity/player counts. Do not trade correctness back to approximate sampling merely to recover a synthetic microbenchmark result; if the exact implementation is too expensive, optimize its state representation and hot-path allocation profile while preserving the exact traversal contract.

### Packet side channels

Audit packets that reference entity IDs or reveal entity-associated location/activity without going through the managed visibility gate, especially `COLLECT_ITEM`, entity sounds, coordinate-based sounds, damage/effect/particle events, pre-spawn passenger/leash relationships, and vehicle-specific synchronization.

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
- vehicle/passenger/leash relationships;
- current radius result and raycast/occlusion result;
- pending visibility transition/retry state.

Recommended block-entity output:

- material and PacketEvents state ID;
- target-filter decision;
- block-entity classification/status;
- tracked tile state and last-checked tick;
- visibility state;
- whether the containing section is loaded in the occlusion view;
- current radius and raycast result;
- pending block transition/retry state.

For useful bypass diagnostics, evolve `EntityBypassRegistry` so diagnostic state records why an ID is bypassed (for example target-filter exclusion, entity-type exclusion, FancyHolograms, FancyNPCs, relationship support, or future WorldGuard skip) while preserving a cheap hot-path membership check.

The earlier diagnostic-command prototype was intentionally removed from this hardening PR after it expanded scope and static-analysis complexity. Diagnostics should be implemented as a separate focused package rather than coupled to the visibility fixes.

## WorldGuard exemption design

A future WorldGuard state flag such as `piecloak-skip` should be treated as a target-location policy: entities and block entities inside an effective flagged region are always sent normally and should avoid normal anti-ESP tracking/raycast work where safe.

Important structural requirements:

- register the custom flag during the WorldGuard flag-registration phase;
- keep the integration optional/soft-dependent;
- for static block entities, avoid inserting exempt targets into managed tile tracking where practical;
- for moving entities, handle both region-boundary directions: entering an exempt region must reveal and stop normal hiding work, while leaving it must re-enter managed tracking and immediately receive the correct hidden/visible decision;
- do not perform an expensive WorldGuard region query for every block in every chunk; cache region/section intersection or otherwise restrict detailed checks to affected areas;
- define exemption precedence explicitly over target allowlists and normal raycast policy.

## Upstream sync policy

The reviewed upstream baseline is 74 commits ahead of the historical PieCloak import point. The delta contains meaningful fixes, but also large refactors and behavior changes that conflict with fork-specific design. Continue selective provenance-based ports rather than merging upstream `main` wholesale.

High-value selective candidates found in this review:

1. strict radius semantics — ported and regression-tested in this branch;
2. issue #88 bundle-boundary transition handling — applicable, requires careful adaptation to PieCloak's hardened retry/transition paths;
3. allocation/performance improvements — evaluate against the new exact traversal without weakening its correctness contract;
4. large upstream raycast/chunk-parser rewrites — do not import blindly because PieCloak's target filtering and reliability behavior diverge.

## Validation status

At exact code head `fd3620c5396bf8a8896057519b699a50bb9ea72d`, Codacy completed successfully with zero annotations, and Semgrep/Trivy reported no new alerts. Static analysis completed successfully. The immediately preceding code-identical head completed the full Gradle compile/test/build successfully before its superseded workflow was cancelled after the successful build step. A fresh exact-head Build workflow is still required to complete before this report should be treated as final merge evidence.
