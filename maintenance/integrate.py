#!/usr/bin/env python3
"""Reconcile the pinned content baseline while retaining fork-only invariants."""
from pathlib import Path
import re
import subprocess
import sys

FORK, UPSTREAM, BASE = sys.argv[1:]
def git(*args):
    return subprocess.check_output(["git", *args], text=True)
def old(p):
    return git("show", f"{FORK}:{p}")
def upstream(p):
    return git("show", f"{UPSTREAM}:{p}")
def put(p, s):
    p=Path(p); p.parent.mkdir(parents=True,exist_ok=True); p.write_text(s)
def edit(p, f):
    p=Path(p); p.write_text(f(p.read_text()))
def replace(s,a,b):
    if a not in s:
        raise RuntimeError("Missing expected anchor: "+a[:100])
    return s.replace(a,b)
C="core/src/main/java/games/cubi/raycastedantiesp/core/"
CT="core/src/test/java/games/cubi/raycastedantiesp/core/"
P="packetevents/src/main/java/games/cubi/raycastedantiesp/packetevents/"
PT="packetevents/src/test/java/games/cubi/raycastedantiesp/packetevents/"
B="platform-paper/src/main/java/games/cubi/raycastedantiesp/paper/"

# Resolve actual content conflicts only. The fork has stronger staged retries,
# ownership fencing, DDA geometry, and authoritative managed/nonmanaged metadata.
# Preserve those implementations as the starting point and adapt upstream APIs.
retain={
    "README.md","build.gradle.kts","platform-paper/src/main/resources/config.yml",
    C+"engine/AsyncEngine.java",C+"raycast/RaycastUtil.java",
    C+"tracked/NettyTileEntity.java",C+"tracked/TrackedTileEntity.java",
    C+"view/AbstractBlockView.java",C+"view/BlockView.java",C+"view/PackedBlockTransitionQueue.java",
    C+"view/controller/PacketEntityViewController.java",
    CT+"raycast/RaycastUtilTest.java",CT+"tracked/NettyEntityTrackedStateTest.java",
    CT+"view/controller/PacketEntityViewControllerTest.java",
    P+"replaydata/PacketEventsEntityReplayData.java",P+"viewcontrollers/PacketEventsBlockViewController.java",
    P+"viewcontrollers/PacketEventsEntityViewController.java",P+"viewcontrollers/chunkparser/AbstractChunkParser.java",
    PT+"viewcontrollers/PacketEventsEntityViewControllerTest.java",
    B+"EventListener.java",B+"FancyCompatibility.java",B+"RaycastedAntiESP.java",B+"UpdateChecker.java",
    B+"commands/RaycastedAntiESPCommand.java",B+"packets/PacketEventsPaperBlockInfoResolver.java",
    B+"packets/PaperPacketEventsCommonViewController.java",B+"utils/PaperTicker.java",
}
for p in sorted(retain):
    put(p,old(p))
# Preserve the old strict DDA rather than exchanging it for an algorithm that
# intentionally misses intermediate voxels; expose upstream chunk-view API.
edit("build.gradle.kts",lambda s:s.rstrip()+"\n\n"+upstream("build.gradle.kts").rstrip()+"\n")
edit(C+"view/BlockView.java",lambda s:replace(s,"    boolean isBlockOccluding(int x, int y, int z);","""    boolean isBlockOccluding(int x, int y, int z);

    default games.cubi.raycastedantiesp.core.chunks.ChunkOcclusionView getChunkOcclusionView(int chunkX, int chunkY, int chunkZ) {
        return null;
    }"""))
edit(C+"view/AbstractBlockView.java",lambda s:replace(s,"    @Override\n    public boolean isBlockOccluding(int x, int y, int z) {","""    @Override
    public games.cubi.raycastedantiesp.core.chunks.ChunkOcclusionView getChunkOcclusionView(int chunkX, int chunkY, int chunkZ) {
        return chunks.getSection(chunkX, chunkY, chunkZ);
    }

    @Override
    public boolean isBlockOccluding(int x, int y, int z) {"""))

# Adopt upstream's configuration 2.1 serializer and code-defined default model.
# Keep PieCloak's independent allowlist and retain legacy ID overrides verbatim
# rather than silently discarding operator configuration.
target=old(C+"config/TargetFilterConfig.java")
target=target.replace("import org.spongepowered.configurate.ConfigurationNode;","import org.spongepowered.configurate.objectmapping.ConfigSerializable;")
target=target.replace("public record TargetFilterConfig(","@ConfigSerializable\npublic record TargetFilterConfig(")
start=target.index("    public static final ConfigExtension")
end=target.index("    public TargetFilterConfig {",start)
target=target[:start]+"""    public static final TargetFilterConfig DEFAULT = new TargetFilterConfig(true, Mode.ALLOWLIST,
            List.of("minecraft:villager", "minecraft:copper_golem", "minecraft:armadillo", "minecraft:wolf",
                    "minecraft:cat", "minecraft:ocelot", "minecraft:allay", "minecraft:bee", "minecraft:iron_golem",
                    "minecraft:snow_golem", "minecraft:item_frame", "minecraft:glow_item_frame", "minecraft:armor_stand", "minecraft:painting"),
            List.of("minecraft:campfire", "minecraft:soul_campfire", "minecraft:decorated_pot", "minecraft:bell",
                    "minecraft:jukebox", "minecraft:conduit", "minecraft:beacon", "minecraft:moving_piston"),
            List.of("shulker_boxes", "signs", "hanging_signs", "banners", "wall_banners", "beds", "heads_and_skulls"));

"""+target[end:]
start=target.index("    public static TargetFilterConfig load(")
end=target.index("    public static String normalizeKey",start)
target=target[:start]+target[end:]
put(C+"config/TargetFilterConfig.java",target)
edit(C+"config/RootConfig.java",lambda s:replace(replace(s,'@Setting("updates") UpdateConfig updateConfig','@Setting("updates") UpdateConfig updateConfig,\n        @Setting("target-filter") TargetFilterConfig targetFilterConfig'),'UpdateConfig.DEFAULT\n','UpdateConfig.DEFAULT,\n            TargetFilterConfig.DEFAULT\n'))
edit(C+"config/ConfigMapping.java",lambda s:replace(s,'.registerExact(BlockProcessorMode.class, enumSerializer(BlockProcessorMode::fromString, BlockProcessorMode::getName))','.registerExact(BlockProcessorMode.class, enumSerializer(BlockProcessorMode::fromString, BlockProcessorMode::getName))\n                .registerExact(TargetFilterConfig.Mode.class, enumSerializer(TargetFilterConfig.Mode::fromConfigName, Enum::name))'))
edit(C+"config/ConfigManager.java",lambda s:replace(replace(s,'    public DebugConfig getDebugConfig() {','    public TargetFilterConfig getTargetFilterConfig() {\n        return requireReady().targetFilterConfig();\n    }\n\n    public DebugConfig getDebugConfig() {'),'        if (!next.blockProcessorConfig().equals(startupConfig.blockProcessorConfig())) {','        if (!next.targetFilterConfig().equals(startupConfig.targetFilterConfig())) {\n            throw new RestartRequiredException("target-filter cannot be changed without a restart.");\n        }\n        if (!next.blockProcessorConfig().equals(startupConfig.blockProcessorConfig())) {'))
# Keep numeric state IDs at their original key. New selectors remain supported.
put(C+"config/LegacyPacketEventsOverrides.java","""package games.cubi.raycastedantiesp.core.config;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import java.util.List;

@ConfigSerializable
public record LegacyPacketEventsOverrides(List<Integer> tileEntityExemptedIds,
                                          List<Integer> tileEntityForceIncludedIds) {
    public static final LegacyPacketEventsOverrides DEFAULT = new LegacyPacketEventsOverrides(List.of(), List.of());
    public LegacyPacketEventsOverrides {
        tileEntityExemptedIds = List.copyOf(tileEntityExemptedIds);
        tileEntityForceIncludedIds = List.copyOf(tileEntityForceIncludedIds);
    }
}
""")
edit(C+"config/BlockProcessorConfig.java",lambda s:replace(replace(s,'boolean trackAllBlocks\n','boolean trackAllBlocks,\n        @org.spongepowered.configurate.objectmapping.meta.Setting("packetevents") LegacyPacketEventsOverrides packetEventsOverrides\n'),'new BlockProcessorConfig(BlockProcessorMode.PACKETEVENTS, false)','new BlockProcessorConfig(BlockProcessorMode.PACKETEVENTS, false, LegacyPacketEventsOverrides.DEFAULT)'))
edit(C+"config/ConfigMigration.java",lambda s:replace(s,'''        builder.addAction(NodePath.path("block-processor", "packetevents"), (path, value) -> {
            value.raw(null);
            return null;
        });
''',''))
edit(C+"config/ConfigCandidate.java",lambda s:replace(s,'        if (!root.blockProcessorConfig().trackAllBlocks() && root.checksConfig().chunkSectionConfig().enabled()) {','''        if (!root.blockProcessorConfig().trackAllBlocks()
                && !root.blockProcessorConfig().packetEventsOverrides().tileEntityForceIncludedIds().isEmpty()) {
            violations.add(new AbsoluteViolation(
                    NodePath.path("block-processor", "packetevents", "tile-entity-force-included-ids"),
                    "must be empty when block-processor.track-all-blocks is false"
            ));
        }
        if (!root.blockProcessorConfig().trackAllBlocks() && root.checksConfig().chunkSectionConfig().enabled()) {'''))
# Ensure a pre-migration backup is made before any replacement of a 2.0 config.
edit(C+"config/ConfigFile.java",lambda s:replace(s,'            return Files.exists(configPath) ? loader.load() : loader.createNode();','''            if (!Files.exists(configPath)) {
                return loader.createNode();
            }
            CommentedConfigurationNode loaded = loader.load();
            if ("2.0".equals(loaded.node("config-version").raw())) {
                Path backup = dataFolder.resolve("config.yml.pre-2.1.bak");
                try {
                    Files.copy(configPath, backup);
                } catch (java.nio.file.FileAlreadyExistsException ignored) {
                    // Never overwrite the original migration backup.
                }
            }
            return loaded;'''))
# Deliberately preserve the fork's policy defaults, not upstream's player hiding.
edit(C+"config/raycast/PlayerConfig.java",lambda s:replace(s,'new RaycastSettings(true, (byte) 3, (short) 8, (short) 128, (short) 24, (short) 5)','new RaycastSettings(false, (byte) 3, (short) 24, (short) 48, (short) 0, (short) 10)').replace('            true\n    );','            false\n    );'))
edit(C+"config/raycast/EntityConfig.java",lambda s:replace(s,'new RaycastSettings(true, (byte) 3, (short) 8, (short) 92, (short) 24, (short) 5)','new RaycastSettings(true, (byte) 3, (short) 24, (short) 48, (short) 0, (short) 10)'))
edit(C+"config/raycast/TileEntityConfig.java",lambda s:replace(replace(s,'new RaycastSettings(true, (byte) 3, (short) 8, (short) 64, (short) 24, (short) -1)','new RaycastSettings(true, (byte) 3, (short) 24, (short) 48, (short) 0, (short) 20)'),'List.of(BlockSelector.parse("minecraft:beacon"))','List.of()'))
edit(C+"config/UpdateConfig.java",lambda s:replace(s,'new UpdateConfig(true, true, true, true)','new UpdateConfig(false, false, false, false)'))
edit(B+"RaycastedAntiESP.java",lambda s:replace(s.replace('import games.cubi.raycastedantiesp.packetevents.config.PacketEventsBlockProcessorConfig;\n',''),'''            config = ConfigManager.initialiseConfigManager(
                    () -> getResource("config.yml"),
                    getDataFolder().toPath(),
                    List.of(PacketEventsBlockProcessorConfig.EXTENSION, TargetFilterConfig.EXTENSION)
            );''','''            config = ConfigManager.initialiseConfigManager(getDataFolder().toPath());
            config.completeInitialLoad();'''))
edit(B+"target/PaperTargetFilterService.java",lambda s:s.replace('getExtensionConfig(TargetFilterConfig.class)','getTargetFilterConfig()'))
edit(B+"packets/PacketEventsPaperBlockInfoResolver.java",lambda s:replace(replace(replace(s,'import games.cubi.raycastedantiesp.packetevents.config.PacketEventsBlockProcessorConfig;','import games.cubi.raycastedantiesp.core.config.LegacyPacketEventsOverrides;\nimport games.cubi.raycastedantiesp.paper.config.PaperBlockSelectorMatcher;'),'PacketEventsBlockProcessorConfig config = RaycastedAntiESP.getConfigManager().getExtensionConfig(PacketEventsBlockProcessorConfig.class);','''var settings = RaycastedAntiESP.getConfigManager();
        var selectors = new PaperBlockSelectorMatcher(settings.getTileEntityConfig().excludedBlocks());
        for (int id = 0; id < tileEntityArray.length; id++) {
            if (tileEntityArray[id]) {
                BlockData data = SpigotConversionUtil.toBukkitBlockData(WrappedBlockState.getByGlobalId(id));
                if (data != null && selectors.test(data)) {
                    tileEntityArray[id] = false;
                }
            }
        }
        LegacyPacketEventsOverrides config = settings.getBlockProcessorConfig().packetEventsOverrides();'''),'occlusion.put(iterator, blockData.getMaterial().isOccluding());','occlusion.put(iterator, blockData.getMaterial() != Material.BARRIER && blockData.getMaterial().isOccluding());'))
# The source bundle includes the old YAML as reference; upstream generates new
# configuration from code. Do not ship a contradictory 2.0 resource.
Path("platform-paper/src/main/resources/config.yml").unlink(missing_ok=True)
edit(CT+"config/ConfigManagerTest.java",lambda s:replace(s,'assertTrue(migrated.node("block-processor", "packetevents").virtual());','assertEquals(List.of(1), migrated.node("block-processor", "packetevents", "tile-entity-exempted-ids").getList(Integer.class));\n        assertTrue(Files.isRegularFile(configPath().resolveSibling("config.yml.pre-2.1.bak")));'))

# Upstream registry now caches a snapshot. Use it in the fork's guarded engine
# rather than losing shutdown and worker-failure handling.
edit(C+"engine/AsyncEngine.java",lambda s:replace(s,'Collection<PlayerData> allPlayers = PlayerRegistry.getInstance().getAllPlayerData();','Collection<PlayerData> allPlayers = java.util.Arrays.asList(PlayerRegistry.get().getPlayerDataArray());'))
edit(B+"EventListener.java",lambda s:replace(s,'        Player player = event.getPlayer();','        Player player = event.getPlayer();\n        if (player.hasMetadata("NPC")) return;',) )
# Unify renamed utility packages and transition enum references across fork additions.
mapping={
 "games.cubi.raycastedantiesp.core.utils.Clearable":"games.cubi.utils.Clearable",
 "games.cubi.raycastedantiesp.core.utils.VarHandler":"games.cubi.utils.VarHandler",
 "games.cubi.raycastedantiesp.core.utils.CanonicalSet":"games.cubi.utils.CanonicalSet",
 "games.cubi.raycastedantiesp.core.utils.ConcurrentSelfMap":"games.cubi.utils.ConcurrentSelfMap",
 "games.cubi.raycastedantiesp.core.utils.PrimitiveIntArrayList":"games.cubi.utils.lists.PrimitiveIntArrayList",
 "games.cubi.raycastedantiesp.core.utils.IntArrayListMarker":"games.cubi.utils.lists.IntArrayListMarker",
 "games.cubi.raycastedantiesp.core.utils.InvasivelyLinkedSWMRList":"games.cubi.utils.lists.InvasivelyLinkedSWMRList",
 "games.cubi.raycastedantiesp.core.utils.BaseEntitySpawnTask":"games.cubi.raycastedantiesp.core.tracked.BaseEntitySpawnTask",
 "PlayerRegistry.getInstance()":"PlayerRegistry.get()",
 "BlockViewTransition.Type":"TransitionType.Tile",
 "EntityViewTransition.Type":"TransitionType.Entity",
 "import games.cubi.raycastedantiesp.core.view.BlockViewTransition;":"import games.cubi.raycastedantiesp.core.view.TransitionType;",
 "import games.cubi.raycastedantiesp.core.view.EntityViewTransition;":"import games.cubi.raycastedantiesp.core.view.TransitionType;",
}
for p in [*Path("core").rglob("*.java"),*Path("packetevents").rglob("*.java"),*Path("platform-paper").rglob("*.java")]:
    s=p.read_text()
    for a,b in mapping.items(): s=s.replace(a,b)
    # Do not retain repeated imports introduced when two old types collapse.
    seen=set(); lines=[]
    for line in s.splitlines(keepends=True):
        if line.startswith("import "):
            if line in seen: continue
            seen.add(line)
        lines.append(line)
    p.write_text("".join(lines))
# Final replay objects are constructed by upstream PacketEventsEntity.
# The fork's replay recovery code must use the same object rather than reassign.
p=P+"viewcontrollers/PacketEventsEntityViewController.java"
edit(p,lambda s:replace(s,'''        PacketEventsEntityReplayData replayData = entity.packetReplayData();
        if (replayData == null) {
            replayData = PacketEventsEntityReplayData.create();
            entity.setPacketReplayData(replayData);
        }
        return replayData;''','''        return entity.packetReplayData();'''))
# Keep the fork's independent safe replay/cache lifecycle until exact regression
# coverage demonstrates the new final replay semantics. Reconcile its tracked
# entity constructors and API as a unit, not piecemeal.
# Use explicit integration tests and compilation before merging anything.
# Remove all markers (including nonconflicting files that were not expected).
for p in [*Path("core").rglob("*.java"),*Path("packetevents").rglob("*.java"),*Path("platform-paper").rglob("*.java"),Path("README.md"),Path("build.gradle.kts")]:
    if re.search(r"^<<<<<<<|^>>>>>>>",p.read_text(),re.M):
        raise RuntimeError(f"unresolved merge marker in {p}")
print("Resolved pinned baseline; typed fork configuration and compatibility adapted.")
