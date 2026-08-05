# Package 01 handoff — Current Minecraft 1.21.11 platform baseline

- Date/time: 2026-08-05T20:19:40Z
- Agent role: sequential repository worker
- Repository: `wsg138/PieCloak`
- Pull request: `#11 — Sync latest RaycastedAntiESP upstream and preserve PieCloak filtering`
- Implementation branch: `agent/sync-upstream-clean-history`
- Starting main coordination SHA: `7d0d38cba0f0add8e39354a1047691db28851e25`
- Starting PR SHA: `33b615d5ef3a937dafdf461c48e9626ee7d342fc`
- Ending PR SHA: `d23c6a577ead79fb4d70b230d1344a91095fb97b`
- Outcome: `COMPLETE`; route is ready for package `02-block-transition-reliability`

## Live reconciliation

`ai-agents/WORKSPACE-STATE.md` selected `01-current-platform-baseline`, so this worker completed exactly that package. At the start, live GitHub matched the recorded routing: `main` was `7d0d38cba0f0add8e39354a1047691db28851e25`, PR #11 was open and non-draft against `main`, and its branch head was `33b615d5ef3a937dafdf461c48e9626ee7d342fc`.

The other open pull requests were Dependabot updates. They did not advance or replace PR #11. No review threads, submitted reviews, requested-change reviews, or requested reviewers existed on PR #11. The implementation branch and `main` were re-read immediately before each update; neither had moved unexpectedly, and no newer work was overwritten.

A local checkout could not be obtained because the worker runtime could not resolve external Git hosts. Repository inspection, mutations, Actions evidence, artifact inspection, and exact-head verification therefore used the connected GitHub application. No local test result is claimed.

## Scope completed

Package 01 established one explicit build boundary for the active production target:

- Minecraft `1.21.11`;
- Paper development bundle `1.21.11-R0.1-SNAPSHOT`;
- Java `21`;
- generated plugin API metadata `1.21.11`;
- a Paper test-server task using Minecraft `1.21.11` on Java `21`;
- CI that reads the same checked-in values, builds the shaded JAR, and verifies its generated metadata;
- documentation that treats stable Paper `26.2` or newer as a separate future migration rather than the current target.

No packet, visibility-controller, queue, retry, cache, lifecycle, or gameplay implementation was changed in this package.

## Implementation commits

1. `2aa83d42747aa193abc438cef520b53a86e69aff` — `Establish the Minecraft 1.21.11 build baseline`
2. `2d975fd29f7f2e466ab677069f342dbccb3d8a82` — `Make the platform guard configuration-cache safe`
3. `d23c6a577ead79fb4d70b230d1344a91095fb97b` — `Validate pull requests at the exact branch head`

All three commits were pushed normally to `agent/sync-upstream-clean-history`. No force-push, merge, deployment, or production-server change occurred.

## Design and behavior

`gradle.properties` is now the checked-in source of truth for Minecraft, Paper development-bundle, and Java versions. The root build performs exact configuration-time assertions for the current target and exposes `verifyCurrentPlatformBaseline` as the CI/build marker. Java source compatibility, target compatibility, compiler release, and module toolchains use the centralized Java value.

The Paper module consumes the centralized development bundle, run-server Minecraft version, Java launcher, plugin API version, and generated platform metadata. The shaded artifact records the exact Git revision plus Minecraft, Paper bundle, and Java values. The future Paper 26.2 migration remains contained at the build, Paper adapter, generated metadata, and packet-wrapper boundary; this package does not claim Paper 26.2 support.

The Build and Static analysis workflows now explicitly check out `github.event.pull_request.head.sha` for pull-request runs. This prevents a green synthetic merge-ref build from being mistaken for validation of the implementation branch itself.

## Files and architecture changed

- Build source of truth and guard: `gradle.properties`, `build.gradle.kts`
- Module toolchains and Paper boundary: `core/build.gradle.kts`, `packetevents/build.gradle.kts`, `platform-paper/build.gradle.kts`
- Generated identity and baseline metadata: `platform-paper/src/main/resources/plugin.yml`, `platform-paper/src/main/resources/build-properties/platform.yml`
- Exact-head build and scanners: `.github/workflows/build.yml`, `.github/workflows/analysis.yml`
- Product maintenance/testing documentation: `README.md`, `MAINTAINING.md`, `TESTING.md`

## Tests and validation

### Corrective validation during implementation

The first package head, `2aa83d42747aa193abc438cef520b53a86e69aff`, compiled and ran tests but Build run `31042516058` failed because the new verification task captured Gradle script state that configuration cache could not serialize. Commit `2d975fd29f7f2e466ab677069f342dbccb3d8a82` moved the exact assertions to configuration time and removed that captured closure.

The next Build run, `31042796038`, succeeded, but hostile review showed that the default pull-request checkout validated GitHub's synthetic merge commit rather than the implementation commit. Commit `d23c6a577ead79fb4d70b230d1344a91095fb97b` made both workflows check out the exact PR head and made artifact naming use that head SHA.

### Exact final PR-head evidence

Build run `31043407191`, job `92432984793`, checked out exact commit `d23c6a577ead79fb4d70b230d1344a91095fb97b` and completed successfully on Temurin Java `21.0.11+10` with Gradle `9.0.0`.

The exact command was:

```text
./gradlew clean verifyCurrentPlatformBaseline :platform-paper:compileJava test :platform-paper:build :platform-paper:buildSnapshot --no-daemon
```

Evidence from that job:

- `BUILD SUCCESSFUL in 1m 25s`;
- 34 actionable tasks: 28 executed, 1 from cache, 5 up-to-date;
- configuration cache entry stored successfully;
- `locatables:test`, `core:test`, `packetevents:test`, and `platform-paper:test` completed successfully; modules with no test sources were reported as `NO-SOURCE`;
- the shaded JAR was produced as `RaycastedAntiESP-0.7.0-SNAPSHOT-Paper-0.10.0-SNAPSHOT+build-2026-08-05T20-18-21.959811801Z+git-d23c6a57.jar`;
- JAR SHA-256: `2b2f029d6eb3b526e46d5dcbda6a32f02da4f93b363ff68d602a48599ac81487`;
- generated `plugin.yml` named `PieCloak` and declared `api-version: '1.21.11'`;
- generated `build-properties/platform.yml` recorded full Git SHA `d23c6a577ead79fb4d70b230d1344a91095fb97b`, Minecraft `1.21.11`, Paper bundle `1.21.11-R0.1-SNAPSHOT`, and Java `21`;
- artifact `PieCloak-d23c6a577ead79fb4d70b230d1344a91095fb97b` uploaded successfully as artifact ID `8945509366`.

Static analysis run `31043407187` also checked out exact commit `d23c6a577ead79fb4d70b230d1344a91095fb97b`; its PMD, Semgrep, and Trivy jobs all concluded successfully.

- Semgrep scanned 163 tracked files with 217 applicable rules and reported two findings. Comparison with the starting-head SARIF showed the same two pre-existing unsafe-reflection findings at `RaycastedAntiESP.java:71` and `IDtoBukkitBlockData.java:61`; package 01 introduced neither finding.
- Trivy ran vulnerability, misconfiguration, and secret scanners. It found zero language-specific dependency files and zero configuration files in its supported scan scope; its SARIF artifact was 475 bytes and the job succeeded.
- PMD remained report-only and retained six pre-existing findings: four accessibility-alteration findings plus one non-final static assignment and one overridable-constructor-call finding.
- The inherited LeafPile sources emitted compiler warnings, including unchecked-operation warnings; they did not fail compilation.

The exact final head had no review threads or submitted reviews, and its CodeRabbit combined status was `success`.

## Hostile review

The entire package diff from `33b615d5ef3a937dafdf461c48e9626ee7d342fc` through `d23c6a577ead79fb4d70b230d1344a91095fb97b` was reviewed after implementation.

Confirmed package defects found and fixed during that review:

1. The original baseline task was not configuration-cache safe. It was changed to configuration-time exact checks, and the final build stored configuration cache successfully.
2. Pull-request workflows were validating a synthetic merge ref and embedding that merge SHA in artifacts. Both workflows now check out the exact PR head; the final JAR embeds `d23c6a577ead79fb4d70b230d1344a91095fb97b`.

No packet writes or runtime visibility-state mutations are part of this diff, so duplicate spawn/destroy/replay packets, staged retry state, disconnect/world-change cleanup, scheduler rejection, entity-ID reuse, and shutdown reconciliation are outside this package's changed paths. Those risks remain routed to packages 02–05 rather than being expanded here.

The build does not log credentials or production data. Artifact metadata contains only intended build identity and platform values.

## Remaining risks and manual tests

The following remain unverified and are not claimed as passed:

- launching the final JAR on a live Paper `1.21.11` server;
- launching on the production Leaf/Paper-compatible stack;
- Java-client gameplay and packet behavior;
- Bedrock behavior through Geyser/Floodgate;
- injected packet-write or network failures;
- production deployment, reload, disable/re-enable, and shutdown behavior;
- any future stable Paper `26.2` build.

The exact-head CI proves compilation, repository tests, shaded packaging, generated metadata, and configured scanners for the selected 1.21.11 boundary. It does not substitute for the later runtime packages or final live-server validation.

## Main coordination update

This report, `ai-agents/WORKSPACE-STATE.md`, `ai-agents/reports/agent-handoffs/CURRENT.md`, and `ai-agents/reports/agent-handoffs/INDEX.md` are published together as one coordination-only commit on `main`. No product source, tests, workflow, build, plugin metadata, or ordinary product documentation is included in that main commit.

## Next route

- State: `READY_FOR_AGENT`
- Current package: `02-block-transition-reliability`
- Active PR: `#11`
- Active branch: `agent/sync-upstream-clean-history`
- Recorded implementation head: `d23c6a577ead79fb4d70b230d1344a91095fb97b`
- Package after 02, if package 02 completes: `03-entity-transition-reconciliation`

The next worker must execute package 02 only and must not merge or close PR #11.
