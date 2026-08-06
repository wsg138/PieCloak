---
id: 09-final-integration-pr-cleanup-release-review
title: Superseding final integration, PR cleanup, and release review
role: coordinator
expected_commits: 0-or-focused-fixes
---

# Package 09 — Superseding final integration and release review

## Role

This is the new final coordinator package. It supersedes package 06's readiness verdict after packages 07 and 08 address the owner-review blockers. Do not assume either remediation is correct merely because its tests pass.

## Goal

Review the complete PR #11 at the exact post-package-08 head, verify both owner-review blockers are actually closed, clean up the PR description and coordination state, and return a new evidence-based `READY_FOR_OWNER` or `NOT_READY` verdict.

Do not merge or deploy without a separate explicit owner instruction.

## Required independent review

Repeat the complete package-06 review across platform/build, block protocol, entity protocol, concurrency, lifecycle, security/operability, test integrity, and documentation, with extra emphasis on:

- same-classloader disable/re-enable and partial controller construction;
- ownership release when unregister/cleanup throws;
- stale owners attempting cleanup after replacement;
- same-world respawn with queued SHOW/HIDE/block/relationship work;
- entity-ID reuse and replay state after respawn;
- Java and Geyser/Floodgate behavior assumptions;
- interaction between package 07 lifecycle fencing and package 08 generation invalidation.

## Required validation

Validate the exact final head using the current clean Gradle command, all focused remediation tests, repeated race-sensitive tests, full tests, shaded JAR and metadata inspection, configured static analysis, exact-head GitHub Actions, artifacts, open PRs, and unresolved review threads.

The prior package-06 Build and Static analysis runs are historical evidence only. They do not validate packages 07 or 08.

## PR cleanup

Update PR #11 so its body accurately records:

- the superseded package-06 verdict;
- both owner-review blockers;
- package 07 and 08 implementation commits and exact behavior;
- final exact-head CI and artifact identity;
- remaining live/manual validation;
- the new final verdict and explicit no-merge boundary.

Remove stale claims that the package-06 head is owner-ready.

## Final output

If no known source or exact-head CI blocker remains, set `READY_FOR_OWNER`, `current_package: none`, and record the remaining manual/live gaps. If a blocker remains, set `NOT_READY` or route a new explicit package with evidence. Do not create speculative cleanup packages merely to continue the sequence.
