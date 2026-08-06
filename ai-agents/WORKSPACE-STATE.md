---
repository: wsg138/PieCloak
default_branch: main
coordination_branch: main
active_pr: 11
active_branch: agent/sync-upstream-clean-history
state: READY_FOR_AGENT
current_package: 07-controller-ownership-reenable
recorded_pr_head: 72489966f5c45261e61538c8725c955750fd188b
target_minecraft: 1.21.11
future_platform_target: stable Paper 26.2-or-newer
current_handoff: ai-agents/reports/agent-handoffs/0007-20260806T024300Z-owner-review-remediation-reopen.md
---

# PieCloak workspace state

Last coordinated: 2026-08-06

Owner review superseded package 06's `READY_FOR_OWNER` verdict with two confirmed release blockers. PR #11 remains open and unmerged. Sequential remediation is reopened at package 07.

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
| Recorded implementation head | `72489966f5c45261e61538c8725c955750fd188b` |
| Current package | `07-controller-ownership-reenable` |
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
| 07 | Controller ownership and same-JVM re-enable | `SELECTED` | 04, owner review |
| 08 | Respawn visibility-state invalidation | `PENDING` | 07 |
| 09 | Superseding final integration, PR cleanup, and release review | `PENDING` | 07–08 |

## Confirmed blockers

### Controller ownership/re-enable

`PacketEntityViewController.SELF` and `PacketEventsEntityViewController.SELF` survive the first controller's close. A second enable in the same classloader can fail as a duplicate controller. Failed construction can also leave partial singleton ownership. Package 07 must provide ownership-aware release, rollback, idempotent close, and reconstruction tests.

### Same-world respawn stale work

A same-world `RESPAWN` currently preserves the visibility generation and pre-respawn work. A queued SHOW can run after respawn and create a client-side ghost for an entity that is authoritatively hidden. Package 08 must make every respawn invalidate pre-respawn transitions, retries, reconciliation, tracked views, and client assumptions.

## Evidence boundary

Package-06 exact-head Build run `31063979744` and Static analysis run `31063979737` passed on `72489966f5c45261e61538c8725c955750fd188b`, but those workflows did not exercise either confirmed blocker. They remain historical build evidence, not a current readiness verdict.

## Current boundaries

- PR #11 is `NOT READY` until packages 07 and 08 complete and package 09 issues a new verdict.
- Do not merge or close PR #11 without a new explicit owner instruction.
- Do not deploy a JAR or modify production from this workflow.
- Do not begin package 08 while package 07 is selected.
- Do not switch the active target away from Minecraft `1.21.11`.
- Do not claim stable Paper `26.2` support.

## Next route

Complete exactly `07-controller-ownership-reenable`, validate its exact ending PR head, leave its handoff on `main`, select package 08, and stop.
