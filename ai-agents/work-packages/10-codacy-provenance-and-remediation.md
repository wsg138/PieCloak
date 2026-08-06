# Package 10 — Codacy provenance and remediation

## Goal

Produce an exact, reproducible classification of every Codacy issue reported on PR #11 as either inherited from the synchronized `Cubicake/RaycastedAntiESP` upstream baseline or introduced by PieCloak-specific code/remediation. Fix every finding attributable to PieCloak without mass-refactoring upstream-only findings.

## Starting evidence to verify live

- Repository: `wsg138/PieCloak`
- Pull request: `#11`
- Recorded starting head: `c650595a9a2d010ac5adef6725f1a63abaf294a7`
- Recorded Codacy check run: `92522261869`
- Recorded result: `action_required`
- Recorded added issues: `573`
- Recorded solved issues: `68`

Live GitHub and Codacy evidence override these recorded values.

## Required work

1. Export every check-run annotation through the GitHub Checks API, following pagination until complete. Do not classify from the shortened check summary.
2. Record, at minimum, path, line/column, message, analyzer, rule/pattern, symbol, severity, exact PR SHA, provenance, evidence, and fix commit.
3. Confirm the exported annotation count against Codacy's reported count and explain any mismatch.
4. Establish the exact synchronized `Cubicake/RaycastedAntiESP` upstream commit from PR history, sync/merge commits, upstream references, the upstream repository, and the final tree. Do not trust an old handoff without verification.
5. Compare the exact upstream baseline, the current PR head, and all PieCloak commits after import while preserving submodule/generated-source boundaries.
6. Assign every finding exactly one final category:
   - `UPSTREAM`: the same violation exists in the exact upstream baseline under the same effective rule.
   - `PIECLOAK`: PieCloak-created or modified code caused the violation or crossed the configured threshold.
7. Do not use git blame alone. For method/file-level or threshold findings, compare the same analyzer/rule/symbol on both versions. Use Codacy data or the underlying analyzer where exact local execution is unavailable, and document tool-version differences rather than guessing.
8. Fix every `PIECLOAK` finding. Preserve behavior and Minecraft `1.21.11`, Java `21`, Leaf/Paper, PacketEvents, and Geyser/Floodgate compatibility. Add focused tests for behavior-sensitive refactors.
9. Do not fix, suppress, exclude, or broadly refactor upstream-only findings. Do not weaken the quality gate, disable rules, change generated code/submodules to hide findings, or add broad suppressions. A narrow documented suppression is allowed only for a proven PieCloak false positive when no clearer safe expression exists.
10. Create on `main`:
    - `ai-agents/reports/codacy/package-10-findings.csv`, one row per exported finding;
    - `ai-agents/reports/codacy/package-10-summary.md`, with exact totals, counts by rule/module/production-vs-tests, methodology, authoritative upstream SHA, starting/ending PR SHAs, fixes, severe upstream findings, and remaining Codacy status.
11. The final totals must satisfy `UPSTREAM + PIECLOAK = TOTAL EXPORTED FINDINGS`, with no `UNKNOWN` or `MIXED` rows.
12. After final implementation push, export annotations again and prove `remaining PieCloak-attributed findings: 0`; do not rely only on the total decreasing.

## Hostile review

Review the complete package diff for metric-driven behavior changes, obscured ordering/state transitions, hot-path allocations, weakened concurrency, packet-write/visibility mutation reordering, cleanup/ownership reorder, tests bypassing production paths, diagnostic changes, reflection/serialization/API breakage, and accidental upstream-only edits. Fix every confirmed regression.

## Validation

Validate the exact final PR head with generated LeafPile sources, clean platform-baseline verification, full compile/tests, repeated concurrency-sensitive tests, shaded JAR and snapshot, metadata inspection, PMD, Semgrep, Trivy, exact-head Actions, exact-head Codacy, and unresolved review threads.

A remaining Codacy `action_required` result is acceptable only when the report proves every remaining annotation is `UPSTREAM` and all `PIECLOAK` findings were fixed.

## PR and handoff

Update PR #11 with exact upstream/PieCloak counts, fixed count, remaining count, report paths, remaining-upstream explanation when applicable, and exact validation head. Keep the PR `NOT READY` until package 11 independently verifies the result.

On `main`, add the package-10 handoff with starting/ending heads, upstream SHA, exported/upstream/PieCloak counts, fixed count, commits, validation evidence, remaining Codacy status, report paths, hostile-review findings, and next route. Advance only to `11-final-codacy-verification`, then stop.
