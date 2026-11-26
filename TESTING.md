# PieCloak / RaycastedAntiESP Testing Checklist

Use a normal client plus a pie-chart/base-finding client or packet capture where possible.

## Build Baseline

- Initialize all Git submodules recursively.
- Generate LeafPile sources with Java 21 before the Gradle build.
- Run `./gradlew clean verifyCurrentPlatformBaseline :platform-paper:compileJava test :platform-paper:build :platform-paper:buildSnapshot --no-daemon`.
- Confirm the shaded JAR is named `RaycastedAntiESP-*.jar`, not `Incorrectly-Compiled-Without-ShadowJar`.
- Confirm generated `plugin.yml` names `PieCloak` and declares API version `1.21.11`.
- Confirm `build-properties/platform.yml` records Minecraft `1.21.11`, Paper bundle `1.21.11-R0.1-SNAPSHOT`, Java `21`, and the exact Git revision.
- Treat `runPaper`/`runServer` as a local Paper 1.21.11 smoke-test convenience, not proof of Leaf, Geyser, or production compatibility.

## Server Behavior

- Normal mobs still spawn and tick.
- Farms still work without plugin-side spawn cancellation or AI changes.
- Villager trading halls still trade and restock.
- Iron farms still spawn golems.
- Honey farms and bees still work.
- Snow golem farms still work.
- Allays still work.
- Players are never hidden.
- Non-allowlisted entities and block entities behave normally.
- Java and Bedrock players through Geyser/Floodgate observe the same managed visibility outcomes.

## Skip-region exit and raycast progress regressions

- With player checks disabled and `piecloak-skip: allow` inside a region, repeatedly cross its boundary with two players. Both player bodies and nametags must remain visible outside the region.
- Repeat with entity checks disabled; tracked entities must not be stranded hidden on exemption exit. With checks enabled, normal occlusion must still apply outside the region.
- Outside the exempt region, approach protected shulkers and villagers, including targets whose ray endpoints lie on integer coordinates in mixed positive/negative directions. Their visibility must keep updating; diagnostic completed-tick counts must continue increasing.
- Repeat after teleport, relog, chunk reload, and on the supported Java and Geyser/Floodgate client paths. These are manual acceptance checks, not implied by passing unit tests.
- When replacing an affected build, perform a full server restart: a worker already stuck in the old traversal cannot be repaired by a configuration reload.

## Target Filter

- `villager` and `minecraft:villager` config entries both resolve to the same target.
- Missing current-version entries, such as version-dependent entities, log a warning and are skipped.
- `/raesp stats` shows resolved target counts and skipped invalid entries.
- `/raesp debugplayer <player>` shows managed target counts for the player.
- `/raesp benchmark <radius> <samples>` reports raycast timing without changing gameplay.

## Leak Checks

- Target entities do not appear in pie chart before their spawn packet should be revealed.
- Target block entities do not leak from `CHUNK_DATA`.
- Target block entities do not leak through standalone `BLOCK_ENTITY_DATA`.
- Target block entities do not leak through `BLOCK_CHANGE`.
- Target block entities do not leak through `MULTI_BLOCK_CHANGE`.

## One-Tick Cases

- Walk into render distance of a base.
- Teleport near a base.
- Log in near a base.
- Change dimensions near a base.
- Reload chunks near a base.
- Fly through chunks at high elytra speed.

## Entity Desync Regression

- Join and relog beside an allowlisted iron golem inside `always-show-radius`; it must be visible immediately.
- Grant `raycastedantiesp.bypass`, relog, and verify all managed entities remain visible and continue moving.
- Move a villager in a minecart across visible and hidden boundaries; both must hide and show together.
- Repeat with ordinary boats, chest boats, bamboo rafts and chest rafts. Move the mounted villager from beyond the raycast radius into the always-visible radius; it must reappear without relogging or breaking its vehicle.
- Repeat boat movement, teleport, empty passenger list, boat destruction and reconnect for Java and Geyser/Floodgate viewers. Verify hidden passengers remain filtered and visible passengers are mounted after SHOW replay.
- Dismount the villager with an empty `SET_PASSENGERS`; the minecart and villager must resume independent visibility.
- Destroy the minecart while mounted, then repeat by removing the passenger first; neither case may leave stale visibility.
- Repeat at least 100 hide/show cycles and confirm replay state and heap usage remain bounded.
- Force one SHOW replay failure in a development build and verify other transitions still process and the failed SHOW retries.

## Block Transition Repair Regression

- Inject a write failure for a fake-block HIDE packet and verify later packed transitions still run and the failed tile retries.
- Inject a failure before the real-block SHOW packet and verify the repair restarts from the block stage.
- Inject a failure only for SHOW block-entity data and verify retry sends only the missing data rather than duplicating the real block.
- Disable tile checks while several tiles are hidden, fail one repair write, and verify every other tile is repaired while the failed tile remains queued.
- Toggle checks enabled → disabled → enabled while a repair is pending and verify the old generation is discarded.
- Saturate one viewer with more than 256 distinct failed block repairs and verify queued staged repairs are preserved, newest failures are reported, and each repair stops after eight failed attempts.
- Change worlds or disconnect with repairs pending and verify no old-world repair is sent afterward.
- Send standalone managed block-entity data without cached tile state and verify it is cancelled and replaced with the hidden block.
- Send virtual/non-managed block-entity data and verify it still reaches Java and Bedrock clients.
- Inject an unexpected `MAP_CHUNK_BULK` packet and verify it passes through without throwing while diagnostics remain bounded.
- Repeat the package-specific concurrency and retry tests at least 25 times before release.

## Entity transition failure injection

For package 03 validation, inject one outbound write failure at a time while revealing a hidden managed entity. Exercise spawn, absolute position correction, head look, each replay category (metadata, equipment, velocity, effects, and attributes), passenger state, vehicle state, leash state, and destroy.

For every stage, verify that a successful spawn or destroy is not sent a second time, later retries resume at the failed stage, and a newer opposite visibility decision cancels the stale repair. Also verify bounded terminal behavior, disconnect cleanup, respawn/world-change cleanup, entity despawn and ID reuse, minecart passenger ordering, bypass convergence, and Java plus Geyser/Floodgate clients.

These injected network-failure and live-client scenarios remain manual unless a handoff records direct server evidence.

## Fancy optional-plugin lifecycle compatibility

The supported operational path for optional Fancy integrations is a complete server restart, not a same-JVM hot reload. The following limitations were independently reproduced **without PieCloak** in disposable Paper 26.2-129 / Java 26.0.1 fixtures using FancyNpcs 2.12.1 and two connected protocol clients:

- [#30](https://github.com/wsg138/PieCloak/issues/30): a main-thread FancyNpcs hot-disable triggered Paper async command-tree `ConcurrentModificationException` errors. This also occurred when FancyNpcs was the only optional plugin installed.
- [#31](https://github.com/wsg138/PieCloak/issues/31): same-JVM disable/re-enable failed inside FancyLib translation initialization with `Could not find default language file`; FancyNpcs remained disabled.

These observations apply to the pinned versions tested and do not establish the exact upstream cause of #30 or imply that all versions fail. Do not add PieCloak workarounds for these external failures without new evidence. For release acceptance:

- Start a clean server with the supported exact Paper/Java/PacketEvents combination, and repeat with the selected FancyNpcs and FancyHolograms versions, recording all version/build identifiers.
- Verify startup backfill, late spawn/show, removal, numeric ID reuse and owner-specific bypass cleanup, including idle weak-owner cleanup.
- Repeat after full process restart; verify unaffected entities remain unaffected and normal visibility resumes after removal.
- Test optional integrations absent, separately present, and jointly present. Distinguish natural numeric ID reuse from synthetic event replay in the report.
- Treat hot-disable/re-enable as a separate diagnostic, not evidence of supported operation; if it still fails without PieCloak, record that external limitation. Any new failure with PieCloak alone remains a release blocker.

### November 26 verified maintenance candidate

- Merged production source: [6566f9e](https://github.com/wsg138/PieCloak/commit/6566f9e74b8e4d30cb8ff2e53c852abbfec56054), [PR #35](https://github.com/wsg138/PieCloak/pull/35).
- [Published JAR and checksum](https://github.com/wsg138/PieCloak/releases/tag/piecloak-1.21.11-2026-11-26-6566f9e); JAR SHA-256: `672facfefb09475ccea3f1491c6669997f9cf80b8879cf3348856f4ea7c1f4c3`.
- [Exact-release CI evidence](https://github.com/wsg138/PieCloak/actions/runs/45772052384): full Gradle suite and four successful isolated server instances (Paper 1.21.11 build 132, FancyNpcs 2.12.1, PacketEvents 2.12.0, Java 25; with/without FancyHolograms 2.12.0; fresh boot and persisted restart). Two independent clients verified spawn, destroy, movement, interaction and range reentry. Synthetic late callback and zero-viewer deletion tests passed.
- The [standalone runtime fixture](https://github.com/wsg138/PieCloak/tree/e59026ae5dca26c7cf64733d1c3e93f03b2c19d1/qa/fancy) is pinned by immutable commit and is **not part of the production source tree or release JAR**. Its separate test-tooling Codacy review remains open in [#33](https://github.com/wsg138/PieCloak/issues/33). The hosted acceptance job uses read-only permissions and runs the fixture in an isolated loopback server. No repository-wide quality checks are disabled. This is an explicit scope separation, not a claim that all fixture lint findings were resolved.
- Full upstream migration, #21–23, hot disable/re-enable (#30–31), natural numeric ID reuse, and additional platform validation remain open.
