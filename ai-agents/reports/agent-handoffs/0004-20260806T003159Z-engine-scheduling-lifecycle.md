# Package 04 handoff — Engine scheduling and lifecycle

- Date/time: 2026-08-06 00:31:59 UTC / 2026-08-05 20:31:59 America/Indiana/Indianapolis
- Agent role: sequential implementation worker
- Repository: `wsg138/PieCloak`
- Coordination branch: `main`
- Coordination starting SHA: `8e16917e4f0d55d212a87dfc85be3fe796a164d6`
- Pull request: `#11`
- Implementation branch: `agent/sync-upstream-clean-history`
- Starting implementation head: `3e18acde563809f68f003229266258687ce8ce10`
- Ending implementation head: `f203d7d1fe4781936fa69d4f7cf96083bbf73ab7`
- Package: `04-engine-scheduling-lifecycle`
- State: `READY_FOR_AGENT`

## Live reconciliation

`ai-agents/WORKSPACE-STATE.md` selected package 04 and recorded PR head `3e18acde563809f68f003229266258687ce8ce10`; live GitHub matched that routing when work began. `main` was `8e16917e4f0d55d212a87dfc85be3fe796a164d6`.

PR #11 was open, non-draft, unmerged, based on `main`, and used branch `agent/sync-upstream-clean-history`. The only other open pull requests were Dependabot PRs #4 through #9; none overlapped this implementation package. Active branches and recent commits were inspected, including the non-authoritative package-03 validation branch. No pull-request reviews, requested changes, or unresolved review threads existed. The starting exact head had successful Build, Static analysis, and CodeRabbit evidence.

Immediately after implementation, PR #11 remained open and unmerged at exact head `f203d7d1fe4781936fa69d4f7cf96083bbf73ab7`, with no reviews or review threads. Live code and checks were used over stale PR-body text.

## Scope completed

Package 04 is complete:

- replaced pre-counted async workers with a submission-gated completion contract;
- made scheduler rejection, partial submission, inline execution, worker failure, setup failure, and shutdown converge on one exact-once tick-finalization path;
- added an explicit engine shutdown state and bounded drain;
- paired PacketEvents and Bukkit registration with idempotent unregistering;
- added reverse-order transactional startup ownership and partial-startup rollback;
- reset plugin-owned registries and singleton references only after async work and listener teardown are proven complete;
- made successful disable/re-enable create fresh effective components;
- explicitly blocks same-JVM re-enable when cleanup cannot be proven, rather than allowing stale components to run;
- made shutdown null-safe and idempotent after partial startup.

Package-05 optional-integration, stale bypass-identity, update-checker, join-window, and sensitive-diagnostic work was not started.

## Implementation commits

1. `43d6765a5a6e2e5b4cd836bdc755a78577d6fd99` — Fix partial async tick submission finalization.
2. `fea86e17bde44990ed42c7515648f9dddc988118` — Make plugin startup and shutdown transactional.
3. `6944ee56503fcab8e2da11bcbe38765a425f2cea` — Harden lifecycle cleanup failure fencing.
4. `63433c5889116328428e9afa20d10fc4059c307b` — Close remaining startup ownership gaps.
5. `f203d7d1fe4781936fa69d4f7cf96083bbf73ab7` — Complete composite listener cleanup.

The package used five focused commits rather than the suggested two to three because exact-head CI and hostile review exposed independent lifecycle defects that required separately reviewable corrections.

## Design and behavior

### Async scheduling

The old engine initialized a worker count before scheduler submission. A fast accepted worker could finish before a later submission failed, decrement the count to zero, and leave no worker responsible for finalization.

`AsyncTickWork` now owns an initial submission permit plus one idempotent permit per attempted worker. Worker completion cannot finish the tick until submissions close. A rejected submission cancels only its own permit; accepted workers drain normally. A scheduler that executes a task and then throws cannot double-release that permit. Worker `Throwable`s are reported while their permit is still released in `finally`.

`AsyncEngine` now tracks shutdown and active work explicitly. Setup failures release the running tick state, accepted work finalizes once, and shutdown rejects new ticks, cancels pending reservations, and waits only for a bounded interval. Shared state is not reset while accepted workers may still touch it.

### Lifecycle ownership

`LifecycleScope` owns startup resources and closes them once in reverse order. Cleanup continues after individual failures and preserves them as suppressed exceptions. The tick source is registered with the lifecycle before later startup work and starts only after setup succeeds.

PacketEvents controllers retain the exact `PacketListenerCommon` registration handle returned by PacketEvents and unregister that handle. Bukkit listeners and the world controller unregister through `HandlerList`. Folia retains and cancels its scheduled task. Composite Fancy compatibility cleanup attempts both child listeners even when one close fails.

Global view factories, entity-type exclusions, player state, bypass sets, controller singleton references, ticker/engine references, and logger binding now have explicit reset/rebind behavior. Successful disable/re-enable constructs fresh effective listeners/controllers. If engine drain or critical unregistering fails, the old shared state remains fenced and re-enable is rejected until a process restart rather than risking stale callbacks.

The core completion and lifecycle mechanisms remain platform-neutral. Paper, Folia, Bukkit, and PacketEvents specifics stay at adapter/controller boundaries, preserving a contained future stable Paper 26.2-or-newer migration.

## Files and architecture changed

Important implementation files on the PR branch:

- `core/.../engine/AsyncEngine.java`
- `core/.../engine/AsyncTickWork.java`
- `core/.../lifecycle/LifecycleScope.java`
- `core/.../Core.java`, `Ticker.java`
- `core/.../view/ViewRegistry.java`
- `core/.../players/PlayerRegistry.java`
- `core/.../entity/EntityBypassRegistry.java`
- `core/.../config/raycast/EntityTypeExclusions.java`
- `packetevents/.../PacketEventsCommonViewController.java`
- `platform-paper/.../RaycastedAntiESP.java`
- `platform-paper/.../EventListener.java`, `FancyCompatibility.java`
- Paper/Folia ticker and listener utilities
- Paper PacketEvents entity, block, and common controllers
- focused tests for submission races, shutdown, lifecycle rollback/fresh instances, and registry reset.

No build baseline, workflow, plugin target, or ordinary product documentation was changed. The package diff is five commits, 25 files, and is strictly ahead of the starting PR head with no unrelated merge.

## Tests and validation

### Deterministic local checks

- A standalone Java 21 `AsyncTickWork` harness was compiled with `javac --release 21` and passed 500 of 500 repeated deterministic iterations covering first rejection, partial rejection, inline completion before later rejection, worker failure, shutdown during submission, queued workers during shutdown, and run-then-throw submission.
- `LifecycleScope.java` was compiled locally with Java 21.
- The final artifact was inspected with:
  - `unzip -q /mnt/data/PieCloak-f203d7d1fe.zip -d /tmp/piecloak-artifact`
  - `sha256sum <exact shaded jar>`
  - `unzip -p <exact shaded jar> plugin.yml`
  - `unzip -p <exact shaded jar> META-INF/MANIFEST.MF`

### Exact-head GitHub Actions

Exact implementation head: `f203d7d1fe4781936fa69d4f7cf96083bbf73ab7`.

- Build run `31059767805`, first job `92484943305`: success.
- The same exact-head build job was rerun independently as job `92485323007`: success.
- Both executions completed `./gradlew clean verifyCurrentPlatformBaseline :platform-paper:compileJava test :platform-paper:build :platform-paper:buildSnapshot --no-daemon`, staging-JAR inspection, and artifact upload.
- Static analysis run `31059767818`: success.
  - PMD job `92484943327`: success.
  - Semgrep CE job `92484943326`: success.
  - Trivy job `92484943312`: success.
- Combined commit status: CodeRabbit success.
- Pull-request reviews and unresolved review threads: none.

An intermediate implementation head `fea86e17bde44990ed42c7515648f9dddc988118` failed Build run `31058867997`, job `92482214201`, because PacketEvents requires unregistering a returned `PacketListenerCommon` registration handle rather than the listener object. Core tests had already run successfully before that platform compile failure. Commit `6944ee56503fcab8e2da11bcbe38765a425f2cea` corrected ownership of the exact handles; the final exact-head executions are green.

### Exact artifact

- Workflow artifact: `PieCloak-f203d7d1fe4781936fa69d4f7cf96083bbf73ab7`
- Artifact archive digest: `sha256:8474bae9f39476ed6ac313fc336ad6c126c1164f87edaba50074869267b85070`
- Shaded JAR: `RaycastedAntiESP-0.7.0-SNAPSHOT-Paper-0.10.0-SNAPSHOT+build-2026-08-06T00-27-58.968337997Z+git-f203d7d1.jar`
- JAR SHA-256: `91a178f90b558bef0901d688e49d49e776d7fac5f248216a5b558c9d73981854`
- Generated metadata declares PieCloak, main class `games.cubi.raycastedantiesp.paper.RaycastedAntiESP`, API `1.21.11`, PacketEvents dependency, Fancy soft dependencies, and Folia support.
- Manifest retains Mojang paperweight mappings.

## Hostile review

The full package diff was reviewed after implementation as foreign code. Confirmed defects found and fixed during that pass:

- setup exceptions before worker ownership could leave `tickState` running;
- a scheduler that executes then throws could double-release a worker;
- PacketEvents unregistering initially used the wrong object instead of the returned handle;
- a ticker or common controller could register before lifecycle ownership;
- failed teardown could be followed by a second disable that incorrectly cleared fenced state;
- engine-drain state had to become unsafe before calling shutdown so a thrown shutdown could not permit reset;
- registration rollback had to preserve the original exception and suppress cleanup failures;
- composite compatibility teardown had to attempt both listeners after the first failure;
- old listener instances needed proof that they become ineffective while a fresh lifecycle remains effective.

No package blocker remains in the reviewed diff. No sensitive payloads were added to logs. The implementation does not broaden version-specific coupling beyond Paper/PacketEvents adapters.

## Remaining risks and manual tests

Unverified live behavior, to be retained for final coordination:

- real Leaf 1.21.11 enable, disable, and same-JVM re-enable with players online;
- real Geyser/Floodgate viewers across shutdown/re-enable;
- Folia runtime task cancellation and re-enable;
- real PacketEvents injected failures, especially a transport that writes then throws;
- forced five-second engine-drain timeout and critical listener-unregister failure on a running server;
- optional Fancy plugin absence/one-present/both-present startup paths, which belong to package 05.

Unit and CI evidence proves the deterministic ownership contracts but cannot prove plugin-manager or network behavior that was not executed on a live server.

## Main coordination update

The coordination-only commit updates exactly:

- this new timestamped report;
- `ai-agents/WORKSPACE-STATE.md`;
- `ai-agents/reports/agent-handoffs/CURRENT.md`;
- `ai-agents/reports/agent-handoffs/INDEX.md`.

No product code, tests, build files, workflows, plugin metadata, or ordinary product documentation are placed on `main`.

## Next route

Advance to `05-optional-integrations-hardening`. Package 04's exact-head implementation and validation are complete, so the next worker can safely build on the new lifecycle contract while making Fancy integrations absence-safe, correcting bypass identity cleanup, and fixing update-checker, join-window, and sensitive-diagnostic defects.
