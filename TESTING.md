# PieCloak / RaycastedAntiESP Testing Checklist

Use a normal client plus a pie-chart/base-finding client or packet capture where possible.

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
