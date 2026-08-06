# Superseding handoff — Codacy provenance review reopened

- Date/time: 2026-08-06T05:18:06Z
- Agent role: owner-directed coordination reopen
- Repository: `wsg138/PieCloak`
- Coordination branch: `main`
- Coordination starting SHA: `9b5ebf302a2659a2b9a2c3e87dec0687029df405`
- Pull request: `#11`
- Implementation branch: `agent/sync-upstream-clean-history`
- Recorded implementation head: `c650595a9a2d010ac5adef6725f1a63abaf294a7`
- Selected package: `10-codacy-provenance-and-remediation`
- State: `READY_FOR_AGENT`

## Why the previous verdict is superseded

Owner review found an unclassified Codacy `action_required` result after package 09 issued `READY_FOR_OWNER`. The live check run `92522261869` is attached to exact PR head `c650595a9a2d010ac5adef6725f1a63abaf294a7` and reports 573 added issues and 68 solved issues.

PR #11 includes a major synchronization from `Cubicake/RaycastedAntiESP`. Codacy's phrase “new relative to PieCloak main” does not prove that all 573 findings were introduced by PieCloak. The previous verdict did not export all annotations or establish per-finding upstream provenance, so it is superseded for this review boundary.

## Coordination changes

This coordination update preserves packages 01–09 and all prior handoffs, adds:

- package 10: exact Codacy provenance classification and remediation;
- package 11: independent final Codacy verification.

Routing is reopened at package 10. PR #11 must remain `NOT READY — CODACY PROVENANCE REVIEW PENDING` until package 11 independently verifies package 10.

## Required package-10 outcome

Package 10 must export every annotation with pagination, establish the exact synchronized upstream SHA, assign every finding exactly `UPSTREAM` or `PIECLOAK`, fix every PieCloak-attributed finding, produce one-row-per-finding CSV and a reproducible Markdown report, validate the exact final head, re-export final annotations, and prove zero remaining PieCloak-attributed findings.

Remaining upstream-only annotations may keep Codacy `action_required`; they may not be mass-fixed or hidden merely to make the check green.

## Next route

`state: READY_FOR_AGENT`

`current_package: 10-codacy-provenance-and-remediation`

`recorded_pr_head: c650595a9a2d010ac5adef6725f1a63abaf294a7`
