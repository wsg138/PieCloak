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

## Required Validation

After any ported upstream update, run:

```powershell
.\gradlew.bat :platform-paper:compileJava --no-daemon
.\gradlew.bat :platform-paper:build --no-daemon
.\gradlew.bat :platform-paper:buildSnapshot --no-daemon
```

Then test on a server before production use:

- target entities remain packet-hidden only when allowlisted
- non-allowlisted entities and block entities behave normally
- players are not hidden
- farms, trading halls, bees, villagers, allays, golems, and block entities continue working server-side
- target entities do not leak through spawn/metadata/movement packets
- target block entities do not leak through chunk data, block entity data, block change, or multi-block change packets
- login, teleport, dimension change, chunk reload, render-distance entry, and elytra movement do not create one-tick leaks

## Attribution And License

Keep the original AGPL license and attribution intact:

- preserve `LICENSE`
- preserve original source notices
- keep README links to the original RaycastedAntiESP project
- keep PieCloak public source available at https://github.com/wsg138/PieCloak
