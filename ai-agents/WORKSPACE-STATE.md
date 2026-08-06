---
repository: wsg138/PieCloak
default_branch: main
coordination_branch: main
active_pr: 11
active_branch: agent/sync-upstream-clean-history
state: READY_FOR_AGENT
current_package: 08-respawn-visibility-state-invalidation
recorded_pr_head: a6056313378f3fbdbbbb3698a67ca675533ad351
target_minecraft: 1.21.11
future_platform_target: stable Paper 26.2-or-newer
current_handoff: ai-agents/reports/agent-handoffs/0008-20260806T034000Z-controller-ownership-reenable.md
---

# PieCloak workspace state

Last coordinated: 2026-08-06

Owner review superseded package 06's `READY_FOR_OWNER` verdict with two confirmed release blockers. Package 07 has now completed the controller-ownership and same-JVM re-enable remediation. PR #11 remains open, unmerged, and `NOT READY` because package 08 and a fresh package-09 final review are still required.

## Branch responsibilities

| Branch | Purpose |
| --- | --- |
| `main` | Agent rules, package definitions, routing state, and handoff reports only |
| `agent/sync-upstream-clean-history` | PR #11 implementation, tests, builds, workflows, metadata, and product documentation |

## Active work

| Field | Recorded value |
| --- | --- |
| State | `READY_FOR_AGENT` |
| Pull request | `#11 — Sync latest RaycastedAntiESP upstream and preserve PieCloak filtering` |
| Implementation branch | `agent/sync-upstream-clean-history` |
| Recorded implementation head | `a6056313378f3fbdbbbb3698a67ca675533ad351` |
| Current package | `08-respawn-visibility-state-invalidation` |
| Current target | Minecraft `1.21.11`, Leaf/Paper-compatible, Geyser/Floodgate-compatible |
| Future target | Stable Paper `26.2` or newer after a separate verified upgrade |
| Merge authority | Owner only; workers have no merge or deployment authority |

## Package routing

| Order | Package | Status | Dependency |
| --- | --- | --- | --- |
| 01 | Current 1.21.11 platform and CI baseline | `COMPLETE` | none |
| 02 | Block-transition reliability and block-data hardening | `COMPLETE` | 01 |
| 03 | Idempotent entity-transition reconciliation | `COMPLETE` | 02 |
| 04 | Async-engine scheduling and complete lifecycle cleanup | `COMPLETE` | 03 |
| 05 | Optional integrations and remaining hardening | `COMPLETE` | 04 |
| 06 | Final coordinator integration and brutal review | `SUPERSEDED` | 01–05 |
| 07 | Controller ownership and same-JVM re-enable | `COMPLETE` | 04, owner review |
| 08 | Respawn visibility-state invalidation | `SELECTED` | 07 |
| 09 | Superseding final integration, PR cleanup, and release review | `PENDING` | 07–08 |

## Package 07 completion

Exact implementation head `a6056313378f3fbdbbbb3698a67ca675533ad351` now provides ownership-aware release and rollback for the core and PacketEvents entity-controller singleton slots, idempotent listener registration cleanup, stale-owner protection, and explicit same-JVM re-enable fencing when constructor or shutdown cleanup cannot be proven safe.

Exact-head Build run `31068970441` and Static analysis run `31068970437` passed. The shaded JAR metadata records Minecraft `1.21.11`, Java `21`, and the exact implementation head.

Live Leaf disable/re-enable and real PacketEvents injected-cleanup failures remain manual/unverified scenarios; see the current handoff.

## Remaining confirmed blocker

### Same-world respawn stale work

A same-world `RESPAWN` currently preserves the visibility generation and pre-respawn work. A queued SHOW can run after respawn and create a client-side ghost for an entity that is authoritatively hidden. Package 08 must make every respawn invalidate pre-respawn entity and block transitions, retries, reconciliation, replay and relationship state, tracked views, client-visible assumptions, and stale after-send callbacks.

The reset must apply to same-world, same-dimension, death, different-world, repeated, and bypass-viewer respawns without corrupting the retained player registration.

## Open-PR boundary

Open Dependabot PRs #4, #5, #6, #7, #8, and #9 remain intentionally untouched. Their compatibility, overlap, and merge/close decisions are package 09 scope.

## Current boundaries

- PR #11 remains `NOT READY`.
- Do not merge or close PR #11 without a new explicit owner instruction.
- Do not deploy a JAR or modify production from this workflow.
- Complete exactly package 08; do not begin package 09 in the same worker channel.
- Do not switch the active target away from Minecraft `1.21.11`.
- Do not claim stable Paper `26.2` support.
- The bypass permission refresh limitation remains a documented package-09 review item unless package-08 testing proves direct interference.

## Next route

Complete exactly `08-respawn-visibility-state-invalidation`, validate its exact ending PR head, leave its handoff on `main`, select package 09, and stop.
