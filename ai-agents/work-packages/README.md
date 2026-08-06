# PieCloak work packages

These packages convert verified review findings into sequential, non-overlapping ChatGPT worker assignments.

## Selection

The only active package is the `current_package` named in `../WORKSPACE-STATE.md` on `main`. Do not choose another package manually.

## Package completion

A package is complete only when its acceptance criteria are implemented on PR #11, focused tests prove important failure paths, a hostile review finds no package blocker, the exact final head is validated, intentional commits are pushed, and one coordination-only handoff commit updates `WORKSPACE-STATE.md`, `CURRENT.md`, and `INDEX.md` on `main`.

## Sequential order

1. `01-current-platform-baseline.md`
2. `02-block-transition-reliability.md`
3. `03-entity-transition-reconciliation.md`
4. `04-engine-scheduling-lifecycle.md`
5. `05-optional-integrations-hardening.md`
6. `06-final-integration-review.md` — superseded by later owner review
7. `07-controller-ownership-reenable.md`
8. `08-respawn-visibility-state-invalidation.md`
9. `09-final-integration-pr-cleanup-release-review.md`
10. `10-codacy-provenance-and-remediation.md`
11. `11-final-codacy-verification.md`

The order is intentional. Package 10 performs the complete annotation export, upstream provenance accounting, and PieCloak-only remediation. Package 11 independently verifies those results and issues the next owner verdict. They must not run concurrently or in one channel.

## Scope changes

When a package discovers a real prerequisite defect, fix it inside the package only when small and directly necessary; otherwise mark the package blocked, document exact evidence on `main`, and stop. Do not silently invent or reorder packages.
