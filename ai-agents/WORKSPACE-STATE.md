---
repository: wsg138/PieCloak
default_branch: main
coordination_branch: main
active_pr: 11
active_branch: agent/sync-upstream-clean-history
state: READY_FOR_AGENT
current_package: 06-final-integration-review
recorded_pr_head: 1c3b8c572030cdafb96975f36d471142aa9399bc
target_minecraft: 1.21.11
future_platform_target: stable Paper 26.2-or-newer
current_handoff: ai-agents/reports/agent-handoffs/0005-20260806T012531Z-optional-integrations-hardening.md
---

# PieCloak workspace state

Last coordinated: 2026-08-05

This routing record lives on `main`. Every worker must reconcile it with live GitHub before acting.

## Branch responsibilities

| Branch | Purpose |
| --- | --- |
| `main` | Agent rules, package definitions, routing state, and handoff reports only |
| `agent/sync-upstream-clean-history` | PR #11 implementation, tests, builds, workflows, metadata, and product documentation |

Workers implement on the PR branch first, then make one coordination-only commit to `main` containing the handoff and next routing state.

## Active work

| Field | Recorded value |
| --- | --- |
| State | `READY_FOR_AGENT` |
| Pull request | `#11 — Sync latest RaycastedAntiESP upstream and preserve PieCloak filtering` |
| Implementation branch | `agent/sync-upstream-clean-history` |
| Recorded implementation head | `1c3b8c572030cdafb96975f36d471142aa9399bc` |
| Current package | `06-final-integration-review` |
| Current target | Minecraft `1.21.11`, Leaf/Paper-compatible, Geyser/Floodgate-compatible |
| Future target | Stable Paper `26.2` or newer after a separate verified upgrade |
| Merge authority | None for workers; owner instruction required |

## Package routing

| Order | Package | Status | Dependency |
| --- | --- | --- | --- |
| 01 | Current 1.21.11 platform and CI baseline | `COMPLETE` | none |
| 02 | Block-transition reliability and block-data hardening | `COMPLETE` | 01 |
| 03 | Idempotent entity-transition reconciliation | `COMPLETE` | 02 |
| 04 | Async-engine scheduling and complete lifecycle cleanup | `COMPLETE` | 03 |
| 05 | Optional integrations and remaining hardening | `COMPLETE` | 04 |
| 06 | Final coordinator integration and brutal review | `READY` | 01–05 |

Packages are sequential to avoid overlapping controller, lifecycle, and build edits.

## Completed package 01 baseline

- Minecraft `1.21.11`, Paper development bundle `1.21.11-R0.1-SNAPSHOT`, and Java `21` are centralized in `gradle.properties`.
- Build, Paper test-server tasks, generated plugin metadata, and CI use the same values.
- Pull-request workflows validate the exact implementation head rather than GitHub's synthetic merge ref.
- Exact head `d23c6a577ead79fb4d70b230d1344a91095fb97b` passed Build run `31043407191` and Static analysis run `31043407187`.
- The shaded artifact records the exact Git SHA and the selected Minecraft, Paper bundle, and Java baseline.
- Stable Paper `26.2` or newer remains a separate future migration with no current support claim.

## Completed package 02 block-transition reliability

- Packed block-transition callbacks are failure-isolated; one callback cannot discard later packed or queued transitions.
- HIDE, SHOW, and mode-disable repairs use explicit packet stages and resume at the first incomplete write.
- Block repairs are deduplicated, backoff-bounded, capped at 256 entries per viewer, and invalidated by world, mode, disconnect, removed-tile, and expected-block changes.
- Desired tile visibility is committed independently from client repair so one failed mode-disable write does not block later repairs.
- Authoritative managed/non-managed/unknown block-entity classification works with both full-block tracking and the default occlusion-only mode.
- Unknown standalone managed block-entity data fails closed without logging NBT; known virtual/non-managed data remains pass-through.
- Unexpected `MAP_CHUNK_BULK` packets pass through with bounded diagnostics rather than throwing.
- Exact head `dd1cf6c2784a721cff6c1d5fd437cb5b65f5616b` passed Build run `31052209121`, Static analysis run `31052209222`, and focused validation run `31052271820` with seven package test groups repeated 25 times.
- The exact shaded JAR declares PieCloak API `1.21.11` and records the exact implementation SHA, Minecraft `1.21.11`, Paper bundle `1.21.11-R0.1-SNAPSHOT`, and Java `21`.

## Completed package 03 entity-transition reconciliation

- SHOW and HIDE use immutable packet plans with independent checkpoints for spawn, corrections, replay, relationships, and destroy.
- A successful packet write advances state immediately, so a later failure resumes at the first incomplete packet rather than duplicating spawn or destroy.
- Packet-confirmed visibility and local `clientVisible` bookkeeping are repaired separately; superseding opposite transitions inherit confirmed external state.
- Retry work is keyed, exponential-backoff bounded, limited to eight failures, capped at 256 entries per viewer and 4096 globally, and rejects new work at capacity instead of evicting queued partial repairs.
- Retry validation and cleanup cover viewer disconnect, join/respawn, world epoch, entity UUID/ID/object identity, despawn, destroyed IDs, desired visibility changes, and entity-ID reuse.
- Direct forced-show/bypass paths use the same staged reconciliation.
- Focused transition tests passed once plus 20 forced reruns in run `31056041879`.
- Exact head `3e18acde563809f68f003229266258687ce8ce10` passed Build run `31056637735`, Static analysis run `31056637857`, and CodeRabbit.
- The exact shaded JAR declares PieCloak API `1.21.11`, records the exact Git SHA and current platform baseline, and has SHA-256 `1e776b5b352026058e064e6200c6eed9feea6466ed41edd7d5d1d0d1db4a8a0d`.
- Live Leaf, Geyser/Floodgate, and real injected packet send-then-throw behavior remain unverified and are recorded in the package handoff.

## Completed package 04 engine scheduling and lifecycle

- Async worker submission uses a submission gate and idempotent worker permits, so fast completion, partial rejection, run-then-throw schedulers, worker failure, and shutdown cannot bypass exact-once finalization.
- Engine shutdown rejects new ticks, cancels pending reservations, drains accepted work with a bounded wait, and does not reset shared state while workers may still use it.
- Transactional startup owns ticker, PacketEvents controllers, Bukkit listeners, compatibility listeners, metrics, registries, and singleton references in reverse-cleanup order.
- PacketEvents teardown unregisters exact returned registration handles; Bukkit and Folia listeners/tasks have explicit idempotent close paths.
- Successful disable/re-enable creates fresh effective components. Failed drain or critical unregistering fences old state and explicitly blocks same-JVM re-enable.
- Deterministic local scheduling stress passed 500/500 iterations.
- Exact head `f203d7d1fe4781936fa69d4f7cf96083bbf73ab7` passed Build run `31059767805` twice (jobs `92484943305` and `92485323007`), Static analysis run `31059767818`, and CodeRabbit.
- The exact shaded JAR declares PieCloak API `1.21.11`, records the exact implementation SHA, and has SHA-256 `91a178f90b558bef0901d688e49d49e776d7fac5f248216a5b558c9d73981854`.
- Live Leaf, Geyser/Floodgate, Folia, same-JVM re-enable, forced drain-timeout, and real PacketEvents failure scenarios remain unverified and are recorded in the package handoff.

## Completed package 05 optional integrations and remaining hardening

- FancyNpcs and FancyHolograms APIs are isolated in separate classes and loaded by name only when the exact optional Bukkit plugin is enabled.
- One absent or incompatible optional integration cannot resolve or initialize the other and cannot disable PieCloak.
- Successful optional listeners transfer into the existing reverse-order lifecycle owner and close on disable or failed startup.
- NPC removal and hologram deletion clear both bypass classifications only after non-cancelled deletion; ordinary entity removal and shutdown reset remain idempotent cleanup backstops.
- The updater uses one configured connection, rejects declared or streamed responses above 50 KiB, avoids unbounded body accumulation, and delivers messages without a blocking scheduler hop.
- The join notification window uses elapsed monotonic ticks and is correct across boundaries and integer counter wrap.
- The inspected block-entity diagnostic path does not log raw NBT.
- Exact head `1c3b8c572030cdafb96975f36d471142aa9399bc` passed Build run `31062577947`, Static analysis run `31062577977`, and CodeRabbit.
- The exact shaded JAR declares PieCloak API `1.21.11`, records the exact implementation SHA, and has SHA-256 `b8c1323e9e33993d1b23f5691cc1c3ef7b912d5173ba2ce3ea43be6e51fd801f`.
- Live Leaf, Geyser/Floodgate, optional-plugin lifecycle, real network failure, and in-game join-notification scenarios remain unverified and are recorded in the package handoff.

## Final review focus

Package 06 must review the entire exact PR head rather than only package 05. It must reconcile every package handoff with live code, inspect cross-package interactions and exact-head checks, assess all recorded live/manual gaps, review the remaining non-fatal PMD findings including the optional-resource ownership warning, and issue the final `READY_FOR_OWNER` or `NOT_READY` verdict. It must not merge or deploy.

## Current boundaries

- No production deployment or server modification.
- No merge, close, or replacement of PR #11 without current owner instruction.
- No target change away from Minecraft 1.21.11.
- No Paper 26.2 support claim until a future stable build is selected and directly tested.
- No parallel workers.
- No product code, tests, workflows, or plugin metadata directly on `main`.
- No agent-routing files on the implementation branch.
- `agent/package03-worker-20260805` is a non-authoritative temporary validation branch with no PR; it must not be used for routing or implementation.

## Next route

The next ChatGPT worker must complete `ai-agents/work-packages/06-final-integration-review.md` against exact PR #11 head `1c3b8c572030cdafb96975f36d471142aa9399bc`, perform the final cross-package brutal review and exact-head validation, write the final timestamped coordinator handoff to `main`, issue the required READY/NOT READY verdict, and stop without merging or deploying.
