# Package 07 handoff — controller ownership and same-JVM re-enable

- Date/time: 2026-08-06 03:40 UTC
- Agent role: sequential remediation implementation worker
- Repository: `wsg138/PieCloak`
- Coordination branch: `main`
- Coordination starting SHA: `7e5f36d27aad4a489f1ca004906a626f68aacd15`
- Pull request: `#11`
- Implementation branch: `agent/sync-upstream-clean-history`
- Starting implementation head: `72489966f5c45261e61538c8725c955750fd188b`
- Ending implementation head: `a6056313378f3fbdbbbb3698a67ca675533ad351`
- Package: `07-controller-ownership-reenable`
- State: `READY_FOR_AGENT`

## Live reconciliation

At package start, `main` was `7e5f36d27aad4a489f1ca004906a626f68aacd15`, which selected package 07 after owner review superseded package 06. PR #11 was open, non-draft, unmerged, based on `main`, and its implementation branch was still at the recorded starting head `72489966f5c45261e61538c8725c955750fd188b`.

Immediately before this handoff, PR #11 remained open, non-draft, unmerged, and mergeable at exact head `a6056313378f3fbdbbbb3698a67ca675533ad351`. There were zero review threads and zero submitted reviews. `main` remained at `7e5f36d27aad4a489f1ca004906a626f68aacd15`; no newer worker changed routing.

The other open repository pull requests were Dependabot PRs #4, #5, #6, #7, #8, and #9. They were not modified because their full compatibility and cleanup review is explicitly package 09 scope.

## Scope completed

Package 07 required clean disable/re-enable in one JVM/classloader, ownership-aware release of both entity-controller singleton layers, constructor rollback, idempotent listener cleanup, stale-owner protection, independent cleanup attempts, and explicit fencing when cleanup cannot be proven safe.

Completed behavior:

- `PacketEntityViewController.SELF` and `PacketEventsEntityViewController.SELF` are treated as one lifecycle ownership pair at the Paper adapter boundary.
- Construction is serialized on the same class lock used by the core singleton claim.
- Failed construction restores both singleton slots independently and attempts listener rollback.
- Normal close attempts listener unregistration and both singleton releases even when an earlier action throws.
- Each singleton is cleared only when the closing controller is still that slot's exact owner.
- Repeated close is harmless, and an old controller cannot clear a replacement controller.
- One successful listener lifecycle registers once and unregisters at most once.
- Registration failure attempts candidate unregistration; failure of that cleanup marks startup unsafe.
- Constructor rollback or singleton restoration failure marks `teardownSafe` false before the controller is added to `LifecycleScope`, preserving the existing same-JVM re-enable fence.
- Successful close leaves the ownership pair empty so a new controller can be created in the same classloader.

No package-08 respawn or visibility-state behavior was changed.

## Implementation commits

- `16b9d4ffe865ac4204f06e5e02b10bead4b1db2b` — introduced ownership release and lifecycle regression coverage.
- `c7c1191cccc3e67a37900557ff9d8064ef722809` — kept controller construction behind a private production factory after the first compile review.
- `5b85160f87362009267aadfd7eb42902bd8af0af` — rejected partial singleton ownership explicitly.
- `a5233005312f1f75e55a5ea4d01d08e89cc6c183` — completed independent ownership cleanup and listener-registration lifecycle coverage; added PacketEvents to the test runtime.
- `5182da75da8495780690758afd2eafcacfb823b9` — hostile-review fix: fenced re-enable when constructor-time cleanup cannot be proven safe.
- `a6056313378f3fbdbbbb3698a67ca675533ad351` — documented intentional identity comparisons for PMD without changing behavior.

## Design and behavior

The original failure occurred because the plugin cleared its own `packetEventsController` reference while the inherited core and PacketEvents static singleton fields still retained the first controller. A second normal enable in the same classloader then failed while constructing another singleton owner. Constructor failures could also leave only one layer claimed.

The remediation isolates fixed-name singleton-slot access in `EntityControllerOwnership` at the Paper/PacketEvents adapter boundary. `ControllerOwnership` commits ownership only after construction, listener registration, and PacketEvents publication all produce one identical owner. On failure, it attempts external rollback and both slot restorations independently, retaining the original exception and suppressing cleanup failures.

`ListenerRegistration` is the production registration lease used by the Paper controller and by focused tests. It provides one successful register/one attempted unregister per lifecycle and an idempotent close. A failed unregister is surfaced rather than hidden. The lifecycle safety callback marks startup unsafe when partial listener registration cannot be cleaned up, so the existing server-restart requirement remains explicit.

Identity comparison is deliberate: ownership is based on the exact controller instance, not object equality. A narrow PMD suppression records that invariant.

The reflective binding exists because the inherited singleton fields have no release API. It is confined to the Paper adapter boundary so a future upstream or stable Paper 26.2-or-newer upgrade fails explicitly at that boundary instead of silently retaining stale ownership.

## Files and architecture changed

PR-branch changes for package 07:

- `platform-paper/src/main/java/games/cubi/raycastedantiesp/paper/RaycastedAntiESP.java`
- `platform-paper/src/main/java/games/cubi/raycastedantiesp/paper/packets/ControllerOwnership.java`
- `platform-paper/src/main/java/games/cubi/raycastedantiesp/paper/packets/EntityControllerOwnership.java`
- `platform-paper/src/main/java/games/cubi/raycastedantiesp/paper/packets/ListenerRegistration.java`
- `platform-paper/src/main/java/games/cubi/raycastedantiesp/paper/packets/PaperPacketEventsEntityViewController.java`
- `platform-paper/src/test/java/games/cubi/raycastedantiesp/paper/packets/ControllerOwnershipTest.java`
- `platform-paper/src/test/java/games/cubi/raycastedantiesp/paper/packets/ListenerRegistrationTest.java`
- `platform-paper/build.gradle.kts`

The build-file change adds the existing PacketEvents 2.12.0 dependency to test runtime only so the production binding/factory test can load the real controller classes. It does not shade or deploy PacketEvents.

## Tests and validation

Exact final head: `a6056313378f3fbdbbbb3698a67ca675533ad351`.

### Exact-head build

GitHub Actions Build run `31068970441`, job `92512714076`, checked out the exact head and succeeded using Temurin Java `21.0.11+10`.

Executed command:

```bash
./gradlew clean verifyCurrentPlatformBaseline :platform-paper:compileJava test :platform-paper:build :platform-paper:buildSnapshot --no-daemon
```

Result: `BUILD SUCCESSFUL`; 34 actionable tasks, 29 executed and 5 up-to-date. Generated LeafPile sources, baseline verification, all repository test tasks, shaded snapshot build, JAR inspection, and artifact upload succeeded. The workflow does not print a total test count, so no numerical test total is claimed.

Focused deterministic tests cover:

- construct → close → reconstruct → close in one classloader;
- constructor and secondary-publication rollback;
- later valid construction after failed construction;
- repeated close;
- stale owner versus replacement owner;
- listener cleanup failure with both singleton releases still attempted;
- one singleton release failure while the other release is still attempted;
- one restoration failure while the other restoration is still attempted;
- partial ownership rejection;
- exactly one listener registration and one unregistration per successful lifecycle;
- failed registration candidate cleanup;
- unsafe-cleanup fencing before lifecycle publication.

The tests are deterministic and contain no concurrency/race loop, so repeated race runs were not applicable. A local checkout was attempted for an independent rerun but the execution container could not resolve `github.com`; exact-head Actions is the direct execution evidence.

### Exact-head static analysis

Static analysis run `31068970437` succeeded:

- PMD job `92512714107`: success;
- Semgrep job `92512714138`: success;
- Trivy job `92512714165`: success.

The uploaded PMD XML was inspected directly. It contains seven pre-existing platform findings in `FancyCompatibility`, `HackyEntityIDGuard`, `IDtoBukkitBlockData`, and `PacketEventsPaperBlockInfoResolver`; it contains no finding in a package-07 source or test file.

### Exact-head artifact

Build artifact: `PieCloak-a6056313378f3fbdbbbb3698a67ca675533ad351`, artifact ID `8954925564`.

Shaded JAR:

`RaycastedAntiESP-0.7.0-SNAPSHOT-Paper-0.10.0-SNAPSHOT+build-2026-08-06T03-38-28.269563848Z+git-a6056313.jar`

JAR SHA-256:

`6db5d21896eaa70902d29101cec4c55f396f15992af62715dd988a1e8e1890e8`

Direct artifact inspection confirmed:

- `plugin.yml` name `PieCloak`;
- API version `1.21.11`;
- required dependency `packetevents`;
- embedded long Git SHA `a6056313378f3fbdbbbb3698a67ca675533ad351`;
- Paper dev bundle `1.21.11-R0.1-SNAPSHOT`;
- Java version `21`;
- all package-07 production classes are present in the shaded JAR.

### Superseded failed validation

Intermediate failures were retained as evidence and fixed rather than hidden:

- the first implementation head failed compilation because a helper attempted to call a private controller constructor; construction was moved behind the class's public static factory;
- a later head failed one production-binding test because PacketEvents was compile-only and absent from the test runtime; the matching existing version was added as test-runtime-only;
- PMD initially reported intentional ownership identity comparisons; a narrow documented suppression removed only those package-07 findings.

## Hostile review

The complete package diff was reviewed after implementation as if written by another developer.

Reviewed failure points included core claim before PacketEvents publication, listener registration failure, candidate unregistration failure, external rollback failure, one singleton restore/release failing before the other, repeated lifecycle close, stale close after replacement, startup failure before `activeLifecycle`, normal disable, and re-enable after clean shutdown.

Confirmed defects found and fixed during hostile review:

1. Constructor-time unregistration failure could occur before the controller entered `LifecycleScope`, allowing reset without setting the safety fence. The constructor and ownership rollback paths now receive an unsafe-cleanup callback that marks `teardownSafe` false.
2. Cleanup originally needed stronger proof that both singleton actions were attempted when either failed. Cleanup and rollback now aggregate independent attempts, with regression tests.
3. Listener exact-once behavior was initially implicit. It now uses a production `ListenerRegistration` lease with focused lifecycle tests.
4. The production consistency check initially did not reject one-sided ownership strongly enough. It now rejects any non-identical slot values, including one null/one non-null.

No package blocker remained after these fixes. No sensitive controller data is logged. The ownership state is bounded to two static slots and one registration lease per active controller.

## Remaining risks and manual tests

Not directly run and therefore unverified:

- real Leaf 1.21.11 startup → disable → enable → disable in one JVM/classloader;
- actual PacketEvents partial-registration/send-then-throw behavior under injected failure;
- listener-unregister failure against a real PacketEvents event manager;
- Java-client and Geyser/Floodgate behavior during live re-enable;
- shutdown while live packet work is active;
- interaction with optional FancyNpcs/FancyHolograms combinations;
- long-running heap, listener, and registry stability across repeated live lifecycle cycles.

The same-world respawn visibility blocker remains confirmed and intentionally untouched. PR #11 remains `NOT READY` until package 08 completes and package 09 issues a fresh final verdict.

The bypass permission refresh limitation from the owner remediation plan remains documented package-09 review scope and was not expanded into package 07.

## Main coordination update

This coordination-only commit updates:

- this timestamped handoff report;
- `ai-agents/WORKSPACE-STATE.md`;
- `ai-agents/reports/agent-handoffs/CURRENT.md`;
- `ai-agents/reports/agent-handoffs/INDEX.md`.

No product source, tests, build files, workflow files, metadata, or product documentation are being committed to `main`.

## Next route

Package 07 is complete at exact PR head `a6056313378f3fbdbbbb3698a67ca675533ad351`. Select `08-respawn-visibility-state-invalidation` with state `READY_FOR_AGENT`.

Package 08 must make every respawn, including same-world and bypass-viewer respawns, establish a new client-state epoch and invalidate all pre-respawn entity, block, retry, replay, relationship, reconciliation, and after-send work. Do not begin package 09 until package 08 is complete.
