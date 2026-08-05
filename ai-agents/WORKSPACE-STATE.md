---
repository: wsg138/PieCloak
default_branch: main
coordination_branch: main
active_pr: 11
active_branch: agent/sync-upstream-clean-history
state: READY_FOR_AGENT
current_package: 02-block-transition-reliability
recorded_pr_head: d23c6a577ead79fb4d70b230d1344a91095fb97b
target_minecraft: 1.21.11
future_platform_target: stable Paper 26.2-or-newer
current_handoff: ai-agents/reports/agent-handoffs/0001-20260805T201940Z-current-platform-baseline.md
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
| Recorded implementation head | `d23c6a577ead79fb4d70b230d1344a91095fb97b` |
| Current package | `02-block-transition-reliability` |
| Current target | Minecraft `1.21.11`, Leaf/Paper-compatible, Geyser/Floodgate-compatible |
| Future target | Stable Paper `26.2` or newer after a separate verified upgrade |
| Merge authority | None for workers; owner instruction required |

## Package routing

| Order | Package | Status | Dependency |
| --- | --- | --- | --- |
| 01 | Current 1.21.11 platform and CI baseline | `COMPLETE` | none |
| 02 | Block-transition reliability and block-data hardening | `READY` | 01 |
| 03 | Idempotent entity-transition reconciliation | `WAITING` | 02 |
| 04 | Async-engine scheduling and complete lifecycle cleanup | `WAITING` | 03 |
| 05 | Optional integrations and remaining hardening | `WAITING` | 04 |
| 06 | Final coordinator integration and brutal review | `WAITING` | 01–05 |

Packages are sequential to avoid overlapping controller, lifecycle, and build edits.

## Completed package 01 baseline

- Minecraft `1.21.11`, Paper development bundle `1.21.11-R0.1-SNAPSHOT`, and Java `21` are centralized in `gradle.properties`.
- Build, Paper test-server tasks, generated plugin metadata, and CI use the same values.
- Pull-request workflows validate the exact implementation head rather than GitHub's synthetic merge ref.
- Exact head `d23c6a577ead79fb4d70b230d1344a91095fb97b` passed Build run `31043407191` and Static analysis run `31043407187`.
- The shaded artifact records the exact Git SHA and the selected Minecraft, Paper bundle, and Java baseline.
- Stable Paper `26.2` or newer remains a separate future migration with no current support claim.

## Confirmed review findings routed into packages

### Package 02

- A block transition callback failure consumes the queue entry and can discard remaining packed transitions.
- HIDE/SHOW writes and mode-disable visibility repair are not transactional or retry-safe.
- Unknown block-entity data currently fails open and logs full NBT.
- `MAP_CHUNK_BULK` intentionally throws from packet handling.

### Package 03

- Current entity retry wraps a multi-packet SHOW as one operation and can resend a spawn after a later stage fails.
- Retries are unbounded and may continue indefinitely.
- Direct forced-show paths can commit visibility before packet reconciliation succeeds.
- Relationship, replay, and correction stages need explicit idempotent semantics.

### Package 04

- Partial async worker submission can reach zero without any worker observing zero, leaving the engine wedged.
- Static listener/controller state is not reinitializable in the same JVM.
- PacketEvents listeners and registries lack a complete shutdown/reset path.
- Partial startup can cause shutdown null failures and stale async work.

### Package 05

- FancyNPCs/FancyHolograms compatibility is constructed unconditionally despite soft dependencies.
- Bypass IDs can remain stale after optional entities are removed or IDs are reused.
- The update checker configures one connection but reads from another unbounded connection.
- The join-time update window arithmetic is reversed.
- Sensitive block NBT can enter logs.

## Current boundaries

- No production deployment or server modification.
- No merge, close, or replacement of PR #11 without current owner instruction.
- No target change away from Minecraft 1.21.11.
- No Paper 26.2 support claim until a future stable build is selected and directly tested.
- No parallel workers.
- No product code, tests, workflows, or plugin metadata directly on `main`.
- No agent-routing files on the implementation branch.

## Next route

The next ChatGPT worker must complete `ai-agents/work-packages/02-block-transition-reliability.md` on the PR branch, validate the exact implementation head, write a timestamped package handoff to `main`, advance `current_package` to `03-entity-transition-reconciliation` if complete, and stop.
