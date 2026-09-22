# PieCloak entity visibility hardening audit — 2026-09-22

Base reviewed: `main` at `6b552bda324f5240967dab333e4a14d76bebe235`.
Upstream reference reviewed: `Cubicake/RaycastedAntiESP` through `1fcec23a57cad64886713e67dcc6b0e16bdbfa98`.

## Confirmed findings

### 1. Entity raycast radius was effectively one step too large

`RaycastUtil.raycast(...)` subtracted the one-block ray step from the real target distance before comparing the result with `always-show-radius` and `raycast-radius`.

With a one-block step this allowed a target slightly beyond a configured 48-block maximum (for example 48.5 blocks away) to pass the radius gate. It also enlarged the always-show radius by the same mechanism.

The hardening branch now performs both radius decisions against the real start-to-target distance and applies the step subtraction only to the occlusion walk. Regression tests cover both boundaries.

This is directly relevant to reports of entities being visible just beyond 48 blocks. It does not explain entities visible substantially farther than roughly one extra step.

### 2. Occlusion raycasting deliberately favors false-visible results

The current raycast is a one-block stepping sampler rather than exact voxel traversal. Its own contract explicitly accepts missing corner blocks and treats those misses as visible.

That is a performance tradeoff, not a state-machine bug, but it means the visibility system is not mathematically fail-closed for all diagonal/corner geometries. If the project requirement becomes strict anti-information-leak correctness, exact/supercover 3D voxel traversal should be evaluated and benchmarked before replacing the current sampler.

### 3. Glowing entities bypass normal radius/occlusion hiding

Managed entities with the Minecraft glowing metadata flag are force-shown before the normal raycast/radius decision. This is consistent with vanilla glowing semantics, but it is an explicit exception to a policy such as “anything beyond 48 blocks is always hidden.”

Diagnostics should report this state so a glow-related exception is immediately visible during testing.

### 4. PieCloak has not yet incorporated upstream bundle-boundary reliability fixes

Upstream issue #88 reported client disconnects when anti-ESP show packets were emitted inside an unrelated existing protocol bundle. Upstream fixed entity and block transition emission so pending transitions are deferred until the outer bundle ends.

The current PieCloak controllers do not contain upstream's bundle-state guard. This is a genuine reliability difference worth selectively porting. A wholesale upstream merge is not appropriate because PieCloak has substantial fork-specific target filtering, transition hardening, and chunk/block-entity behavior.

### 5. Packet coverage remains an anti-information-leak surface

Upstream issue #45 still tracks clientbound packets that are not fully visibility-aware. Upstream issue #94 is an accepted concrete example: `COLLECT_ITEM` can expose a visible effect when its collector is hidden from a Java client.

PieCloak currently ships with player checks disabled, so the exact #94 player scenario is not active under the bundled production configuration. Entity-side equivalents and other unhandled packets should nevertheless be reviewed if the goal is strict leak resistance.

## Item frame / armor stand conclusion

The configured entity names match PacketEvents' registry names, and the bundled exclusions do not exclude item frames, glow item frames, or armor stands. A normal non-glowing, non-attached, non-plugin-bypassed instance of one of these types should enter the managed entity view.

Once managed, the normal state machine starts distant spawns hidden and repeatedly rechecks hidden entities. After the radius-boundary fix, the configured maximum radius is strict. Therefore a report of ordinary frames/stands visible only slightly beyond 48 blocks is consistent with the confirmed radius bug; a report of them visible much farther away requires checking exceptional state (glowing, attachment, explicit plugin bypass) or reproducing the report.

FancyHolograms and FancyNPCs intentionally register their own entity IDs in the bypass registry. This can explain plugin-owned armor stands or hologram internals, but it does not explain ordinary vanilla item frames at a normal base.

## Hardening candidates requiring more proof or design

### Missing chunk section data is fail-open for occlusion

An untracked/missing occlusion section currently answers “not occluding.” Hidden-on-spawn behavior limits the obvious race window, so this audit does not classify that alone as a reproduced leak. A dedicated packet-order/concurrency test should establish whether a visible entity can be rechecked while required section data is absent before changing the policy.

### Exact raycasting

If strict structural privacy is prioritized over the current speed tradeoff, prototype a voxel-exact/supercover ray and benchmark it against realistic entity counts. Do not silently call the existing approximate sampler exact.

### Packet side channels

Audit packets that reference entity IDs or reveal entity-associated location/activity without going through the managed visibility gate, especially `COLLECT_ITEM`, entity sounds, damage/effect/particle events, and vehicle-specific synchronization.

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

1. strict radius semantics — ported in this branch;
2. issue #88 bundle-boundary transition handling — applicable, requires careful adaptation to PieCloak's hardened retry/transition paths;
3. allocation/performance improvements — evaluate after correctness ports;
4. large raycast/chunk-parser rewrites — do not import blindly because PieCloak's target filtering and reliability behavior diverge.
