# PieCloak

[![Codacy Badge](https://app.codacy.com/project/badge/Grade/5aa184993c2247ca9df86b95e6b6361f)](https://app.codacy.com/gh/wsg138/PieCloak/dashboard)

PieCloak is a public modified fork of [RaycastedAntiESP](https://github.com/Cubicake/RaycastedAntiESP) for the server's current Paper/Leaf-compatible Minecraft 1.21.11 stack.

It keeps the upstream packet, async-engine, chunk-processing, compatibility, and performance improvements while applying anti-ESP handling only to an explicit allowlist of entity and block-entity clues used for pie-chart or base-finding. Non-allowlisted targets are sent normally.

Public source: https://github.com/wsg138/PieCloak

## Behavior

- Does not cancel Bukkit spawn events or change server-side spawning, AI, ticking, farms, breeding, villagers, or trades.
- Does not hide players. Player checks are disabled and player entities bypass managed anti-ESP views.
- Tracks and hides only entity types listed under `target-filter.entities`.
- Tracks and rewrites only block entities selected by `target-filter.block-entities` and `target-filter.block-entity-groups`.
- Keeps upstream occlusion processing, async visibility checks, relationship replay, passenger handling, chunk parsing, Folia support, and PacketEvents compatibility.
- Saves target-filter config changes through the admin commands, but applies them after a restart so already-tracked entities cannot retain stale visibility state.

PacketEvents is required.

## Commands

Administrative commands require `raycastedantiesp.command`:

- `/raesp reload`
- `/raesp config-values`
- `/raesp set <key> <value>`
- `/raesp add <key> <value>`
- `/raesp remove <key> <value>`
- `/raesp stats`
- `/raesp source`
- `/raesp test ...`

Public source and attribution commands:

- `/piecloak source`
- `/raycastedantiespCredits`

## Build

The checked-in production baseline is Minecraft 1.21.11, Paper development bundle `1.21.11-R0.1-SNAPSHOT`, and Java 21. Those values are centralized in `gradle.properties`; `verifyCurrentPlatformBaseline` prevents an accidental target change.

Initialize the submodules, generate the LeafPile sources, and run the same clean build used by CI:

```bash
git submodule update --init --recursive
cd leafpile/templates
javac --release 21 GenerateSources.java Preprocessor.java
java GenerateSources . ..
cd ../..
./gradlew clean verifyCurrentPlatformBaseline :platform-paper:compileJava test :platform-paper:build :platform-paper:buildSnapshot --no-daemon
```

The shaded plugin JAR is written to `platform-paper/build/libs` with a `RaycastedAntiESP-` filename. Its generated `plugin.yml` and `build-properties/platform.yml` record the Minecraft, Paper bundle, Java, Git, and build-time baseline used for that artifact.

The `runServer` and `runPaper` tasks use Minecraft 1.21.11 on Java 21. They are development conveniences and do not constitute Leaf, Geyser, or production validation.

## Testing and upstream maintenance

See [TESTING.md](TESTING.md) for the server test checklist and [MAINTAINING.md](MAINTAINING.md) before integrating later upstream changes.

This update is based on upstream commit `853fa1531acbbb1458f776bbe2dc637fd0d40b7c` from August 5, 2026.

## License and credits

PieCloak and RaycastedAntiESP are licensed under the GNU Affero General Public License v3.0 only. Original copyright and license notices are preserved.

- Cubicake, creator and maintainer of RaycastedAntiESP
- RaycastedAntiESP contributors
- P2wn and PieCloak contributors
- PacketEvents, StrokkCommands, Paper, Spigot, and Bukkit contributors
