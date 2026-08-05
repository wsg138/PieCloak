# PieCloak

[![Codacy Badge](https://app.codacy.com/project/badge/Grade/5aa184993c2247ca9df86b95e6b6361f)](https://app.codacy.com/gh/wsg138/PieCloak/dashboard)

PieCloak is a public modified fork of [RaycastedAntiESP](https://github.com/Cubicake/RaycastedAntiESP) for Paper and Leaf 1.21.x servers.

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

```powershell
.\gradlew.bat :platform-paper:build --no-daemon
```

The shaded plugin jar is written to `platform-paper/build/libs` with a `RaycastedAntiESP-` filename.

Snapshot builds include the Git commit and build time:

```powershell
.\gradlew.bat :platform-paper:buildSnapshot --no-daemon
```

## Testing and upstream maintenance

See [TESTING.md](TESTING.md) for the server test checklist and [MAINTAINING.md](MAINTAINING.md) before integrating later upstream changes.

This update is based on upstream commit `853fa1531acbbb1458f776bbe2dc637fd0d40b7c` from August 5, 2026.

## License and credits

PieCloak and RaycastedAntiESP are licensed under the GNU Affero General Public License v3.0 only. Original copyright and license notices are preserved.

- Cubicake, creator and maintainer of RaycastedAntiESP
- RaycastedAntiESP contributors
- P2wn and PieCloak contributors
- PacketEvents, StrokkCommands, Paper, Spigot, and Bukkit contributors
