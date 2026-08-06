# Package 05 handoff — optional integrations and remaining hardening

- Date/time: 2026-08-06T01:25:31Z
- Agent role: sequential implementation worker 05
- Repository: `wsg138/PieCloak`
- Coordination branch: `main`
- Coordination starting SHA: `6382142b05060101758607577e6c758470370308`
- Pull request: `#11`
- Implementation branch: `agent/sync-upstream-clean-history`
- Starting implementation head: `f203d7d1fe4781936fa69d4f7cf96083bbf73ab7`
- Ending implementation head: `1c3b8c572030cdafb96975f36d471142aa9399bc`
- Package: `05-optional-integrations-hardening`
- State: `READY_FOR_AGENT`

## Live reconciliation

The coordination record and live GitHub state agreed before implementation. `main` was `6382142b05060101758607577e6c758470370308`. PR #11 was open, non-draft, unmerged, based on `main`, and its implementation branch was exactly `f203d7d1fe4781936fa69d4f7cf96083bbf73ab7`. The other open pull requests were Dependabot PRs #4–#9; none overlapped the active implementation branch. The previously recorded temporary package-03 validation branch was non-authoritative and was not used. There were no submitted reviews, requested changes, or unresolved review threads.

The starting exact head had successful Build run `31059767805`, Static analysis run `31059767818`, and CodeRabbit status. Immediately before every implementation push, the remote PR-branch head was re-read and had not moved. Immediately before this coordination update, `main` was still `6382142b05060101758607577e6c758470370308` and the PR branch was still the validated ending head.

## Scope completed

Package 05 was current because packages 01–04 were complete and `WORKSPACE-STATE.md` routed the next worker to `05-optional-integrations-hardening`.

Completed package requirements:

- optional FancyNpcs and FancyHolograms API classes are no longer loaded on absent-plugin paths;
- each enabled optional integration initializes independently and failure-soft without disabling PieCloak;
- NPC removal and hologram deletion explicitly clear global bypass IDs, while cancelled removals preserve the registry entry;
- existing ordinary Bukkit entity removal and plugin-shutdown registry reset paths were confirmed and regression-tested for ID reuse;
- update-check responses use the configured connection, enforce a 50 KiB limit before and during accumulation, decode UTF-8, and do not use a second unconfigured connection;
- update-check scheduling failure completes exceptionally instead of waiting only for timeout, and audience delivery remains non-blocking through `PaperScheduler.runForAudience`;
- the join notification window now uses elapsed monotonic ticks with correct lower and upper bounds, including integer counter wrap;
- block-entity diagnostics were inspected and confirmed not to serialize or log raw NBT;
- `plugin.yml`, compile-only dependency boundaries, the build workflow, and shaded metadata were inspected; no build, metadata, configuration, or product-documentation change was required beyond source and tests.

No unrelated package was started.

## Implementation commits

1. `c794b09a91ce4edc06de58f644ca4c48ce3abc62` — isolate optional Fancy integrations behind plugin-presence checks and separate load boundaries; add lifecycle cleanup tests.
2. `20f709be0944c83d8f2a16eecec62a33022a877d` — use one configured updater connection and bound response accumulation.
3. `da30d80d08ad44c48544eb393ed43b34e5dee44a` — correct the elapsed-tick join update window and add boundary/overflow tests.
4. `8cffae86696fe33541ea9de19032b7ede440c691` — hostile-review fix to ignore cancelled optional removals before clearing bypass IDs.
5. `1c3b8c572030cdafb96975f36d471142aa9399bc` — make optional resource ownership transfer explicit and close the response-limit test stream.

## Design and behavior

The prior implementation referenced both optional APIs directly from one eagerly constructed class. Even with `softdepend`, JVM resolution or constructor execution could fail when either plugin was absent or incompatible. `FancyCompatibility` now checks the exact Bukkit plugin name first, then loads a string-named integration class through reflection only for an enabled plugin. FancyNpcs and FancyHolograms API imports live in separate classes, so absence of one API cannot resolve the other. Successful listeners transfer ownership into the existing reverse-order startup lifecycle and are closed on disable or failed startup.

Global fake-entity bypass entries now follow explicit optional-plugin deletion events in addition to the existing outbound destroy/Bukkit removal cleanup. Per-viewer hologram hide events intentionally do not remove a global ID because other viewers may still need it. Cancellable deletion events are observed at `MONITOR` with `ignoreCancelled = true`; state is not committed until the external deletion is allowed to proceed. Registry cleanup is idempotent and removes both bypass and relationship-support classifications before an entity ID can be reused.

The updater now opens one `URLConnection`, applies connect/read timeouts to that same object, rejects an oversized declared content length before opening the body, and reads at most one byte beyond the 50 KiB bound before failing. No unbounded `readAllBytes` or second connection remains. Network and parsing work stays on Bukkit's async scheduler; the eventual message is submitted through the existing audience scheduler without blocking a scheduler thread.

The join-time check now computes `currentTick - joinTick` and accepts only elapsed values 0–9. Java integer subtraction preserves the intended short window across tick-counter wrap. This logic remains isolated from future Paper 26.2 work and keeps the production target at Minecraft 1.21.11.

## Files and architecture changed

PR branch source:

- `platform-paper/src/main/java/games/cubi/raycastedantiesp/paper/FancyCompatibility.java`
- `platform-paper/src/main/java/games/cubi/raycastedantiesp/paper/FancyNpcsCompatibility.java`
- `platform-paper/src/main/java/games/cubi/raycastedantiesp/paper/FancyHologramsCompatibility.java`
- `platform-paper/src/main/java/games/cubi/raycastedantiesp/paper/UpdateChecker.java`
- `platform-paper/src/main/java/games/cubi/raycastedantiesp/paper/EventListener.java`

PR branch tests:

- `core/src/test/java/games/cubi/raycastedantiesp/core/entity/EntityBypassRegistryTest.java`
- `platform-paper/src/test/java/games/cubi/raycastedantiesp/paper/FancyCompatibilityTest.java`
- `platform-paper/src/test/java/games/cubi/raycastedantiesp/paper/UpdateCheckerResponseLimitTest.java`
- `platform-paper/src/test/java/games/cubi/raycastedantiesp/paper/EventListenerTest.java`

No build files, workflows, plugin metadata, configuration, or ordinary product documentation changed. The existing `compileOnly` optional dependencies and `softdepend: [FancyNpcs, FancyHolograms]` metadata remain the correct boundary.

## Tests and validation

A usable local checkout was not available in this worker environment: direct clone/download attempts could not resolve GitHub, and the `gh` CLI was unavailable. Validation therefore used exact-head GitHub Actions and direct source/artifact evidence rather than claiming local execution.

Exact ending head `1c3b8c572030cdafb96975f36d471142aa9399bc`:

- Build run `31062577947`, job `92493428070`: **success**.
- Command: `./gradlew clean verifyCurrentPlatformBaseline :platform-paper:compileJava test :platform-paper:build :platform-paper:buildSnapshot --no-daemon`.
- Environment: Temurin Java `21.0.11+10`; configured Minecraft `1.21.11`; Paper development bundle `1.21.11-R0.1-SNAPSHOT`.
- Result: all repository test tasks completed, Paper source compiled, shadow/staging JAR built, and `BUILD SUCCESSFUL` was recorded.
- Static analysis run `31062577977`: **success**.
- CodeRabbit combined status: **success**.
- Reviews/requested changes/unresolved threads after implementation: none.

Exact shaded artifact inspected by CI:

- file: `RaycastedAntiESP-0.7.0-SNAPSHOT-Paper-0.10.0-SNAPSHOT+build-2026-08-06T01-23-38.543716528Z+git-1c3b8c57.jar`;
- JAR SHA-256: `b8c1323e9e33993d1b23f5691cc1c3ef7b912d5173ba2ce3ea43be6e51fd801f`;
- embedded long Git SHA: `1c3b8c572030cdafb96975f36d471142aa9399bc`;
- plugin name/API: `PieCloak`, API `1.21.11`;
- embedded platform baseline: Minecraft `1.21.11`, Paper bundle `1.21.11-R0.1-SNAPSHOT`, Java `21`;
- required dependency: `packetevents`; optional dependencies: `FancyNpcs`, `FancyHolograms`;
- uploaded artifact ID: `8952665169`; uploaded archive digest: `3200c19e6382bb34cf500536fa155dff90fbae9d750dc7783ff9b4627b584782`.

The package added no concurrency-sensitive test that required repeated randomized runs. All package tests ran as part of the exact-head full test suite once. Live Leaf, Geyser/Floodgate, optional-plugin, network-failure, and scheduler-rejection scenarios were not run.

PMD completed and remained non-fatal under the repository's existing configuration. It reported seven main-source findings: six established reflective/static-design findings plus one `CloseResource` warning on the new optional-integration ownership transfer. The resource is transferred to the lifecycle-owned list and closed in reverse order; no suppression was added merely to silence the scanner. Package 06 should decide whether a scanner-specific refactor is worthwhile during final integration review.

## Hostile review

The complete five-commit package diff was reviewed after implementation with emphasis on absent dependencies, partial initialization, cancellation, cleanup ordering, entity-ID reuse, update I/O bounds, scheduler rejection, shutdown, logging, and tests that could pass without proving behavior.

Confirmed defects found and fixed during hostile review:

- optional NPC and hologram deletion events are cancellable; clearing the bypass registry on a cancelled deletion could expose a still-live fake entity to filtering. Both handlers now ignore cancelled events;
- successful optional resources were initially returned through a local ownership handoff that produced an ambiguous analyzer warning and allowed a post-construction success-log failure to complicate ownership. The success-log side effect was removed and ownership now transfers directly into the lifecycle consumer;
- the updater response test's synthetic stream was not closed. It now uses try-with-resources.

No duplicate global cleanup is harmful because `markEntityDespawned` is idempotent. Per-viewer hologram hiding is not treated as global despawn. No state is removed before a cancellable external deletion succeeds. Disabled optional plugins never resolve their integration classes. Update parsing cannot accumulate an unbounded body. No raw NBT logging was found in the inspected production path.

Unrelated or final-coordinator observations:

- PR #11's descriptive body still contains historical package-01/current-head text and should not be treated as routing authority; `main` state and handoffs are authoritative.
- Existing non-package PMD findings remain for final coordinator review.

## Remaining risks and manual tests

- Start/disable/re-enable on the production Leaf 1.21.11 stack with neither Fancy plugin installed, each plugin installed individually, both installed, and an intentionally incompatible optional API was not live-tested.
- Create, cancel deletion of, delete, and reuse entity IDs for FancyNpcs/FancyHolograms were unit/source validated but not observed on a live server.
- Real Modrinth responses, chunked oversized responses, timeouts, DNS failures, and Bukkit/Folia scheduler rejection were not injected live.
- The in-game join notification boundary was unit-tested but not observed with a real player joining through Java and Geyser/Floodgate.
- Raw-NBT absence was established by source/log-path inspection, not by a live malformed block-entity packet.
- No Paper 26.2 support claim or validation was made.

## Main coordination update

This coordination-only commit updates exactly:

- new report `ai-agents/reports/agent-handoffs/0005-20260806T012531Z-optional-integrations-hardening.md`;
- `ai-agents/WORKSPACE-STATE.md`;
- `ai-agents/reports/agent-handoffs/CURRENT.md`;
- `ai-agents/reports/agent-handoffs/INDEX.md`.

No product source, tests, build files, workflows, metadata, configuration, or ordinary documentation are placed on `main`.

## Next route

Advance to `06-final-integration-review`. Packages 01–05 are complete on the active PR branch, the exact package-05 ending head is green in Build and Static analysis, CodeRabbit is successful, and no review thread or requested change blocks final cross-package review. Package 06 must perform the final integration/brutal review and issue the repository's READY/NOT READY verdict; this worker stops here.
