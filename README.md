# PieCloak

[![Codacy Badge](https://app.codacy.com/project/badge/Grade/5aa184993c2247ca9df86b95e6b6361f)](https://app.codacy.com/gh/wsg138/PieCloak/dashboard)

PieCloak is a public modified fork of [RaycastedAntiESP](https://github.com/Cubicake/RaycastedAntiESP) for Paper and Leaf 1.21.x servers.

It adds an allowlist-based target filter so anti-ESP packet handling applies only to configured entity and block-entity clues. Non-allowlisted targets are sent normally.

Public source: https://github.com/wsg138/PieCloak

## Behavior

- Does not cancel Bukkit spawn events.
- Does not change server-side spawning, AI, farms, breeding, villager trading, or block-entity ticking.
- Does not hide players with the default PieCloak configuration.
- Hides only configured allowlisted targets from outgoing client packets.
- Filters target block entities from chunk data before the client receives them.
- Tracks mounted managed entities with their vehicles to avoid passenger desync.

PacketEvents is required.

## Commands

Administrative commands require `raycastedantiesp.command`:

- `/raesp reload`
- `/raesp config-values`
- `/raesp set <key> <value>`
- `/raesp add <key> <value>`
- `/raesp remove <key> <value>`
- `/raesp stats`
- `/raesp debugplayer <player>`
- `/raesp benchmark <radius> <samples>`
- `/raesp trace ...`
- `/raesp source`

Public source and attribution commands:

- `/piecloak source`
- `/raycastedantiespCredits`

## Build

```powershell
.\gradlew.bat :platform-paper:build --no-daemon
```

The usable shaded jar is written to `platform-paper/build/libs` with a `RaycastedAntiESP-` filename.

Snapshot builds include the Git commit and build time:

```powershell
.\gradlew.bat :platform-paper:buildSnapshot --no-daemon
```

## Testing

See [TESTING.md](TESTING.md) for the server test checklist.

## Upstream

Original project: [Cubicake/RaycastedAntiESP](https://github.com/Cubicake/RaycastedAntiESP)

PieCloak preserves the upstream Git history. Upstream changes are reviewed and integrated manually because PieCloak changes entity targeting, packet filtering, visibility transitions, replay state, and passenger handling.

See [MAINTAINING.md](MAINTAINING.md) before integrating upstream updates.

## Credits

- Cubicake, creator and maintainer of RaycastedAntiESP
- RaycastedAntiESP contributors
- PacketEvents contributors
- StrokkCommands contributors
- Paper, Spigot, and Bukkit contributors

## License

PieCloak and RaycastedAntiESP are licensed under the GNU Affero General Public License v3.0 only. The original copyright and license notices are preserved.

The complete corresponding source for PieCloak is available in this repository. See [LICENSE](LICENSE) for the full license text.
