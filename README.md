# PieCloak

PieCloak is a private fork of RaycastedAntiESP for Paper/Leaf 1.21.x servers.

This fork adds an allowlist-based target filter so packet-level anti-ESP handling only applies to configured entity and block entity clues used for pie-chart/base-finding. Non-allowlisted entities and block entities are sent normally.

## Behavior

- Does not cancel Bukkit spawn events.
- Does not affect server-side spawning, AI, farms, breeding, villagers, trades, or block entity ticking.
- Does not hide players.
- Hides only configured allowlisted targets from outgoing client packets.
- Rewrites chunk data before the client receives target block entity data.

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
- Defaults and testing docs:
  - `platform-paper/src/main/resources/config.yml`
  - `TESTING.md`

## Commands

- `/raesp stats`
- `/raesp debugplayer <player>`
- `/raesp benchmark <radius> <samples>`

## Build

```powershell
.\gradlew.bat :platform-paper:build
```

The usable plugin jar is the shaded `RaycastedAntiESP-*.jar` in `platform-paper/build/libs`.

## Testing

See [TESTING.md](TESTING.md) for the in-server checklist.
