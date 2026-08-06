# Package 11 — final Codacy verification

## Goal

Independently verify package 10's Codacy provenance classification, remediation completeness, exact-head validation, and PR reporting before issuing a new owner verdict.

## Dependency

Run only after package 10 is complete and routing selects this package. Do not reuse package 10's conclusions without checking the underlying exported annotations, reports, commits, live check run, and exact final head.

## Required work

1. Reconcile live `main`, PR #11, implementation head, open PRs, reviews, threads, and exact-head checks.
2. Read the package-10 CSV and Markdown report and independently sample and challenge classifications across every analyzer/rule/module category, all PieCloak-attributed rows, threshold findings, tests, and severe upstream rows.
3. Re-export all annotations from the final Codacy check using complete pagination and confirm:
   - report totals add exactly;
   - no row is unknown or mixed;
   - every remaining annotation is reproducibly upstream;
   - remaining PieCloak-attributed findings are zero.
4. Verify the authoritative upstream SHA and the import boundary from repository history and the upstream repository.
5. Review the complete package-10 implementation diff hostilely for behavior, ordering, concurrency, hot-path allocation, cleanup, packet-write/state mutation, compatibility, test-quality, and accidental upstream-only changes.
6. Validate the exact final head with the repository's full current build/test/static-analysis/artifact workflow, repeated concurrency-sensitive tests, Codacy, metadata inspection, and unresolved review-thread inspection.
7. Correct only confirmed package-10 defects. Keep any fixes focused, re-run exact-head validation, and update the classification reports when a fix changes evidence or totals.
8. Update PR #11 with the independently verified final status and exact head. Do not merge, close, or deploy.

## Verdict and handoff

Issue `READY_FOR_OWNER` only if the complete provenance accounting is reproducible, every PieCloak finding is fixed, remaining annotations are proven upstream, exact-head validation passes, and no package blocker remains. Otherwise issue `NOT_READY` or `BLOCKED` with exact evidence.

Record a new package-11 handoff and final routing on `main`, preserving all previous history. Then stop.
