---
repository: wsg138/PieCloak
default_branch: main
coordination_branch: main
active_pr: 11
active_branch: agent/sync-upstream-clean-history
state: READY_FOR_AGENT
current_package: 01-current-platform-baseline
recorded_pr_head: 33b615d5ef3a937dafdf461c48e9626ee7d342fc
target_minecraft: 1.21.11
future_platform_target: stable Paper 26.2-or-newer
current_handoff: ai-agents/reports/agent-handoffs/0000-coordination-bootstrap.md
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
| Recorded implementation head | `33b615d5ef3a937dafdf461c48e9626ee7d342fc` |
| Current package | `01-current-platform-baseline` |
| Current target | Minecraft `1.21.11`, Leaf/Paper-compatible, Geyser/Floodgate-compatible |
| Future target | Stable Paper `26.2` or newer after a separate verified upgrade |
| Merge authority | None for workers; owner instruction required |

## Package routing

| Order | Package | Status | Dependency |
| --- | --- | --- | --- |
| 01 | Current 1.21.11 platform and CI baseline | `READY` | none |
| 02 | Block-transition reliability and block-data hardening | `WAITING` | 01 |
| 03 | Idempotent entity-transition reconciliation | `WAITING` | 02 |
| 04 | Async-engine scheduling and complete lifecycle cleanup | `WAITING` | 03 |
| 05 | Optional integrations and remaining hardening | `WAITING` | 04 |
| 06 | Final coordinator integration and brutal review | `WAITING` | 01–05 |

Packages are sequential to avoid overlapping controller, lifecycle, and build edits.

## Confirmed review findings routed into packages

### Package 01

- PR #11 currently validates an older Paper/API and Java baseline rather than the owner's Minecraft 1.21.11 target.
- Build, run-server, plugin metadata, and CI versions need one coherent source of truth.
- The future Paper 26.2 move needs a narrow upgrade seam rather than current-target drift.

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

The next ChatGPT worker must complete `ai-agents/work-packages/01-current-platform-baseline.md` on the PR branch, validate the exact implementation head, write a timestamped package handoff to `main`, advance `current_package` to `02-block-transition-reliability`, and stop.