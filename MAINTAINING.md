# Maintaining PieCloak

PieCloak is a modified fork of [RaycastedAntiESP](https://github.com/Cubicake/RaycastedAntiESP). Upstream should be treated as a reference for future fixes, not as a branch to merge blindly.

## Safe Upstream Review

1. Fetch the upstream repository.

   ```powershell
   git fetch upstream
   ```

2. Compare upstream against PieCloak without merging.

   ```powershell
   git log --oneline --left-right origin/main...upstream/main
   git diff --stat origin/main...upstream/main
   ```

3. Review upstream commits manually. Only port changes that are useful for PieCloak, such as:

   - bug fixes
   - PacketEvents compatibility updates
   - Paper/Leaf compatibility updates
   - performance improvements
   - low-risk maintenance fixes

4. Do not overwrite PieCloak behavior while porting changes:

   - Do not remove the allowlist target filter.
   - Do not remove PieCloak config sections.
   - Do not re-enable player hiding unless that is an intentional, tested change.
   - Do not change packet hiding, chunk data rewriting, block entity filtering, or reveal timing without retesting pie chart leaks.
   - Do not replace PieCloak defaults with upstream defaults without reviewing the server impact.

5. Port selected changes as normal commits on top of PieCloak `main`. Prefer small commits with clear messages.

## Upstream integration baseline

The November 26 integration reconciles upstream `373343e96706885c057bb00613dd113d68ec9f33` against the previous documented content baseline `853fa1531acbbb1458f776bbe2dc637fd0d40b7c`. The integration retains both Git histories. The typed schema 2.1, block selectors, logging, registry snapshots, library updates and tile client visibility are integrated. PieCloak deliberately preserves its exact voxel DDA geometry rather than upstream's approximate ray-stepping algorithm; the section lookup optimization is adapted to DDA. The fork retains its stronger staged packet repairs and lifecycle fences. The original 2.0 configuration is backed up as `config.yml.pre-2.1.bak` during migration, and numeric legacy block-state overrides are retained alongside selectors. Target filter and block classification changes remain restart-only.

## Current Platform Baseline

The default build target is Minecraft 1.21.11, Java 21, Paper bundle `1.21.11-R0.1-SNAPSHOT`, and PacketEvents 2.12.0. The explicit latest-version build target is Minecraft 26.3, Java 25, Paper bundle `26.3.build.262-stable`, and PacketEvents 2.14.0. Native runtime acceptance used Paper 26.3 build 263. The default properties and `verifyCurrentPlatformBaseline` guard protect against unsupported combinations. WorldGuard compile dependencies are selected per profile (7.0.16 and 7.0.21); runtime qualification is separate.

Review legacy numeric block-state overrides when migrating Minecraft versions; named selectors avoid dependence on numeric registry IDs. Future platform updates must deliberately update the build guard and pass the compile and runtime matrices.

## Required Validation

After any ported upstream update or platform-boundary change, initialize submodules, generate LeafPile sources, and run:

```bash
git submodule update --init --recursive
cd leafpile/templates
javac --release 21 GenerateSources.java Preprocessor.java
java GenerateSources . ..
cd ../..
./gradlew clean verifyCurrentPlatformBaseline :platform-paper:compileJava test :platform-paper:build :platform-paper:buildSnapshot --no-daemon
```

For Minecraft 26.3, use JDK 25, generate LeafPile sources with `javac --release 25`, and run:

```bash
./gradlew clean verifyCurrentPlatformBaseline :platform-paper:compileJava test :platform-paper:build :platform-paper:buildSnapshot --no-daemon -PminecraftVersion=26.3 -PjavaVersion=25 -PpaperDevBundleVersion=26.3.build.262-stable -PpacketEventsVersion=2.14.0
```

Inspect the shaded JAR after the build:

- `plugin.yml` names `PieCloak` and declares the selected API version (`1.21.11` or `26.3`)
- `build-properties/platform.yml` records the selected Minecraft version, Paper bundle, Java release, and exact Git revision
- the JAR contains the expected shaded project modules and plugin resources

Then test on a server before production use:

- target entities remain packet-hidden only when allowlisted
- non-allowlisted entities and block entities behave normally
- players are not hidden
- farms, trading halls, bees, villagers, allays, golems, and block entities continue working server-side
- target entities do not leak through spawn/metadata/movement packets
- target block entities do not leak through chunk data, block entity data, block change, or multi-block change packets
- login, teleport, dimension change, chunk reload, render-distance entry, and elytra movement do not create one-tick leaks
- Java and Bedrock players through the production Geyser/Floodgate path receive consistent visibility behavior

## Attribution And License

Keep the original AGPL license and attribution intact:

- preserve `LICENSE`
- preserve original source notices
- keep README links to the original RaycastedAntiESP project
- keep PieCloak public source available at https://github.com/wsg138/PieCloak
