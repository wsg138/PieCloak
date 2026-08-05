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

## Entity Desync Regression

- Join and relog beside an allowlisted iron golem inside `always-show-radius`; it must be visible immediately.
- Grant `raycastedantiesp.bypass`, relog, and verify all managed entities remain visible and continue moving.
- Move a villager in a minecart across visible and hidden boundaries; both must hide and show together.
- Dismount the villager with an empty `SET_PASSENGERS`; the minecart and villager must resume independent visibility.
- Destroy the minecart while mounted, then repeat by removing the passenger first; neither case may leave stale visibility.
- Repeat at least 100 hide/show cycles and confirm replay state and heap usage remain bounded.
- Force one SHOW replay failure in a development build and verify other transitions still process and the failed SHOW retries.
