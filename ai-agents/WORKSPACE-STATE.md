---
repository: wsg138/PieCloak
default_branch: main
coordination_branch: main
active_pr: 11
active_branch: agent/sync-upstream-clean-history
state: READY_FOR_AGENT
current_package: 09-final-integration-pr-cleanup-release-review
recorded_pr_head: 35127dd6bff64e9f2d6dd4a1fe5e4ea48995aeb3
target_minecraft: 1.21.11
future_platform_target: stable Paper 26.2-or-newer
current_handoff: ai-agents/reports/agent-handoffs/0009-20260806T041555Z-respawn-visibility-state-invalidation.md
---

# PieCloak workspace state

Last coordinated: 2026-08-06

Owner review superseded package 06's former `READY_FOR_OWNER` verdict with two confirmed release blockers. Packages 07 and 08 have now completed the controller-ownership/re-enable and respawn visibility-state remediation. PR #11 remains open, unmerged, and `NOT READY` until package 09 performs a fresh complete review and issues a superseding final verdict.

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
| Recorded implementation head | `35127dd6bff64e9f2d6dd4a1fe5e4ea48995aeb3` |
| Current package | `09-final-integration-pr-cleanup-release-review` |
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
| 08 | Respawn visibility-state invalidation | `COMPLETE` | 07 |
| 09 | Superseding final integration, PR cleanup, and release review | `SELECTED` | 07–08 |

## Package 08 completion

Exact implementation head `35127dd6bff64e9f2d6dd4a1fe5e4ea48995aeb3` now treats every outbound `RESPAWN` as a new client-visible generation, including same-world and bypass-viewer respawns. It advances the epoch, clears tracked block/entity/player views, reconciliation and relationship state, drops block repair work, fences deferred after-send work, protects against entity-ID reuse, and conditionally unregisters only the exact affected player generation if reset cleanup fails.

Exact-head Build run `31070558208` and Static analysis run `31070558237` passed. The shaded JAR metadata records Minecraft `1.21.11`, Java `21`, and exact implementation head `35127dd6bff64e9f2d6dd4a1fe5e4ea48995aeb3`.

Live Leaf respawn scenarios, Geyser/Floodgate behavior, and injected packet/reset failures remain manual and unverified; see the current handoff.

## Remaining final work

Package 09 must independently review the complete PR after the owner-review remediation, reconcile the open Dependabot PRs and PR description, inspect exact-head checks and review state, validate the final artifact and platform metadata, and issue a new `READY_FOR_OWNER` or `NOT_READY` verdict. Package 09 must not rely on the superseded package-06 verdict.

## Open-PR boundary

Open Dependabot PRs #4, #5, #6, #7, #8, and #9 remain intentionally untouched. Their compatibility, overlap, and merge/close decisions are package 09 scope.

## Current boundaries

- PR #11 remains `NOT READY` pending package 09.
- Do not merge or close PR #11 without a new explicit owner instruction.
- Do not deploy a JAR or modify production from this workflow.
- Complete exactly package 09 in the next worker channel.
- Do not switch the active target away from Minecraft `1.21.11`.
- Do not claim stable Paper `26.2` support.
- The bypass-permission refresh limitation remains a package-09 review item.

## Next route

Complete exactly `09-final-integration-pr-cleanup-release-review`, validate its exact ending PR head, issue the superseding release verdict, record the handoff on `main`, and stop.
