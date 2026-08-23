# PieCloak

[![Codacy Badge](https://app.codacy.com/project/badge/Grade/5aa184993c2247ca9df86b95e6b6361f)](https://app.codacy.com/gh/wsg138/PieCloak/dashboard)

PieCloak is Enthusia SMP's anti-ESP/base-finding layer and a public modified fork of [RaycastedAntiESP](https://github.com/Cubicake/RaycastedAntiESP). It is designed to hide selected **base clues** from clients when those clues should not actually be visible, reducing information available to pie-chart/entity/block-entity based base-finding tools without changing the real server-side entities or blocks.

It keeps the upstream packet, async-engine, chunk-processing, compatibility, and performance work while applying anti-ESP handling only to an explicit allowlist of clues used on Enthusia. Anything outside that allowlist is sent normally.

Public source: https://github.com/wsg138/PieCloak

## What players should know

PieCloak is primarily a **visibility/packet system**, not a gameplay-removal system.

- It does **not** cancel entity spawns.
- It does **not** stop mob AI, ticking, farms, breeding, villagers, or trades.
- It does **not** hide players from one another.
- A hidden clue still exists normally on the server; PieCloak controls whether the remote client receives/sees that clue while it is occluded.
- Once the clue becomes legitimately visible under the configured visibility rules, it is sent/shown again.

This means a base can still function normally while selected entities and block entities inside it are harder to locate from outside using client-side debugging/ESP information.

## Current Enthusia visibility rules

For the selected entity and block-entity clues listed below, the current SMP configuration uses three distance bands:

1. **Within roughly 24 blocks:** the clue is always shown. PieCloak is not intended to hide something from a player who is already very close to it.
2. **Between roughly 24 and 48 blocks:** PieCloak raycasts from the player toward the clue. Once the ray crosses three occluding block samples, the clue is treated as hidden.
3. **Beyond roughly 48 blocks:** the clue is outside the configured raycast visibility radius and remains hidden from the managed view until it becomes eligible to be shown again.

The raycast checks actual occluding blocks along the path. In ordinary base construction, putting a protected clue several solid blocks behind terrain/walls is therefore substantially more effective than leaving it near an exposed surface.

### Practical base-hiding guidance

For players trying to benefit from PieCloak:

- Put the base and its revealing entities/block entities **inside terrain or behind multiple solid blocks**, not directly against an exposed exterior wall.
- Do not assume PieCloak makes a base invisible at close range. The current always-show radius is about **24 blocks**.
- The most relevant clues are the entities and block entities in the allowlist below. Ordinary blocks and non-allowlisted entities are not magically hidden by PieCloak.
- PieCloak reduces specific client-side information leaks; it does **not** protect against normal exploration, exposed builds, player trails, maps, coordinates shared by players, or other legitimate ways of finding a base.

## Entity clues protected on Enthusia

The current production allowlist includes:

- villagers
- copper golems
- armadillos
- wolves
- cats
- ocelots
- allays
- bees
- iron golems
- snow golems
- item frames and glow item frames
- armor stands
- paintings

Players themselves are explicitly excluded from PieCloak's hiding logic.

Projectiles, major bosses, display entities, interaction entities, and several other transient/special entity types are also excluded from managed entity hiding.

## Block-entity clues protected on Enthusia

Individually configured block entities include:

- campfires and soul campfires
- decorated pots
- bells
- jukeboxes
- conduits
- beacons
- moving pistons

The server also protects whole block-entity groups, including:

- all shulker boxes
- signs
- hanging signs
- banners and wall banners
- beds
- heads and skulls

Those groups are expanded from the server material registry where possible so variants such as colors or wood types do not need to be listed one by one.

## Block/chunk behavior

PieCloak can rewrite selected block-entity information in chunk packets before the client receives it. The current server configuration tracks only the selected block-entity clues rather than processing every ordinary block in the world. Full chunk-section hiding is disabled.

This distinction is important: PieCloak is not an X-ray ore hider and is not intended to obfuscate arbitrary base blocks. Its purpose on Enthusia is to suppress selected entity/block-entity clues that can otherwise reveal hidden bases.

## Behavior and implementation notes

- Does not cancel Bukkit spawn events or change server-side spawning, AI, ticking, farms, breeding, villagers, or trades.
- Does not hide players. Player checks are disabled and player entities bypass managed anti-ESP views.
- Tracks and hides only entity types listed under `target-filter.entities`.
- Tracks and rewrites only block entities selected by `target-filter.block-entities` and `target-filter.block-entity-groups`.
- Keeps upstream occlusion processing, async visibility checks, relationship replay, passenger handling, chunk parsing, Folia support, and PacketEvents compatibility.
- Uses an asynchronous visibility engine in the current production configuration.
- Saves target-filter config changes through the admin commands, but applies them after a restart so already-tracked entities cannot retain stale visibility state.

PacketEvents is required.

## Current production values

| Setting | Enthusia SMP |
| --- | --- |
| Target filter | Allowlist |
| Player hiding | Disabled |
| Managed entity hiding | Enabled |
| Managed block-entity hiding | Enabled |
| Always-show radius | 24 blocks |
| Raycast radius | 48 blocks |
| Occluding count | 3 |
| Entity visibility recheck | 10 ticks |
| Block-entity visibility recheck | 20 ticks |
| Full chunk-section hiding | Disabled |
| Engine | Async |
| Packet/block processor | PacketEvents |

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

This fork's documented upstream baseline is commit `853fa1531acbbb1458f776bbe2dc637fd0d40b7c` from August 5, 2026.

## License and credits

PieCloak and RaycastedAntiESP are licensed under the GNU Affero General Public License v3.0 only. Original copyright and license notices are preserved.

- Cubicake, creator and maintainer of RaycastedAntiESP
- RaycastedAntiESP contributors
- P2wn and PieCloak contributors
- PacketEvents, StrokkCommands, Paper, Spigot, and Bukkit contributors
