# PieCloak work packages

These packages convert the deep-review findings into sequential, non-overlapping ChatGPT worker assignments.

## Selection

The only active package is the `current_package` named in `../WORKSPACE-STATE.md` on `main`. Do not choose another package manually.

## Package completion

A package is complete only when:

- its acceptance criteria are implemented on the PR #11 branch;
- focused regression tests prove the important failure paths;
- a separate hostile review finds no package blocker;
- the exact final implementation head is built and tested;
- intentional implementation commits are pushed;
- one timestamped handoff is added on `main`;
- `WORKSPACE-STATE.md`, `CURRENT.md`, and `INDEX.md` on `main` route to the next package.

The handoff/routing update must be one coordination-only main commit and must not contain product code.

## Sequential order

1. `01-current-platform-baseline.md`
2. `02-block-transition-reliability.md`
3. `03-entity-transition-reconciliation.md`
4. `04-engine-scheduling-lifecycle.md`
5. `05-optional-integrations-hardening.md`
6. `06-final-integration-review.md`

The order is intentional. Package 01 establishes the build target used by every later package. Packages 02 and 03 both touch packet reconciliation and must not run concurrently. Package 04 fixes lifecycle around the stabilized controllers. Package 05 uses those lifecycle boundaries for optional integrations. Package 06 is the final coordinator pass.

## Scope changes

When a package discovers a real prerequisite defect:

- fix it inside the package only if it is small, directly necessary, and reviewed;
- otherwise mark the package `BLOCKED`, document the prerequisite in the main handoff, and stop;
- do not silently invent package 07 or reorder the queue without updating the routing documents and explaining why.