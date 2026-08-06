# Package 08 handoff — respawn visibility-state invalidation

- Date/time: 2026-08-06T04:15:55Z
- Agent role: ChatGPT repository worker #7
- Repository: `wsg138/PieCloak`
- Coordination branch: `main`
- Coordination starting SHA: `b7a9a41960502af4bc7b184fe632745959236d5d`
- Pull request: `#11`
- Implementation branch: `agent/sync-upstream-clean-history`
- Starting implementation head: `a6056313378f3fbdbbbb3698a67ca675533ad351`
- Ending implementation head: `35127dd6bff64e9f2d6dd4a1fe5e4ea48995aeb3`
- Package: `08-respawn-visibility-state-invalidation`
- State: `READY_FOR_AGENT`

## Live reconciliation

`ai-agents/WORKSPACE-STATE.md` selected package 08 after package 07 completed, so this worker executed only `08-respawn-visibility-state-invalidation`.

At the start of work, the live coordination head was `b7a9a41960502af4bc7b184fe632745959236d5d`. PR #11 was open, non-draft, unmerged, based on branch `main`, and its live implementation branch head matched the recorded handoff at `a6056313378f3fbdbbbb3698a67ca675533ad351`. The direct `main` ref was treated as authoritative; GitHub's PR metadata retained an older base snapshot SHA because the coordination history is intentionally separate from the implementation branch. No merge from `main` was required or performed.

The other open pull requests were Dependabot PRs #4, #5, #6, #7, #8, and #9. They were left untouched for package 09. PR #11 had no submitted reviews, requested changes, or unresolved review threads. No worker changed `main` routing or the implementation branch while package 08 was being published.

Before the final coordination update, PR #11 remained open, non-draft, unmerged, and mergeable with exact head `35127dd6bff64e9f2d6dd4a1fe5e4ea48995aeb3`. Exact-head Build and Static analysis run 56 succeeded, CodeRabbit status was successful, and there were still no review threads or submitted reviews.

## Scope completed

Package 08 required every outbound `RESPAWN` packet to establish a new client-visible world generation, including death respawns, same-world and same-dimension respawns, different-world respawns, repeated respawns, and bypass viewers. It also required all pre-respawn entity and block transitions, retries, relationship repair, reconciliation, tracked client assumptions, and deferred callbacks to become stale without affecting ordinary non-respawn same-world observation.

The package is complete. Respawn processing now advances the player world epoch even when the world name and UUID are unchanged, clears all world-scoped tracked state, clears pending relationship and reconciliation state, drops block repair work, and fences deferred packet work against the final epoch. The retained player registration and bypass flag are preserved on successful reset. A failed partial reset invalidates only the exact affected player generation rather than publishing a stable half-cleared state.

No unrelated product scope was added. Dependabot overlap, PR-description cleanup, the bypass-permission refresh limitation, and the final release verdict remain package-09 work.

## Implementation commits

- `59c433bfd4e8c61cc7aed7fca90782529b7e24d8` — `Invalidate client state on every respawn`
  - Added the shared respawn state reset, exact-generation unregister protection, deferred epoch fencing, PacketEvents respawn invalidation, Paper entity/block controller integration, and initial regression tests.
- `35127dd6bff64e9f2d6dd4a1fe5e4ea48995aeb3` — `Prove respawn work cannot cross epochs`
  - Added hostile-review regression tests for queued SHOW work, relationship replay, block repair, entity-ID reuse, queue cleanup, and non-respawn packet selection.

## Design and behavior

The failure mechanism was the existing same-world fast path: when the outbound world name matched the tracked world name, it updated only minimum height and returned. The visibility epoch, views, retry queues, unresolved relationships, client-visible flags, and after-send callbacks therefore remained valid from the server's perspective even though the client had discarded its pre-respawn entity universe. A queued SHOW could then run after respawn and create a ghost entity, including when the reused entity ID now represented a different post-respawn entity.

`ClientStateResetter.resetForRespawn` now publishes an odd in-progress epoch before clearing any world-scoped state. It records known entity IDs for expected transition-destroy suppression, clears block, entity, and player views, clears pending reconciliation and unresolved relationship state, clears the self-entity snapshot and own location, updates world metadata, and only then publishes the completed even epoch. Repeated respawns are idempotent generation changes: each successful reset advances the epoch by two and starts from empty world-scoped state.

Cleanup steps are attempted independently and failures are aggregated. If any reset step fails, the affected `PlayerData` generation is conditionally unregistered and left disconnected; it is not published under a completed epoch. `PlayerRegistry.unregisterPlayer(UUID, PlayerData)` uses conditional map removal so delayed cleanup for an obsolete login cannot unregister a newer login generation.

`PacketEventsRespawnStateInvalidator` is invoked before managed-packet filtering, so managed and bypass viewers use the same reset contract. The Paper entity controller wraps only after-send tasks added during its own packet handling with `WorldEpochGuard`, causing pre-respawn deferred work to no-op after disconnect or epoch change. The Paper block controller explicitly removes its viewer repair queue on respawn, independent of listener ordering.

Ordinary `JOIN_GAME`, spawn, and other non-respawn packets do not select the destructive reset path, preserving the existing same-world fast path outside real respawns. The work remains within the current PacketEvents/Paper boundary for Minecraft `1.21.11`; it does not claim or couple the core reset to Paper 26.2 APIs.

## Files and architecture changed

Implementation branch source changes:

- `core/src/main/java/games/cubi/raycastedantiesp/core/players/ClientStateResetter.java`
- `core/src/main/java/games/cubi/raycastedantiesp/core/players/PlayerRegistry.java`
- `core/src/main/java/games/cubi/raycastedantiesp/core/players/WorldEpochGuard.java`
- `packetevents/src/main/java/games/cubi/raycastedantiesp/packetevents/viewcontrollers/PacketEventsCommonViewController.java`
- `packetevents/src/main/java/games/cubi/raycastedantiesp/packetevents/viewcontrollers/PacketEventsRespawnStateInvalidator.java`
- `platform-paper/src/main/java/games/cubi/raycastedantiesp/paper/packets/PaperPacketEventsEntityViewController.java`
- `platform-paper/src/main/java/games/cubi/raycastedantiesp/paper/packets/PaperPacketEventsBlockViewController.java`

Implementation branch test changes:

- `core/src/test/java/games/cubi/raycastedantiesp/core/players/ClientStateResetterTest.java`
- `core/src/test/java/games/cubi/raycastedantiesp/core/players/RespawnEpochBoundaryTest.java`
- `packetevents/src/test/java/games/cubi/raycastedantiesp/packetevents/viewcontrollers/PacketEventsEntityViewControllerTest.java`
- `packetevents/src/test/java/games/cubi/raycastedantiesp/packetevents/viewcontrollers/RespawnRetryBoundaryTest.java`

No build files, workflows, plugin metadata, configuration, or ordinary product documentation changed in package 08.

## Tests and validation

Validation was performed against exact final implementation head `35127dd6bff64e9f2d6dd4a1fe5e4ea48995aeb3`.

Build workflow run 56:

- Run ID: `31070558208`
- Job ID: `92517435936`
- Result: `success`
- Runner: Ubuntu 24.04
- JDK: Temurin `21.0.11+10`
- Gradle: `9.0.0`
- Command: `./gradlew clean verifyCurrentPlatformBaseline :platform-paper:compileJava test :platform-paper:build :platform-paper:buildSnapshot --no-daemon`
- Result: `BUILD SUCCESSFUL in 1m 26s`; 34 actionable tasks, 29 executed and 5 up-to-date.
- Evidence includes successful `core:test`, `packetevents:test`, `platform-paper:test`, Paper compile/build/snapshot, current-platform baseline verification, shaded-JAR inspection, and artifact upload.

Static analysis workflow run 56:

- Run ID: `31070558237`
- PMD job `92517435889`: `success`
- Semgrep CE job `92517435900`: `success`
- Trivy job `92517435976`: `success`

Additional exact-head evidence:

- Combined commit status reported CodeRabbit `success`.
- No unresolved review threads or submitted reviews existed on PR #11.
- Artifact ID: `8955493290`
- Artifact name: `PieCloak-35127dd6bff64e9f2d6dd4a1fe5e4ea48995aeb3`
- Shaded JAR: `RaycastedAntiESP-0.7.0-SNAPSHOT-Paper-0.10.0-SNAPSHOT+build-2026-08-06T04-12-02.876943456Z+git-35127dd6.jar`
- JAR SHA-256: `84bfc1d34d49a974821661f80a1f5b111afa5a1603713b928c8497d52b909824`
- Artifact ZIP upload digest: `1526660c34d43b414d73ae8d1fc71a60df7126beea62c674465726bb3bb484ef`
- `plugin.yml` identified `PieCloak`, API version `1.21.11`, the PacketEvents dependency, and Folia support.
- `build-properties/platform.yml` recorded exact head `35127dd6bff64e9f2d6dd4a1fe5e4ea48995aeb3`, Minecraft `1.21.11`, Paper dev bundle `1.21.11-R0.1-SNAPSHOT`, and Java `21`.
- Independent artifact inspection confirmed the reset, epoch-guard, respawn-invalidator, and modified Paper controller classes were present.

The new logic is deterministic rather than timing-randomized, so no repeated stochastic test loop was required by the repository. The complete exact-head suite ran once in the successful final workflow. A local full build was not claimed because the worker environment lacked `gh` and could not resolve GitHub; GitHub Actions provided the direct exact-head evidence.

## Hostile review

After implementation, the complete package diff was reviewed for failure between each epoch mutation and reset step, partial cleanup, stale retry work, after-send ordering, listener ordering, disconnect and newer-login races, repeated respawn, entity-ID reuse, relationship replay, block repair, sensitive logging, bypass behavior, ordinary non-respawn behavior, and Minecraft `1.21.11` isolation.

The review found one validation defect: the first regression set demonstrated generic epoch fencing but did not separately prove all package-required queued SHOW, relationship, block-repair, entity-ID reuse, and ordinary packet-selection cases. Commit `35127dd6bff64e9f2d6dd4a1fe5e4ea48995aeb3` added those explicit tests. No additional package blocker remained after the final exact-head build and static analysis passed.

No blanket catch was added around packet writes. Existing transition packet commit points and bounded retry semantics remain unchanged; respawn instead invalidates the whole obsolete generation before such work may continue.

## Remaining risks and manual tests

The following scenarios were not run and remain explicitly unverified:

- live Leaf/Paper death, same-world, same-dimension, repeated, and different-world respawns;
- live Java-client observation of hidden entities before and after respawn;
- live Geyser/Floodgate Bedrock respawn behavior;
- injected packet-write or reset-step failure during a real outbound respawn;
- rapid respawn followed by disconnect, world change, despawn, shutdown, or entity-ID reuse under real network scheduling;
- malformed or unresolved respawn world-name behavior on a live server.

No production JAR was deployed and no production server or data was accessed. Stable Paper `26.2` support was not tested or claimed. The bypass-permission refresh limitation remains a package-09 review item.

## Main coordination update

One coordination-only commit updates exactly:

- this new timestamped handoff report;
- `ai-agents/WORKSPACE-STATE.md`;
- `ai-agents/reports/agent-handoffs/CURRENT.md`;
- `ai-agents/reports/agent-handoffs/INDEX.md`.

No plugin source, tests, build files, workflows, metadata, configuration, or product documentation are included on `main`.

## Next route

Select `09-final-integration-pr-cleanup-release-review`. Packages 07 and 08 are complete, PR #11 remains open and unmerged at exact head `35127dd6bff64e9f2d6dd4a1fe5e4ea48995aeb3`, and final exact-head CI is green. Package 09 must independently review the complete PR, reconcile Dependabot overlap and PR metadata, validate its own exact final head, and issue the superseding `READY_FOR_OWNER` or `NOT_READY` verdict. This worker does not begin package 09.
