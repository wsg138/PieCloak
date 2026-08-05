# Package 03 handoff — Entity-transition reconciliation

- Date/time: 2026-08-05 23:33:18 UTC
- Agent role: Sequential repository worker #3
- Repository: `wsg138/PieCloak`
- Coordination branch: `main`
- Coordination starting SHA: `7ab0fc560eb63a78c3f375615d7182820a0e3d86`
- Pull request: `#11`
- Implementation branch: `agent/sync-upstream-clean-history`
- Starting implementation head: `dd1cf6c2784a721cff6c1d5fd437cb5b65f5616b`
- Ending implementation head: `3e18acde563809f68f003229266258687ce8ce10`
- Package: `03-entity-transition-reconciliation`
- State: `READY_FOR_AGENT`

## Live reconciliation

- `main` remained at `7ab0fc560eb63a78c3f375615d7182820a0e3d86` through implementation and exact-head validation.
- PR #11 remained open, non-draft, mergeable, based on `main`, and sourced from `agent/sync-upstream-clean-history`.
- The PR branch remained at the recorded `dd1cf6c2784a721cff6c1d5fd437cb5b65f5616b` until this package was published; no newer implementation work was overwritten.
- The only other open PRs were Dependabot PRs #4–#9; no competing implementation PR existed.
- No reviews, requested changes, or unresolved review threads existed before work or after exact-head validation.
- Starting exact-head evidence at `dd1cf6c2784a721cff6c1d5fd437cb5b65f5616b` was Build run `31052209121`, Static analysis run `31052209222`, and successful CodeRabbit status.
- No worker changed `main` routing or the PR branch after the recorded package-02 handoff.
- A temporary no-PR validation branch, `agent/package03-worker-20260805`, was used because a local checkout was unavailable. The final PR commit was rebuilt as one clean commit from the validated product tree and excludes all temporary workflow/payload files. The connector did not expose branch deletion, so the temporary branch remains non-authoritative evidence only.

## Scope completed

Package 03 was current because `WORKSPACE-STATE.md` selected `03-entity-transition-reconciliation` after package 02 completed. The package is complete:

- Replaced whole-operation entity retry with immutable staged SHOW/HIDE plans.
- SHOW now checkpoints spawn, position correction, head look, replay packets, passengers, vehicle, and leash relationships independently.
- HIDE checkpoints destroy separately.
- Every successful packet write advances the external-action checkpoint immediately; later failures resume at the first incomplete packet instead of repeating spawn or destroy.
- Local client-visibility bookkeeping is repaired independently from packet delivery.
- New opposite transitions inherit packet-confirmed visibility from canceled partial work when entity identity and world epoch still match.
- Retries use bounded exponential backoff, per-viewer/global capacity limits, stale-work validation, and explicit cleanup for disconnect, join/respawn, destroyed entity IDs, world changes, despawn, and entity-ID reuse.
- Direct forced-show/bypass paths use the same staged reconciliation behavior.
- Focused regression tests and manual fault-injection guidance were added.

## Implementation commits

- `3e18acde563809f68f003229266258687ce8ce10` — `Reconcile entity visibility in bounded stages`: complete package-03 implementation, tests, and `TESTING.md` guidance as one clean PR-branch commit.

## Design and behavior

The confirmed failure mechanism was that the prior retry wrapped the complete multi-packet SHOW operation. Visibility was committed only after every packet succeeded, so a failure after spawn could cause a later retry to resend spawn and potentially leave client/server entity state inconsistent.

`EntityTransitionPlan` now captures an immutable ordered packet sequence. `EntityTransitionWork` owns the current packet index, bookkeeping state, world epoch, entity identity, retry count, and due tick. A packet is considered committed only after the packet writer returns successfully, and the next packet is never attempted until that checkpoint advances. Position/head corrections precede replay packets; replay preserves snapshot order; relationship repair follows as passengers, vehicle, then leash packets.

Spawn/destroy packet confirmation and `clientVisible` bookkeeping are separate. If bookkeeping throws after an externally successful spawn or destroy, retry performs only bookkeeping. If a newer opposite transition supersedes that work, confirmed external visibility transfers to the new work before deciding whether spawn, sync-only repair, or destroy is required.

Retries stop after eight failures and use exponential backoff capped at 20 ticks. The queue is capped at 256 entries per viewer and 4096 globally. Capacity exhaustion rejects and reports the newest failed repair instead of silently evicting older partial work. Queue keys include viewer UUID, player/entity view kind, and entity UUID; current world epoch, entity ID, UUID, object identity, and desired visibility are revalidated before retry.

The implementation remains in the PacketEvents/controller boundary and uses existing snapshot/packet abstractions. It does not introduce Paper-internal coupling and does not claim Paper 26.2 support.

## Files and architecture changed

PR branch changes are limited to seven product files:

- `TESTING.md` — added manual staged entity fault-injection scenarios and honest live-test status.
- `packetevents/src/main/java/games/cubi/raycastedantiesp/packetevents/viewcontrollers/EntityTransitionPlan.java` — new immutable staged packet plan.
- `packetevents/src/main/java/games/cubi/raycastedantiesp/packetevents/viewcontrollers/EntityTransitionWork.java` — new packet/bookkeeping checkpoint state machine with bounded backoff.
- `packetevents/src/main/java/games/cubi/raycastedantiesp/packetevents/viewcontrollers/EntityTransitionRetryQueue.java` — bounded keyed retry queue, supersession, cleanup, and capacity rejection semantics.
- `packetevents/src/main/java/games/cubi/raycastedantiesp/packetevents/viewcontrollers/PacketEventsEntityViewController.java` — staged planning/execution, validation, supersession transfer, cleanup, and bounded diagnostics.
- `packetevents/src/test/java/games/cubi/raycastedantiesp/packetevents/viewcontrollers/EntityTransitionRetryQueueTest.java` — queue, supersession, bounds, cleanup, and confirmed-state tests.
- `packetevents/src/test/java/games/cubi/raycastedantiesp/packetevents/viewcontrollers/EntityTransitionWorkTest.java` — packet-stage ordering, every-stage fault, visibility-bookkeeping, transient, and persistent-failure tests.

No build files, workflows, plugin metadata, configuration, or coordination files changed on the PR branch.

## Tests and validation

Package-focused validation used GitHub Actions run `31056041879`, job `92473640155`, on the final validated product tree:

- Temurin JDK `21.0.11+10`.
- `./gradlew :packetevents:test --tests '*EntityTransitionRetryQueueTest' --tests '*EntityTransitionWorkTest' --no-daemon` — passed.
- The same focused tests were then run 20 additional times with `--rerun-tasks`; all 20 passed.
- `./gradlew clean verifyCurrentPlatformBaseline :platform-paper:compileJava test :platform-paper:build :platform-paper:buildSnapshot --no-daemon` — passed.
- Shaded-JAR inspection confirmed the new transition classes and metadata for Minecraft `1.21.11`, Paper development bundle `1.21.11-R0.1-SNAPSHOT`, and Java `21`.

Exact final PR head `3e18acde563809f68f003229266258687ce8ce10` then passed:

- Build run `31056637735`, job `92475453086`: exact-SHA checkout, baseline verification, compilation, all repository tests, Paper build, snapshot build, staging-JAR inspection, and artifact upload succeeded.
- Exact shaded JAR: `RaycastedAntiESP-0.7.0-SNAPSHOT-Paper-0.10.0-SNAPSHOT+build-2026-08-05T23-31-25.128790727Z+git-3e18acde.jar`.
- Exact shaded-JAR SHA-256: `1e776b5b352026058e064e6200c6eed9feea6466ed41edd7d5d1d0d1db4a8a0d`.
- Generated metadata recorded long Git SHA `3e18acde563809f68f003229266258687ce8ce10`, Minecraft `1.21.11`, Paper bundle `1.21.11-R0.1-SNAPSHOT`, and Java `21`; `plugin.yml` declared `name: PieCloak` and API `1.21.11`.
- Static analysis run `31056637857`: Trivy job `92475453277`, Semgrep job `92475453307`, and PMD job `92475453329` all succeeded.
- CodeRabbit commit status succeeded.
- No unresolved review threads or requested changes existed after validation.

No live Leaf server, Geyser/Floodgate client, or injected real-network failure scenario was run.

## Hostile review

The complete package diff was reviewed after the first green implementation run, including failure between every packet/bookkeeping mutation, duplicate spawn/destroy/replay/relationship behavior, stale retries, supersession, queue bounds, disconnect/world/despawn cleanup, entity-ID reuse, logs, tests, 1.21.11 compatibility, and future platform coupling.

Two package blockers were found and fixed before publication:

1. If spawn/destroy succeeded but local `clientVisible` bookkeeping failed, a newer opposite transition could cancel the retry and make a decision from stale local state. Canceled same-entity/same-world work now transfers packet-confirmed visibility before resolving the new action; regression tests cover both spawn and destroy cases.
2. The initial bounded queue evicted older partial repairs when full, silently discarding their checkpoint state. Capacity now preserves queued work, rejects/reports the newest repair, and has per-viewer/global regression tests.

No remaining package blocker or confirmed defect was found. Existing async-engine submission and global shutdown/reinitialization defects remain intentionally routed to package 04 rather than expanding package 03.

## Remaining risks and manual tests

- If an underlying packet writer physically sends a packet and then throws before returning, this layer cannot prove whether the external action committed. Checkpoints advance only after a successful return. A real injected send-then-throw test is still needed to characterize the PacketEvents transport behavior.
- Live rapid SHOW/HIDE changes should be tested with minecarts, passengers, vehicles, and leashes while injecting failures at each stage.
- Live Java and Bedrock/Geyser clients should be tested across disconnect, dimension/world change, despawn, respawn, and entity-ID reuse.
- Plugin disable/re-enable, partial startup, scheduler rejection, and complete static/listener cleanup are package-04 work.
- The temporary validation branch `agent/package03-worker-20260805` remains because branch deletion was unavailable through the connector; it has no PR and must not be treated as routing or implementation authority.

## Main coordination update

The coordination commit updates only:

- `ai-agents/reports/agent-handoffs/0003-20260805T233318Z-entity-transition-reconciliation.md`
- `ai-agents/WORKSPACE-STATE.md`
- `ai-agents/reports/agent-handoffs/CURRENT.md`
- `ai-agents/reports/agent-handoffs/INDEX.md`

No product code, tests, build files, workflows, metadata, configuration, or ordinary product documentation is included on `main`.

## Next route

Advance to `04-engine-scheduling-lifecycle`. Packages 01–03 are complete, exact head `3e18acde563809f68f003229266258687ce8ce10` is green, and package 04 can now address partial async submission, scheduler rejection, shutdown/reset, re-enable, and partial-startup cleanup without overlapping unfinished entity-reconciliation work.
