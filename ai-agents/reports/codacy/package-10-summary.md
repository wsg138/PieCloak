# Package 10 Codacy provenance and remediation summary

## Verdict

Package `10-codacy-provenance-and-remediation` is complete at PR head
`ee5c80f8c96cdd565eb95edcb1cf8128469741bb` (tree
`9b672eec8b2aef8a7af58073ca85677d0c35fb5c`).

```text
remaining PieCloak-attributed findings: 0
```

The final complete Codacy export contains **376** added findings. Every remaining
finding is reproduced by the authoritative Cubicake upstream baseline and is
classified `UPSTREAM`. No newly reported remediation finding remains.

## Authoritative source identities

- Cubicake upstream commit:
  `853fa1531acbbb1458f776bbe2dc637fd0d40b7c`
- PieCloak upstream-sync commit:
  `18f08ab7d70f3b5c4b7a0addc1e777e310381086`
- Tree shared by both commits:
  `c7f17bf195a1cc3d493eba4d35782387a16e6668`
- Initial package-10 PR head:
  `c650595a9a2d010ac5adef6725f1a63abaf294a7`
- Final package-10 PR head:
  `ee5c80f8c96cdd565eb95edcb1cf8128469741bb`

The identical upstream and sync trees establish a content-stable comparison
boundary. Commit attribution alone was not treated as sufficient evidence.

## Dataset completeness

Initial Codacy check run `92522261869` reported:

- 573 added findings
- 68 solved findings

All 573 added findings were exported from Codacy's cursor-paginated v3 issue API.
GitHub's check annotation endpoint returned only 50 annotations and then an empty
second page. The resulting **523-finding discrepancy** proves that GitHub check
annotations are not the complete dataset for this PR.

The final exact-head export used the same complete Codacy API and recorded:

- Codacy check run: `92561079845`
- Exact head: `ee5c80f8c96cdd565eb95edcb1cf8128469741bb`
- Check conclusion: `action_required`
- Complete added-finding count: **376**
- Codacy `fixed` endpoint count: **160**
- Codacy added pages: `100, 100, 100, 76`
- Codacy fixed pages: `100, 60`
- GitHub annotation pages: `50, 0`

The final `action_required` conclusion is entirely explained by the 376
upstream-attributed findings. It does not indicate a remaining PieCloak finding.

## Initial classification

The 573 initial added findings were classified by reproducing PMD 6.55 and
Lizard 1.23 results against both exact source trees, with direct source comparison
for the Opengrep rows.

| Provenance | Count |
|---|---:|
| `UPSTREAM` | 390 |
| `PIECLOAK` | 183 |
| **Total** | **573** |

Tool distribution:

| Tool | Upstream | PieCloak | Total |
|---|---:|---:|---:|
| PMD | 333 | 158 | 491 |
| Lizard | 56 | 24 | 80 |
| Opengrep | 1 | 1 | 2 |

PieCloak-attributed distribution:

| Module | Count |
|---|---:|
| Core | 62 |
| PacketEvents | 93 |
| Paper platform | 28 |

| Scope | Count |
|---|---:|
| Production code | 133 |
| Tests | 50 |

## Remediation accounting

- Initial PieCloak-attributed findings fixed: **183 of 183**
- Initial upstream findings incidentally solved without changing intended behavior:
  **14**
- Initial findings absent from the final dataset: **197**
- Initial upstream findings remaining: **376**
- Newly reported findings discovered on an intermediate exact head: **11**
- Newly reported findings remaining in the final dataset: **0**

The 11 intermediate findings were not ignored merely because the total count had
fallen. They were individually investigated and remediated. They covered
test-helper PMD findings, repeated suppression literals, an unused parameter,
an identity-comparison suppression mismatch, logging guard findings, and a Lizard
file-size threshold crossed by the remediation. The final export contains none of
their issue IDs.

The Codacy API's `fixed` endpoint count is reported separately because its delta
semantics are not identical to the row-level comparison above.

## Clean recovery and publication history

The prior temporary validation chain was not published unchanged. The validated
source and tests were recovered onto the current PR branch as focused commits,
while temporary apply, validation, publication, export, trigger, patch, and marker
scaffolding was excluded from the product branch.

Focused package-10 commits:

1. `cb19910b04cb361e1d6a7623791802120b03b837` —
   recover validated source/test remediation on top of the recorded PR head.
2. `c7ae66aafe52e4b4ec6672be50d27471f81eb1f6` —
   resolve the 11 newly surfaced exact-head findings.
3. `ee5c80f8c96cdd565eb95edcb1cf8128469741bb` —
   resolve the last row-level PieCloak-attributed logging finding.

No force-push was used. Each PR branch update was a verified fast-forward.

## Validation evidence

The final candidate passed:

- generated LeafPile sources and clean generated-tree verification
- clean compile
- complete ordinary test suite
- shaded build
- snapshot build
- five repeated concurrency-sensitive test passes
- plugin and build metadata inspection
- repository PMD 7
- Codacy-compatible PMD 6.55
- Lizard 1.23
- Semgrep CE
- Trivy

Key runs:

| Purpose | Run | Result |
|---|---:|---|
| Recovered temporary-head validation | `31078916622` | success |
| Corrected clean-candidate validation | `31083172095` | success |
| Final two-file correction validation | `31084088462` | success |
| Exact-head Build | `31084466146` | success |
| Exact-head Static analysis | `31084466189` | success |
| Complete final Codacy export | `31084577169` | success |

## Hostile review

The final clean diff was reviewed against the recorded PR head, the corrected
temporary source tree, and package-10 requirements. The review specifically
checked lifecycle/shutdown ownership, transition retry behavior, visibility
timing and flush order, identity comparison semantics, logging filtering, test
coverage, and accidental publication of temporary scaffolding.

No unresolved behavior-changing defect was found. The final two-file correction
only routes an internally level-filtered CubiLogging call through the existing
package-10 logging adapter.

## Published evidence

- Row-level dataset:
  `ai-agents/reports/codacy/package-10-findings.csv`
- This summary:
  `ai-agents/reports/codacy/package-10-summary.md`

The CSV contains all 573 initial findings plus all 11 newly reported intermediate
findings, their provenance evidence, intermediate/final presence, and disposition.
