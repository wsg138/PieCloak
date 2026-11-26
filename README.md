<div align="center">

# PieCloak

**Selective anti-ESP and base-clue protection for Minecraft servers.**

[![Download PieCloak](https://img.shields.io/badge/Download-PieCloak_JAR-238636?style=for-the-badge&logo=github)](https://github.com/wsg138/PieCloak/releases)
[![Build](https://github.com/wsg138/PieCloak/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/wsg138/PieCloak/actions/workflows/build.yml)
[![Codacy Badge](https://app.codacy.com/project/badge/Grade/5aa184993c2247ca9df86b95e6b6361f)](https://app.codacy.com/gh/wsg138/PieCloak/dashboard)
[![License: AGPL v3](https://img.shields.io/badge/License-AGPL--3.0-blue.svg)](LICENSE)

[Download & installation](#download--installation) · [Compatibility](#compatibility) · [Configuration](#current-production-values) · [Commands](#commands) · [Build from source](#build)

</div>

PieCloak is Enthusia SMP's anti-ESP/base-finding layer and a public modified fork of [RaycastedAntiESP](https://github.com/Cubicake/RaycastedAntiESP). It is designed to hide selected **base clues** from clients when those clues should not actually be visible, reducing information available to pie-chart/entity/block-entity based base-finding tools without changing the real server-side entities or blocks.

It keeps the upstream packet, async-engine, chunk-processing, compatibility, and performance work while applying anti-ESP handling only to an explicit allowlist of clues used on Enthusia. Anything outside that allowlist is sent normally.

## Download & installation

| Server | Download | Requirements |
| --- | --- | --- |
| **Paper 26.3** | **[Dedicated 26.3 JAR](https://github.com/wsg138/PieCloak/releases/download/piecloak-26.3-2026-11-26-5895780/PieCloak-26.3-2026-11-26-5895780.jar)** · [Release notes & checksum](https://github.com/wsg138/PieCloak/releases/tag/piecloak-26.3-2026-11-26-5895780) | Java 25+, PacketEvents 2.14.0 |
| Paper 1.21.11 | [Java 21 JAR](https://github.com/wsg138/PieCloak/releases/download/piecloak-1.21.11-2026-11-26-upstream-373343e/PieCloak-1.21.11-2026-11-26-upstream-373343e.jar) · [Release notes & checksum](https://github.com/wsg138/PieCloak/releases/tag/piecloak-1.21.11-2026-11-26-upstream-373343e) | Java 21+, PacketEvents 2.12.0 |

Install only one PieCloak JAR. The Java 21 JAR was also directly verified on native Paper 26.3; the dedicated Java 25 build is recommended for 26.3.

The November 26 upstream integration build supports the tested Paper 1.21.11 and 26.3 configurations below. It contains RaycastedAntiESP through `373343e` plus PieCloak-specific fixes. Its exact merged JAR passed compilation, unit tests, legacy configuration migration, and the two-client Paper runtime matrix before publication. [Build and runtime evidence](https://github.com/wsg138/PieCloak/actions/runs/45778483197). Both releases are marked prerelease pending broader platform and live-production qualification. The dedicated 26.3 build was merged through [PR #37](https://github.com/wsg138/PieCloak/pull/37).

1. Install a compatible [PacketEvents](https://modrinth.com/plugin/packetevents) JAR.
2. Stop the server and back up your existing PieCloak configuration.
3. Place the PieCloak JAR in the server's `plugins/` directory, replacing any previous PieCloak JAR. Do not install RaycastedAntiESP alongside it.
4. Start the server, inspect startup logs, and review the generated configuration before changing its target allowlist.

Use a full server restart when updating PieCloak or Fancy integrations.

## Compatibility

| Component | Supported baseline / verification |
| --- | --- |
| Minecraft / Paper | **1.21.11 (build 132) and 26.3 (build 263)** — both verified with the published JAR |
| Java | Java 21 legacy build; Java 25 dedicated 26.3 build. **Java 25+ required by Paper 26.3** |
| PacketEvents | Required; 2.12.0 on 1.21.11, 2.14.0 on 26.3 |
| FancyNpcs | Optional; 2.12.1 tested for spawning, deletion, type changes, interaction, movement, visibility range and saved NPCs after restart |
| FancyHolograms | Optional; 2.12.0 installed alongside FancyNpcs during the same tests |
| WorldGuard | Optional; 7.0.21 with WorldEdit 7.4.6 tested on Paper 26.3 for target-location exemption and live flag changes |
| Leaf / Folia / Geyser | Existing integrations retained; this candidate's full platform-specific acceptance remains pending |
| Hot disable / re-enable | Not a supported FancyNpcs upgrade path; use a full process restart |

**Minecraft 26.3:** the exact published JAR (SHA-256 `6b4da6a272d287fd89f62ca1b9643a960dbcb290b146257815a26d720045c6d3`) passed native 26.3 two-client Fancy lifecycle, restart, migration, and synthetic/native cancellation tests ([runtime results](https://github.com/wsg138/PieCloak/actions/runs/45876384597)). The real [entity and block visibility test](https://github.com/wsg138/PieCloak/actions/runs/45876752065) verified hidden managed villager/shulker box, unaffected cow/chest, and legitimate reveal. Review legacy numeric block-state overrides when changing Minecraft versions; named selectors are portable. The dedicated 26.3 JAR (SHA-256 `c1761468c1695a7cc7ef18134fdc33ddac87d91efa2d513fcc3beb8a4e225038`) passed the [compile/test matrix](https://github.com/wsg138/PieCloak/actions/runs/45876151727), [Fancy lifecycle and cancellation checks](https://github.com/wsg138/PieCloak/actions/runs/45876482741), and [corrected exact-artifact visibility test](https://github.com/wsg138/PieCloak/actions/runs/45876783247). The initial combined visibility fixture failed on an invalid cross-thread probe read; the corrected standalone fixture passed on the identical artifact. ViaBackwards-translated 1.21.11 test clients disconnected with invalid movement packets, also reproduced [without PieCloak](https://github.com/wsg138/PieCloak/actions/runs/45877318207). Translated clients and other server platforms are not certified by these results.

Additional exact-release Paper 26.3 tests verified [WorldGuard target-location exemption and allow/deny transitions](https://github.com/wsg138/PieCloak/actions/runs/45880385340) and [multi-entity reconciliation burst recovery](https://github.com/wsg138/PieCloak/actions/runs/45880372781). The latter reproduces synthetic packet ordering, not the original production packet source in [#22](https://github.com/wsg138/PieCloak/issues/22).

The published FancyNpcs fix from [PR #35](https://github.com/wsg138/PieCloak/pull/35) is retained in the upstream integration. [Exact release runtime results](https://github.com/wsg138/PieCloak/actions/runs/45778483197) cover the configurations above, not every platform or optional integration.

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

The default build profile remains Minecraft 1.21.11, Paper development bundle `1.21.11-R0.1-SNAPSHOT`, and Java 21. A separate explicit Minecraft 26.3 / Java 25 profile is also supported. Those values are centralized in `gradle.properties`; `verifyCurrentPlatformBaseline` prevents an accidental target change.

Initialize the submodules, generate the LeafPile sources, and run the same clean build used by CI:

```bash
git submodule update --init --recursive
cd leafpile/templates
javac --release 21 GenerateSources.java Preprocessor.java
java GenerateSources . ..
cd ../..
./gradlew clean verifyCurrentPlatformBaseline :platform-paper:compileJava test :platform-paper:build :platform-paper:buildSnapshot --no-daemon
```

For the dedicated 26.3 build, use JDK 25 and generate LeafPile sources with `javac --release 25` instead of 21. Then run:

```bash
./gradlew clean verifyCurrentPlatformBaseline :platform-paper:compileJava test :platform-paper:build :platform-paper:buildSnapshot --no-daemon -PminecraftVersion=26.3 -PjavaVersion=25 -PpaperDevBundleVersion=26.3.build.262-stable -PpacketEventsVersion=2.14.0
```

Both profiles run in the GitHub Actions build matrix.

The shaded plugin JAR is written to `platform-paper/build/libs` with a `RaycastedAntiESP-` filename. Its generated `plugin.yml` and `build-properties/platform.yml` record the Minecraft, Paper bundle, Java, Git, and build-time baseline used for that artifact.

The `runServer` and `runPaper` tasks default to Minecraft 1.21.11 on Java 21. They are development conveniences and do not constitute Leaf, Geyser, or production validation.

## Testing and upstream maintenance

See [TESTING.md](TESTING.md) for the server test checklist and [MAINTAINING.md](MAINTAINING.md) before integrating later upstream changes.

This fork includes upstream commit [`373343e96706885c057bb00613dd113d68ec9f33`](https://github.com/Cubicake/RaycastedAntiESP/commit/373343e96706885c057bb00613dd113d68ec9f33), integrated in [PR #36](https://github.com/wsg138/PieCloak/pull/36). Schema 2.1 automatically backs up the original 2.0 file as `config.yml.pre-2.1.bak`; retain that backup.

## License and credits

PieCloak and RaycastedAntiESP are licensed under the GNU Affero General Public License v3.0 only. Original copyright and license notices are preserved.

- Cubicake, creator and maintainer of RaycastedAntiESP
- RaycastedAntiESP contributors
- P2wn and PieCloak contributors
- PacketEvents, StrokkCommands, Paper, Spigot, and Bukkit contributors
