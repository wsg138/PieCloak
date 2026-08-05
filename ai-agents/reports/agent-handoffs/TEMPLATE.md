# Package <ID> handoff — <title>

- Date/time:
- Agent role:
- Repository: `wsg138/PieCloak`
- Coordination branch: `main`
- Coordination starting SHA:
- Pull request: `#11`
- Implementation branch:
- Starting implementation head:
- Ending implementation head:
- Package:
- State: `READY_FOR_AGENT`, `BLOCKED`, `FINAL_REVIEW`, `READY_FOR_OWNER`, or `NOT_READY`

## Live reconciliation

Record the live main SHA, PR state/head/base, overlapping PRs or branches, review threads, and exact-head checks inspected before work began.

## Scope completed

State exactly what the package required and what was completed. Separate unrelated findings.

## Implementation commits

List each commit SHA and purpose.

## Design and behavior

Explain the failure mechanism, the implemented behavior, ordering/idempotency/lifecycle decisions, and upgrade-boundary implications.

## Files and architecture changed

List the important source, test, build, workflow, metadata, configuration, and product-documentation changes on the PR branch.

## Tests and validation

Record exact commands, repeated test counts, GitHub Actions run/job identifiers, JDK and platform target, artifact inspection, and results. Label unrun live scenarios honestly.

## Hostile review

Record the complete package-diff review, defects found, fixes made, and any coordinator follow-ups.

## Remaining risks and manual tests

List concrete remaining uncertainty, not generic cautions.

## Main coordination update

List the routing files being updated on `main`. Do not include product code in this commit.

## Next route

Name the next package and why the prerequisites are satisfied. If blocked, keep the current package selected and state the exact evidence or input required.