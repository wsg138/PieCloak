# Package 02 handoff — Block-transition reliability

- Date/time: 2026-08-05T22:24:48Z
- Agent role: sequential repository worker
- Repository: `wsg138/PieCloak`
- Selected package: `02-block-transition-reliability`
- Pull request: `#11 — Sync latest RaycastedAntiESP upstream and preserve PieCloak filtering`
- Implementation branch: `agent/sync-upstream-clean-history`
- Starting main coordination SHA: `4db2eb396e69e41852adaa54d16f7975a78cde39`
- Starting PR SHA: `d23c6a577ead79fb4d70b230d1344a91095fb97b`
- Ending PR SHA: `dd1cf6c2784a721cff6c1d5fd437cb5b65f5616b`
- Outcome: `COMPLETE`; route is ready for package `03-entity-transition-reconciliation`

## Live reconciliation

`ai-agents/WORKSPACE-STATE.md` selected `02-block-transition-reliability`, so this worker completed exactly that package. At the start, live `main` was `4db2eb396e69e41852adaa54d16f7975a78cde39`, PR #11 was open and non-draft against `main`, and the active implementation branch was at `d23c6a577ead79fb4d70b230d1344a91095fb97b`.

The other open pull requests were unrelated Dependabot updates. PR #11 had no submitted reviews, requested-change reviews, requested reviewers, or unresolved review threads. The implementation branch and `main` were re-read immediately before their updates; neither moved unexpectedly and no newer work was overwritten.

PR metadata still reported the older PR base SHA `7d0d38cba0f0add8e39354a1047691db28851e25`. Direct ref inspection established that live `main` was `4db2eb396e69e41852adaa54d16f7975a78cde39`, a one-commit descendant of `7d0d38c`, containing the package 01 handoff. The live routing files on that head still selected package 02.

A local checkout could not be obtained because the worker runtime could not resolve GitHub hosts. Repository inspection and mutation used the connected GitHub application. Validation claims below come from GitHub Actions and the downloaded exact-head artifact, not an unverified local build.

## Implementation commits

1. `4013fe7b64d32df33ecfe22db3b66ee0aa09397a` — `Harden block transition repair and retries`
2. `c5887d0f6592096a71a9921450e87fe3be440a68` — `Avoid Adventure dependency in block diagnostics`
3. `d46673143e88aaa1fc32690d64f44e92898f3dfe` — `Trigger exact-head validation after diagnostic fix` — tree-equivalent trigger commit because a GitHub Actions token push does not start downstream pull-request workflows
4. `dd1cf6c2784a721cff6c1d5fd437cb5b65f5616b` — `Keep target resolver test on the Paper test classpath`

All commits were pushed normally to `agent/sync-upstream-clean-history`. No force-push, merge, deployment, production access, or server modification occurred.

A temporary self-deleting branch was used to apply the one-line diagnostics correction with an exact-head guard after the connector could not safely transfer the complete controller file. The first guarded run refused the edit without changing PR #11; the corrected run committed `c5887d0` and deleted the temporary branch. A separate temporary validation branch ran the focused tests 25 times and also deleted itself. Neither temporary workflow is present in the PR tree.

## Behavior changed

### Failure-isolated packed transitions

`PackedBlockTransitionQueue` now isolates runtime failures from individual callbacks, continues through every remaining transition in the packed entry and later queue entries, then rethrows the first failure with later failures suppressed. A middle-entry failure no longer discards unrelated HIDE or SHOW transitions.

### Staged, bounded block repair

A new `BlockTransitionRetryQueue` records per-viewer repair work with explicit operations (`HIDE`, `SHOW`, and mode-disable repair) and packet stages (`BLOCK` and `BLOCK_ENTITY_DATA`). Its key includes tile identity, expected block ID, world epoch, mode token, and operation. It deduplicates equivalent work, keeps the earliest incomplete stage, uses bounded exponential delay up to 20 ticks, caps each viewer at 256 repairs, evicts oldest work at the bound, and clears stale work on mode changes, world changes, or disconnect.

HIDE commits only after the fake-block write succeeds. SHOW and mode-disable repair send the real block first and optional block-entity data second. If the real block succeeds and NBT fails, retry resumes at the NBT stage rather than sending a duplicate real-block update. Retries validate the current world epoch, mode generation, desired visibility, tile removal state, and expected block ID before sending.

### Desired state versus client synchronization

Disabling tile checks now advances the mode generation and commits the desired visible state for every tracked tile even if one repair packet fails. Repair callbacks are failure-isolated across the whole traversal, and failed client synchronization is queued by the packet controller. A failed tile no longer prevents later hidden tiles from being repaired.

### Authoritative block-entity classification

A new `BlockEntitySectionStore` tracks whether positions are authoritative managed block entities, authoritative non-managed positions, or unknown without requiring every full block-state ID to remain cached. Single block updates classify only their replaced position; full chunk parsing marks the whole section known and stores managed positions as a compact bitset. This works with both `track-all-blocks: true` and the production default `false`.

`TargetFilteringBlockInfoResolver` now exposes the same PacketEvents target classifier used during chunk parsing. Standalone managed block-entity data with no tracked tile state fails closed: the packet is cancelled and the hidden fallback block is sent. Known non-managed or virtual/plugin block-entity data remains pass-through. Diagnostics are bounded and do not log NBT payloads.

### Unexpected bulk chunks

Unexpected `MAP_CHUNK_BULK` packets now pass through unchanged instead of throwing from packet handling. Diagnostics are capped at five messages.

## Files and architecture affected

- Core view contract and state: `BlockView`, `AbstractBlockView`, `PackedBlockTransitionQueue`
- New authoritative classifier: `BlockEntitySectionStore`
- Packet repair state machine: `BlockTransitionRetryQueue`, `PacketEventsBlockViewController`
- Chunk classification and replay capture: `AbstractChunkParser`
- Paper target boundary: `TargetFilteringBlockInfoResolver`
- Focused tests across `core`, `packetevents`, and `platform-paper`
- Manual verification guidance: `TESTING.md`

The current implementation remains targeted at Minecraft `1.21.11`. PacketEvents/Paper-specific classification stays at the adapter boundary; no Paper `26.2` support is claimed.

## Tests and validation

### Corrective validation during implementation

Exact-head Build run `31051078343` on `4013fe7b64d32df33ecfe22db3b66ee0aa09397a` failed at `:packetevents:compileJava` because a diagnostics-only call to the PacketEvents block-entity name accessor exposed an Adventure type absent from that module's compile classpath. Commit `c5887d0f6592096a71a9921450e87fe3be440a68` replaced that accessor with classpath-safe string conversion.

Exact-head Build run `31052050047` on `d46673143e88aaa1fc32690d64f44e92898f3dfe` then compiled production code and passed core and PacketEvents tests, but failed at `:platform-paper:compileTestJava` because one Paper test directly imported PacketEvents API types absent from the Paper test classpath. Commit `dd1cf6c2784a721cff6c1d5fd437cb5b65f5616b` retained the production delegation test through the package-private predicate seam without those imports.

### Exact final-head build and scanners

Build run `31052209121`, job `92461727668`, checked out exact commit `dd1cf6c2784a721cff6c1d5fd437cb5b65f5616b` and completed successfully with:

```text
./gradlew clean verifyCurrentPlatformBaseline :platform-paper:compileJava test :platform-paper:build :platform-paper:buildSnapshot --no-daemon
```

Evidence from the job:

- `BUILD SUCCESSFUL in 1m 21s`;
- 34 actionable tasks: 29 executed and 5 up-to-date;
- core, PacketEvents, and Paper tests passed;
- the staging JAR inspection step passed;
- artifact `PieCloak-dd1cf6c2784a721cff6c1d5fd437cb5b65f5616b` uploaded as artifact ID `8948863387` with artifact digest `sha256:33a4934161b22c0ce0bd5cc6ed1229ac77237682edd312d713c4d4e6b883525b`.

Static analysis run `31052209222` checked out the same exact SHA. Semgrep, PMD, and Trivy jobs all concluded successfully. Existing report-only warnings outside this package were not suppressed.

### Repeated focused validation

One-shot validation run `31052271820`, job `92461928996`, checked out and verified exact SHA `dd1cf6c2784a721cff6c1d5fd437cb5b65f5616b`, generated LeafPile sources on Java 21, and ran the following production-path/focused groups 25 times with clean test outputs between runs:

- `PackedTransitionQueueTest`
- `PackedBlockTransitionQueueFailureTest`
- `BlockTransitionRetryQueueTest`
- `PacketEventsBlockTransitionWriteTest`
- `PacketEventsBlockViewTrackingTest`
- `PacketEventsBlockModeRepairTest`
- `TargetFilteringBlockInfoResolverTest`

All 25 iterations completed successfully. The validation branch deleted itself afterward.

### Exact artifact inspection

The downloaded shaded JAR was:

`RaycastedAntiESP-0.7.0-SNAPSHOT-Paper-0.10.0-SNAPSHOT+build-2026-08-05T22-18-33.072459445Z+git-dd1cf6c2.jar`

- JAR SHA-256: `655eb698679858ae49b3cfe410d5d411bf8d9c61a3861057c3ca328122947bd1`
- `plugin.yml`: `name: PieCloak`, correct main class, PacketEvents dependency, `api-version: '1.21.11'`
- `build-properties/platform.yml`: full Git SHA `dd1cf6c2784a721cff6c1d5fd437cb5b65f5616b`, Minecraft `1.21.11`, Paper bundle `1.21.11-R0.1-SNAPSHOT`, Java `21`
- JAR contains the new retry state machine and block-entity classification classes.

The exact final head had no submitted reviews, requested changes, or unresolved review threads.

## Hostile review

After the package diff appeared complete, the full package was reviewed as foreign code with failures considered between every packet write and state mutation.

Confirmed defects found and fixed during that review or exact-head validation:

1. The first classification design depended on full cached block IDs and would fail under the production default `track-all-blocks: false`. It was replaced with authoritative per-section known/managed state.
2. Old mode-generation retries initially survived until due. They now discard immediately when mode or world generation changes.
3. A parser path could allocate repeated all-known section bitsets. It now uses compact shared/immutable chunk data semantics.
4. A diagnostics accessor introduced an undeclared compile-time Adventure dependency. It was removed.
5. One regression test relied on PacketEvents types not exposed on the Paper test classpath. It was rewritten without weakening the behavior asserted.
6. Unknown block-entity diagnostics were checked for sensitive data; NBT is not included.
7. Identity reuse is constrained by tile object identity, expected block ID, removal state, world epoch, and mode generation before repair writes.
8. Disconnect, world replacement, mode toggle, and removed-tile cleanup paths were verified in the production controller/queue contract and focused tests.

No blanket catch was added around packet handling. Runtime packet-write failures are retained as staged synchronization work; unrelated transitions continue. Errors are not deliberately swallowed.

## Remaining risks and unverified behavior

The following are not claimed as passed:

- live Paper `1.21.11` or production Leaf startup;
- Java-client gameplay validation;
- Bedrock behavior through Geyser/Floodgate;
- real injected network/channel write failure on a running server;
- reload, disable/re-enable, shutdown, or production deployment;
- any future stable Paper `26.2` build.

Retries are driven by later outbound packet traffic. If a player receives no subsequent outbound packet after a transient failure, repair waits until traffic resumes.

For an unknown, untracked managed block entity, cancellation prevents the NBT leak and attempts a hidden-block fallback. If that fallback write itself fails, there is no identity-safe tracked tile to enqueue; the client may wait for a later authoritative block/chunk packet to converge.

These limitations are documented rather than hidden and do not expand this package into entity reconciliation or lifecycle work.

## Main coordination update

This report, `ai-agents/WORKSPACE-STATE.md`, `ai-agents/reports/agent-handoffs/CURRENT.md`, and `ai-agents/reports/agent-handoffs/INDEX.md` are committed together on `main` as one coordination-only commit. No product source, tests, build files, workflow, plugin metadata, or ordinary product documentation is included in that commit.

## Next route

- State: `READY_FOR_AGENT`
- Current package: `03-entity-transition-reconciliation`
- Active PR: `#11`
- Active branch: `agent/sync-upstream-clean-history`
- Recorded implementation head: `dd1cf6c2784a721cff6c1d5fd437cb5b65f5616b`
- Next worker must execute only `ai-agents/work-packages/03-entity-transition-reconciliation.md`, then stop after its own handoff.
