# PieCloak

PieCloak is a public modified fork of [RaycastedAntiESP](https://github.com/Cubicake/RaycastedAntiESP) for Paper/Leaf 1.21.x servers.

This fork adds an allowlist-based target filter so packet-level anti-ESP handling only applies to configured entity and block entity clues used for pie-chart/base-finding. Non-allowlisted entities and block entities are sent normally.

Public source: https://github.com/wsg138/PieCloak

## Behavior

- Does not cancel Bukkit spawn events.
- Does not affect server-side spawning, AI, farms, breeding, villagers, trades, or block entity ticking.
- Does not hide players.
- Hides only configured allowlisted targets from outgoing client packets.
- Rewrites chunk data before the client receives target block entity data.

## Upstream

Original project: [Cubicake/RaycastedAntiESP](https://github.com/Cubicake/RaycastedAntiESP)

PieCloak keeps the upstream history and uses RaycastedAntiESP as the base project, but upstream changes should be reviewed manually before being ported. Do not blindly merge or rebase upstream into PieCloak.

## What Changed From REO

PieCloak changes are intentionally limited to these areas:

- Target filter config and resolution:
  - `core/src/main/java/games/cubi/raycastedantiesp/core/config/TargetFilterConfig.java`
  - `platform-paper/src/main/java/games/cubi/raycastedantiesp/paper/target/PaperTargetFilterService.java`
- Packet filtering gates:
  - `packetevents/src/main/java/games/cubi/raycastedantiesp/packetevents/viewcontrollers/PacketEventsEntityViewController.java`
  - `packetevents/src/main/java/games/cubi/raycastedantiesp/packetevents/viewcontrollers/PacketEventsBlockViewController.java`
- Admin diagnostics:
  - `platform-paper/src/main/java/games/cubi/raycastedantiesp/paper/commands/RaycastedAntiESPCommand.java`
  - `core/src/main/java/games/cubi/raycastedantiesp/core/stats/VisibilityStats.java`
  - `core/src/main/java/games/cubi/raycastedantiesp/core/debug/VisibilityTraceService.java`
- Defaults and testing docs:
  - `platform-paper/src/main/resources/config.yml`
  - `TESTING.md`
  - `MAINTAINING.md`

## Commands

- `/raesp stats`
- `/raesp debugplayer <player>`
- `/raesp benchmark <radius> <samples>`
- `/raesp trace`
- `/raesp source`
- `/piecloak source`

## Safe Updating

See [MAINTAINING.md](MAINTAINING.md) before porting upstream changes.

Short version:

- Fetch upstream first.
- Compare upstream changes against PieCloak.
- Review upstream commits manually.
- Port only useful fixes or compatibility updates.
- Do not overwrite PieCloak's allowlist behavior, config, player-hiding defaults, or packet hiding logic.
- Build and server-test any ported update before production use.

## Build

```powershell
.\gradlew.bat :platform-paper:build
```

The usable plugin jar is the shaded `RaycastedAntiESP-*.jar` in `platform-paper/build/libs`.

## Testing

See [TESTING.md](TESTING.md) for the in-server checklist.

## License

PieCloak remains licensed under the GNU Affero General Public License v3.0, the same license used by RaycastedAntiESP. The original license file and attribution are preserved in this repository.
