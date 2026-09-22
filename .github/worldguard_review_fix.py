from pathlib import Path


def replace_once(path, old, new):
    p = Path(path)
    text = p.read_text()
    if old not in text:
        raise RuntimeError(f"expected text not found in {path}: {old[:120]!r}")
    p.write_text(text.replace(old, new, 1))


def write(path, content):
    p = Path(path)
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(content)


# Core policy: active exemptions need a bounded visible recheck even when ordinary visible
# rechecks are disabled or configured very slowly.
write("core/src/main/java/games/cubi/raycastedantiesp/core/policy/VisibilityExemptionPolicy.java", '''package games.cubi.raycastedantiesp.core.policy;

import games.cubi.locatables.api.Locatable;

import java.util.UUID;

/**
 * Target-location policy which can opt a position out of normal visibility hiding.
 * Implementations must be safe for concurrent packet-thread and engine-thread queries.
 */
@FunctionalInterface
public interface VisibilityExemptionPolicy {
    int MAX_EXEMPTION_RECHECK_TICKS = 20;

    VisibilityExemptionPolicy DISABLED = new VisibilityExemptionPolicy() {
        @Override
        public boolean isExempt(UUID world, double x, double y, double z) {
            return false;
        }

        @Override
        public boolean isActive() {
            return false;
        }
    };

    boolean isExempt(UUID world, double x, double y, double z);

    default boolean isExempt(Locatable location) {
        return location != null
                && location.world() != null
                && isExempt(location.world(), location.x(), location.y(), location.z());
    }

    default boolean isActive() {
        return true;
    }

    /**
     * Exemption changes must be noticed even when ordinary visible-target rechecks are disabled or slow.
     */
    default int effectiveVisibleRecheckTicks(int configuredRecheckTicks) {
        if (!isActive()) {
            return configuredRecheckTicks;
        }
        return configuredRecheckTicks < 0
                ? MAX_EXEMPTION_RECHECK_TICKS
                : Math.min(configuredRecheckTicks, MAX_EXEMPTION_RECHECK_TICKS);
    }
}
''')

replace_once(
    "core/src/main/java/games/cubi/raycastedantiesp/core/engine/AsyncVisibilityChecks.java",
    "    private static final int EXEMPTION_RECHECK_TICKS = 20;\n\n",
    ""
)
replace_once(
    "core/src/main/java/games/cubi/raycastedantiesp/core/engine/AsyncVisibilityChecks.java",
    '''    private int effectiveRecheckTicks(int configuredRecheckTicks) {
        if (configuredRecheckTicks >= 0 || !visibilityExemptionPolicy.isActive()) {
            return configuredRecheckTicks;
        }
        return EXEMPTION_RECHECK_TICKS;
    }
''',
    '''    private int effectiveRecheckTicks(int configuredRecheckTicks) {
        return visibilityExemptionPolicy.effectiveVisibleRecheckTicks(configuredRecheckTicks);
    }
'''
)
replace_once(
    "core/src/main/java/games/cubi/raycastedantiesp/core/engine/AsyncEngine.java",
    '''        int configuredRecheckTicks = tileEntityConfig.getVisibleRecheckIntervalTicks();
        int recheckTicks = configuredRecheckTicks >= 0 || !visibilityExemptionPolicy.isActive()
                ? configuredRecheckTicks : 20;
''',
    '''        int configuredRecheckTicks = tileEntityConfig.getVisibleRecheckIntervalTicks();
        int recheckTicks = visibilityExemptionPolicy.effectiveVisibleRecheckTicks(configuredRecheckTicks);
'''
)

# If a hidden entity enters an exempt region on a relative movement packet, direct SHOW already
# synchronizes the new position. Suppress the triggering movement packet so it cannot apply twice.
replace_once(
    "core/src/main/java/games/cubi/raycastedantiesp/core/view/controller/PacketEntityViewController.java",
    '''        if (exempt) {
            if (!entity.visible() || !entity.clientVisible()) {
                applyDirectVisibility(playerData, entity, true, currentTick);
            }
            return false;
        }
''',
    '''        if (exempt) {
            if (!entity.visible() || !entity.clientVisible()) {
                applyDirectVisibility(playerData, entity, true, currentTick);
                // Direct SHOW is built from the already-updated tracked position. Forwarding the
                // movement packet as well would apply relative movement twice on the client.
                return true;
            }
            return false;
        }
'''
)

# Paper-facing policy interface avoids leaking WorldGuard types into the main plugin class when
# the optional dependency is absent.
write("platform-paper/src/main/java/games/cubi/raycastedantiesp/paper/integrations/PaperVisibilityExemptionPolicy.java", '''package games.cubi.raycastedantiesp.paper.integrations;

import games.cubi.raycastedantiesp.core.policy.VisibilityExemptionPolicy;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/** Platform lifecycle for optional visibility-exemption integrations. */
public interface PaperVisibilityExemptionPolicy extends VisibilityExemptionPolicy, AutoCloseable {
    PaperVisibilityExemptionPolicy DISABLED = new PaperVisibilityExemptionPolicy() {
        @Override
        public boolean isExempt(UUID world, double x, double y, double z) {
            return false;
        }

        @Override
        public boolean isActive() {
            return false;
        }

        @Override
        public void enable(JavaPlugin plugin) {
        }

        @Override
        public void close() {
        }
    };

    void enable(JavaPlugin plugin);

    @Override
    default void close() {
    }
}
''')

write("platform-paper/src/main/java/games/cubi/raycastedantiesp/paper/integrations/WorldGuardVisibilityExemption.java", '''package games.cubi.raycastedantiesp.paper.integrations;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import games.cubi.logs.Logger;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Optional WorldGuard-backed target-location visibility exemption. */
public final class WorldGuardVisibilityExemption implements PaperVisibilityExemptionPolicy, Listener {
    public static final String FLAG_NAME = "piecloak-skip";
    private static final int MAX_QUERY_FAILURE_DIAGNOSTICS = 5;

    private final StateFlag flag;
    private final RegionContainer regionContainer;
    private final Map<UUID, com.sk89q.worldedit.world.World> worlds = new ConcurrentHashMap<>();
    private final AtomicInteger queryFailures = new AtomicInteger();
    private final AtomicBoolean enabled = new AtomicBoolean();

    private WorldGuardVisibilityExemption(StateFlag flag) {
        this.flag = flag;
        this.regionContainer = WorldGuard.getInstance().getPlatform().getRegionContainer();
    }

    /** Registers the custom flag during plugin load, before WorldGuard locks its registry. */
    public static WorldGuardVisibilityExemption register() {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        StateFlag requested = new StateFlag(FLAG_NAME, false);
        StateFlag registered;
        try {
            registry.register(requested);
            registered = requested;
        } catch (FlagConflictException conflict) {
            Flag<?> existing = registry.get(FLAG_NAME);
            if (!(existing instanceof StateFlag stateFlag)) {
                throw new IllegalStateException("WorldGuard flag '" + FLAG_NAME
                        + "' already exists with incompatible type "
                        + (existing == null ? "null" : existing.getClass().getName()), conflict);
            }
            registered = stateFlag;
        }
        Logger.info("WorldGuard integration registered region flag '" + FLAG_NAME + "'.",
                4, WorldGuardVisibilityExemption.class);
        return new WorldGuardVisibilityExemption(registered);
    }

    @Override
    public void enable(JavaPlugin plugin) {
        if (!enabled.compareAndSet(false, true)) {
            return;
        }
        try {
            for (org.bukkit.World world : Bukkit.getWorlds()) {
                rememberWorld(world);
            }
            Bukkit.getPluginManager().registerEvents(this, plugin);
        } catch (RuntimeException | Error throwable) {
            enabled.set(false);
            worlds.clear();
            HandlerList.unregisterAll(this);
            throw throwable;
        }
    }

    @Override
    public void close() {
        if (enabled.compareAndSet(true, false)) {
            HandlerList.unregisterAll(this);
        }
        worlds.clear();
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        rememberWorld(event.getWorld());
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        worlds.remove(event.getWorld().getUID());
    }

    private void rememberWorld(org.bukkit.World world) {
        worlds.put(world.getUID(), BukkitAdapter.adapt(world));
    }

    @Override
    public boolean isExempt(UUID worldId, double x, double y, double z) {
        com.sk89q.worldedit.world.World world = worlds.get(worldId);
        if (world == null) {
            return false;
        }
        try {
            var query = regionContainer.createQuery();
            var location = new com.sk89q.worldedit.util.Location(world, x, y, z);
            return query.testState(location, null, flag);
        } catch (RuntimeException exception) {
            int failure = queryFailures.incrementAndGet();
            if (failure <= MAX_QUERY_FAILURE_DIAGNOSTICS) {
                String suffix = failure == MAX_QUERY_FAILURE_DIAGNOSTICS
                        ? " (further WorldGuard query failures suppressed)" : "";
                Logger.error("WorldGuard '" + FLAG_NAME + "' query failed for world=" + worldId
                                + " position=" + x + "," + y + "," + z
                                + ". Falling back to normal PieCloak visibility policy." + suffix,
                        exception, 2, WorldGuardVisibilityExemption.class);
            }
            return false;
        }
    }
}
''')

# Main plugin lifecycle: no WorldGuard-typed fields; own listener/cache lifecycle so failed startup and
# same-JVM disable/re-enable do not leave duplicate listeners or stale worlds.
replace_once(
    "platform-paper/src/main/java/games/cubi/raycastedantiesp/paper/RaycastedAntiESP.java",
    "import games.cubi.raycastedantiesp.paper.integrations.WorldGuardVisibilityExemption;\n",
    "import games.cubi.raycastedantiesp.paper.integrations.PaperVisibilityExemptionPolicy;\nimport games.cubi.raycastedantiesp.paper.integrations.WorldGuardVisibilityExemption;\n"
)
replace_once(
    "platform-paper/src/main/java/games/cubi/raycastedantiesp/paper/RaycastedAntiESP.java",
    '''    private static VisibilityExemptionPolicy visibilityExemptionPolicy = VisibilityExemptionPolicy.DISABLED;
    private static WorldGuardVisibilityExemption worldGuardVisibilityExemption;
''',
    '''    private static PaperVisibilityExemptionPolicy visibilityExemptionPolicy =
            PaperVisibilityExemptionPolicy.DISABLED;
'''
)
replace_once(
    "platform-paper/src/main/java/games/cubi/raycastedantiesp/paper/RaycastedAntiESP.java",
    '''        worldGuardVisibilityExemption = initialiseWorldGuardVisibilityExemption();
        visibilityExemptionPolicy = worldGuardVisibilityExemption == null
                ? VisibilityExemptionPolicy.DISABLED : worldGuardVisibilityExemption;
''',
    '''        visibilityExemptionPolicy = initialiseWorldGuardVisibilityExemption();
'''
)
replace_once(
    "platform-paper/src/main/java/games/cubi/raycastedantiesp/paper/RaycastedAntiESP.java",
    '''        initialiseConfigIfNeeded();
        if (worldGuardVisibilityExemption != null) {
            worldGuardVisibilityExemption.enable(this);
        }

        LifecycleScope startup = new LifecycleScope();
''',
    '''        initialiseConfigIfNeeded();

        LifecycleScope startup = new LifecycleScope();
        startup.onClose(visibilityExemptionPolicy::close);
'''
)
replace_once(
    "platform-paper/src/main/java/games/cubi/raycastedantiesp/paper/RaycastedAntiESP.java",
    '''        try {
            PaperEntityTypeExclusionResolver.resolveAndInitialise(config.getEntityConfig().excludedTypes());
''',
    '''        try {
            visibilityExemptionPolicy.enable(this);
            PaperEntityTypeExclusionResolver.resolveAndInitialise(config.getEntityConfig().excludedTypes());
'''
)
replace_once(
    "platform-paper/src/main/java/games/cubi/raycastedantiesp/paper/RaycastedAntiESP.java",
    '''            ownCritical(startup, teardownSafe,
                    new PaperPacketEventsBlockViewController(blockInfoResolver, trackAllBlocks, currentTickSupplier));
''',
    '''            ownCritical(startup, teardownSafe,
                    new PaperPacketEventsBlockViewController(
                            blockInfoResolver, trackAllBlocks, currentTickSupplier, visibilityExemptionPolicy));
'''
)
replace_once(
    "platform-paper/src/main/java/games/cubi/raycastedantiesp/paper/RaycastedAntiESP.java",
    '''    private WorldGuardVisibilityExemption initialiseWorldGuardVisibilityExemption() {
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") == null) {
            return null;
        }
        return WorldGuardVisibilityExemption.register();
    }
''',
    '''    private PaperVisibilityExemptionPolicy initialiseWorldGuardVisibilityExemption() {
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") == null) {
            return PaperVisibilityExemptionPolicy.DISABLED;
        }
        // Keep this direct optional-dependency reference inside the guarded method body. WorldGuard's
        // own integration guidance warns against exposing optional types in fields or method descriptors.
        return WorldGuardVisibilityExemption.register();
    }
'''
)
# Remove now-unused core policy import from the main plugin.
replace_once(
    "platform-paper/src/main/java/games/cubi/raycastedantiesp/paper/RaycastedAntiESP.java",
    "import games.cubi.raycastedantiesp.core.policy.VisibilityExemptionPolicy;\n",
    ""
)

# Block packet controller and chunk parser gain the target-location policy so exempt block entities
# are never replaced by stone/deepslate on initial chunk or block updates.
p = Path("packetevents/src/main/java/games/cubi/raycastedantiesp/packetevents/viewcontrollers/PacketEventsBlockViewController.java")
text = p.read_text()
text = text.replace(
    "import games.cubi.raycastedantiesp.core.players.PlayerRegistry;\n",
    "import games.cubi.raycastedantiesp.core.players.PlayerRegistry;\nimport games.cubi.raycastedantiesp.core.policy.VisibilityExemptionPolicy;\n"
)
text = text.replace("import java.util.UUID;\n", "import java.util.Objects;\nimport java.util.UUID;\n")
text = text.replace(
    '''    private final BlockTransitionRetryQueue transitionRetries = new BlockTransitionRetryQueue();
''',
    '''    private final BlockTransitionRetryQueue transitionRetries = new BlockTransitionRetryQueue();
    private final VisibilityExemptionPolicy visibilityExemptionPolicy;
'''
)
text = text.replace(
    '''    protected PacketEventsBlockViewController(BlockInfoResolver blockInfoResolver, boolean trackAllBlocks,
            IntSupplier currentTickSupplier) {
        this.blockInfoResolver = blockInfoResolver;
        this.targetFilter = blockInfoResolver instanceof PacketEventsTargetFilter filter
                ? filter
                : PacketEventsTargetFilter.DISABLED;
        this.currentTickSupplier = currentTickSupplier;
        common = PacketEventsCommonViewController.get(currentTickSupplier);
        if (trackAllBlocks) {
            mutatingChunkParser = new BlockChunkParser(blockInfoResolver, this::getHiddenBlockId);
            nonMutatingChunkParser = new NonMutatingBlockChunkParser(blockInfoResolver, this::getHiddenBlockId);
        } else {
            mutatingChunkParser = new OcclusionChunkParser(blockInfoResolver, this::getHiddenBlockId);
            nonMutatingChunkParser = new NonMutatingOcclusionChunkParser(blockInfoResolver, this::getHiddenBlockId);
        }
    }
''',
    '''    protected PacketEventsBlockViewController(BlockInfoResolver blockInfoResolver, boolean trackAllBlocks,
            IntSupplier currentTickSupplier) {
        this(blockInfoResolver, trackAllBlocks, currentTickSupplier, VisibilityExemptionPolicy.DISABLED);
    }

    protected PacketEventsBlockViewController(BlockInfoResolver blockInfoResolver, boolean trackAllBlocks,
            IntSupplier currentTickSupplier, VisibilityExemptionPolicy visibilityExemptionPolicy) {
        this.blockInfoResolver = blockInfoResolver;
        this.targetFilter = blockInfoResolver instanceof PacketEventsTargetFilter filter
                ? filter
                : PacketEventsTargetFilter.DISABLED;
        this.currentTickSupplier = currentTickSupplier;
        this.visibilityExemptionPolicy = Objects.requireNonNull(
                visibilityExemptionPolicy, "visibilityExemptionPolicy");
        common = PacketEventsCommonViewController.get(currentTickSupplier);
        if (trackAllBlocks) {
            mutatingChunkParser = new BlockChunkParser(
                    blockInfoResolver, this::getHiddenBlockId, visibilityExemptionPolicy);
            nonMutatingChunkParser = new NonMutatingBlockChunkParser(blockInfoResolver, this::getHiddenBlockId);
        } else {
            mutatingChunkParser = new OcclusionChunkParser(
                    blockInfoResolver, this::getHiddenBlockId, visibilityExemptionPolicy);
            nonMutatingChunkParser = new NonMutatingOcclusionChunkParser(blockInfoResolver, this::getHiddenBlockId);
        }
    }
'''
)
old = '''        ensureTileReplayData(tileEntity).setBlockEntityData(packet.getBlockEntityType(), packet.getNBT());
        if (tileChecksEnabled && !blockView.isVisible(world, position, currentTick)) {
            event.setCancelled(true);
            processInitialTileEntityOperationSafely(playerData, viewer, Operation.HIDE, tileEntity,
                    blockView.tileEntityCheckModeToken(), currentTick, Stage.BLOCK,
                    playerData.acquireWorldEpoch());
        }
'''
new = '''        boolean exempt = tileChecksEnabled && isVisibilityExempt(world, position);
        updateTileExemptionState(tileEntity, exempt);
        ensureTileReplayData(tileEntity).setBlockEntityData(packet.getBlockEntityType(), packet.getNBT());
        if (tileChecksEnabled && exempt) {
            if (!tileEntity.visible()) {
                blockView.recordOutboundTileEntityVisibility(tileEntity, true);
                event.setCancelled(true);
                processInitialTileEntityOperationSafely(playerData, viewer, Operation.SHOW, tileEntity,
                        blockView.tileEntityCheckModeToken(), currentTick, Stage.BLOCK,
                        playerData.acquireWorldEpoch());
            }
            return;
        }
        if (tileChecksEnabled && !blockView.isVisible(world, position, currentTick)) {
            event.setCancelled(true);
            processInitialTileEntityOperationSafely(playerData, viewer, Operation.HIDE, tileEntity,
                    blockView.tileEntityCheckModeToken(), currentTick, Stage.BLOCK,
                    playerData.acquireWorldEpoch());
        }
'''
if old not in text: raise RuntimeError("block entity data patch missing")
text = text.replace(old, new, 1)
old = '''        BlockView.BlockEntityStatus blockStatus = blockView.getBlockEntityStatus(world, position);
        boolean packetTypeManaged = targetFilter.shouldCullBlockEntity(packet.getBlockEntityType());
'''
new = '''        if (tileChecksEnabled && isVisibilityExempt(world, position)) {
            return;
        }
        BlockView.BlockEntityStatus blockStatus = blockView.getBlockEntityStatus(world, position);
        boolean packetTypeManaged = targetFilter.shouldCullBlockEntity(packet.getBlockEntityType());
'''
if old not in text: raise RuntimeError("unknown block entity patch missing")
text = text.replace(old, new, 1)
old = '''            if (tileEntity) {
                boolean visibleIfNew = !tileChecksEnabled || visibleIfNew(key, playerLocation, world);
                TrackedTileEntity<?> state =
                        blockView.updateOrInsertTileEntity(world, key, blockID, visibleIfNew);
                if (!tileChecksEnabled) {
                    blockView.recordOutboundTileEntityVisibility(state, true);
                } else if (state != null && !state.visible()) {
'''
new = '''            if (tileEntity) {
                boolean exempt = tileChecksEnabled && isVisibilityExempt(world, key);
                boolean visibleIfNew = !tileChecksEnabled || exempt || visibleIfNew(key, playerLocation, world);
                TrackedTileEntity<?> state =
                        blockView.updateOrInsertTileEntity(world, key, blockID, visibleIfNew);
                updateTileExemptionState(state, exempt);
                if (!tileChecksEnabled || exempt) {
                    blockView.recordOutboundTileEntityVisibility(state, true);
                } else if (state != null && !state.visible()) {
'''
if old not in text: raise RuntimeError("multi block patch missing")
text = text.replace(old, new, 1)
old = '''        if (tileEntity) {
            boolean visibleIfNew = !tileChecksEnabled || visibleIfNew(location, playerData.ownLocation(), world);
            TrackedTileEntity<?> state =
                    blockView.updateOrInsertTileEntity(world, location, blockID, visibleIfNew);
            if (!tileChecksEnabled) {
                blockView.recordOutboundTileEntityVisibility(state, true);
            } else if (state != null && !state.visible()) {
'''
new = '''        if (tileEntity) {
            boolean exempt = tileChecksEnabled && isVisibilityExempt(world, location);
            boolean visibleIfNew = !tileChecksEnabled || exempt
                    || visibleIfNew(location, playerData.ownLocation(), world);
            TrackedTileEntity<?> state =
                    blockView.updateOrInsertTileEntity(world, location, blockID, visibleIfNew);
            updateTileExemptionState(state, exempt);
            if (!tileChecksEnabled || exempt) {
                blockView.recordOutboundTileEntityVisibility(state, true);
            } else if (state != null && !state.visible()) {
'''
if old not in text: raise RuntimeError("single block patch missing")
text = text.replace(old, new, 1)
marker = '''    private boolean visibleIfNew(BlockSpatial location, Locatable playerLocation, UUID packetWorld) {
'''
helper = '''    private boolean isVisibilityExempt(UUID world, BlockSpatial position) {
        return visibilityExemptionPolicy.isExempt(
                world,
                position.blockX() + 0.5,
                position.blockY() + 0.5,
                position.blockZ() + 0.5);
    }

    private static void updateTileExemptionState(TrackedTileEntity<?> tileEntity, boolean exempt) {
        if (tileEntity == null) {
            return;
        }
        boolean wasExempt = tileEntity.visibilityExempt();
        tileEntity.setVisibilityExempt(exempt);
        if (wasExempt && !exempt) {
            tileEntity.setLastChecked(TrackedTileEntity.NEVER_CHECKED);
        }
    }

'''
if marker not in text: raise RuntimeError("block helper marker missing")
text = text.replace(marker, helper + marker, 1)
p.write_text(text)

# Chunk parser policy wiring.
p = Path("packetevents/src/main/java/games/cubi/raycastedantiesp/packetevents/viewcontrollers/chunkparser/AbstractChunkParser.java")
text = p.read_text()
text = text.replace(
    "import games.cubi.raycastedantiesp.core.chunks.OccludingChunkDataImpl;\n",
    "import games.cubi.raycastedantiesp.core.chunks.OccludingChunkDataImpl;\nimport games.cubi.raycastedantiesp.core.policy.VisibilityExemptionPolicy;\n"
)
text = text.replace("import java.util.Arrays;\n", "import java.util.Arrays;\nimport java.util.Objects;\n")
text = text.replace(
    '''    private final IntUnaryOperator hiddenBlockID;
''',
    '''    private final IntUnaryOperator hiddenBlockID;
    private final VisibilityExemptionPolicy visibilityExemptionPolicy;
'''
)
text = text.replace(
    '''    protected AbstractChunkParser(BlockInfoResolver blockInfoResolver, boolean mutatePackets,
            IntUnaryOperator hiddenBlockID) {
        this.blockInfoResolver = blockInfoResolver;
        this.mutatePackets = mutatePackets;
        this.hiddenBlockID = hiddenBlockID;
''',
    '''    protected AbstractChunkParser(BlockInfoResolver blockInfoResolver, boolean mutatePackets,
            IntUnaryOperator hiddenBlockID) {
        this(blockInfoResolver, mutatePackets, hiddenBlockID, VisibilityExemptionPolicy.DISABLED);
    }

    protected AbstractChunkParser(BlockInfoResolver blockInfoResolver, boolean mutatePackets,
            IntUnaryOperator hiddenBlockID, VisibilityExemptionPolicy visibilityExemptionPolicy) {
        this.blockInfoResolver = blockInfoResolver;
        this.mutatePackets = mutatePackets;
        this.hiddenBlockID = hiddenBlockID;
        this.visibilityExemptionPolicy = Objects.requireNonNull(
                visibilityExemptionPolicy, "visibilityExemptionPolicy");
'''
)
old = '''                            TrackedTileEntity<?> state = blockView.updateOrInsertTileEntity(
                                    world, key, blockID, !mutatePackets);
                            if (!mutatePackets) {
                                blockView.recordOutboundTileEntityVisibility(state, true);
                            } else if (state != null && !state.visible()) {
                                section.set(localX, localY, localZ, hiddenBlockID.applyAsInt(blockY));
                                mutatedBlock = true;
                            }
'''
new = '''                            boolean exempt = mutatePackets && visibilityExemptionPolicy.isExempt(
                                    world, blockX + 0.5, blockY + 0.5, blockZ + 0.5);
                            TrackedTileEntity<?> state = blockView.updateOrInsertTileEntity(
                                    world, key, blockID, !mutatePackets || exempt);
                            updateExemptionState(state, exempt);
                            if (!mutatePackets || exempt) {
                                blockView.recordOutboundTileEntityVisibility(state, true);
                            } else if (state != null && !state.visible()) {
                                section.set(localX, localY, localZ, hiddenBlockID.applyAsInt(blockY));
                                mutatedBlock = true;
                            }
'''
if old not in text: raise RuntimeError("chunk managed tile patch missing")
text = text.replace(old, new, 1)
marker = '''    private boolean sectionMayContainManagedTiles(Chunk_v1_18 section) {
'''
helper = '''    private static void updateExemptionState(TrackedTileEntity<?> state, boolean exempt) {
        if (state == null) {
            return;
        }
        boolean wasExempt = state.visibilityExempt();
        state.setVisibilityExempt(exempt);
        if (wasExempt && !exempt) {
            state.setLastChecked(TrackedTileEntity.NEVER_CHECKED);
        }
    }

'''
if marker not in text: raise RuntimeError("chunk helper marker missing")
text = text.replace(marker, helper + marker, 1)
p.write_text(text)

# Constructor overloads preserve existing callers/tests while letting the mutating parser query exemptions.
write("packetevents/src/main/java/games/cubi/raycastedantiesp/packetevents/viewcontrollers/chunkparser/BlockChunkParser.java", '''package games.cubi.raycastedantiesp.packetevents.viewcontrollers.chunkparser;

import com.github.retrooper.packetevents.protocol.world.chunk.impl.v_1_18.Chunk_v1_18;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.DataPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.GlobalPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.Palette;
import games.cubi.raycastedantiesp.core.chunks.BlockChunkData;
import games.cubi.raycastedantiesp.core.chunks.BlockInfoResolver;
import games.cubi.raycastedantiesp.core.chunks.ChunkData;
import games.cubi.raycastedantiesp.core.policy.VisibilityExemptionPolicy;
import games.cubi.raycastedantiesp.core.view.BlockView;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.IntUnaryOperator;

public class BlockChunkParser extends AbstractChunkParser<BlockChunkData> {
    public BlockChunkParser(BlockInfoResolver blockInfoResolver, IntUnaryOperator hiddenBlockID) {
        this(blockInfoResolver, hiddenBlockID, VisibilityExemptionPolicy.DISABLED);
    }

    public BlockChunkParser(BlockInfoResolver blockInfoResolver, IntUnaryOperator hiddenBlockID,
            VisibilityExemptionPolicy visibilityExemptionPolicy) {
        this(blockInfoResolver, true, hiddenBlockID, visibilityExemptionPolicy);
    }

    protected BlockChunkParser(BlockInfoResolver blockInfoResolver, boolean mutatePackets,
            IntUnaryOperator hiddenBlockID, VisibilityExemptionPolicy visibilityExemptionPolicy) {
        super(blockInfoResolver, mutatePackets, hiddenBlockID, visibilityExemptionPolicy);
    }

    @Override
    protected @Nullable BlockChunkData parseSection(Chunk_v1_18 section) {
        if (section.isEmpty()) {
            return null;
        }
        DataPalette data = section.getChunkData();
        Palette palette = data.palette;
        if (!(palette instanceof GlobalPalette)) {
            int paletteSize = palette.size();
            char[] states = new char[paletteSize];
            for (int index = 0; index < paletteSize; index++) {
                states[index] = checkedBlockID(palette.idToState(index));
            }
            if (paletteSize == 1) {
                return states[0] == 0 ? null : BlockChunkData.filled(states[0], blockInfoResolver);
            }
            return BlockChunkData.copyOfPalette(states, packed -> data.storage.get(toPacketEventsIndex(packed)), blockInfoResolver);
        }
        return BlockChunkData.copyOfStates(packed -> {
            int x = ChunkData.unpackX(packed);
            int y = ChunkData.unpackY(packed);
            int z = ChunkData.unpackZ(packed);
            return section.getBlockId(x, y, z);
        }, blockInfoResolver);
    }

    @Override
    protected void storeSection(BlockView blockView, UUID world, int chunkX, int sectionY, int chunkZ, BlockChunkData data) {
        blockView.replaceChunkSection(world, chunkX, sectionY, chunkZ, data);
    }

    private static int toPacketEventsIndex(int packed) {
        int x = packed & ChunkData.LOCAL_MASK;
        int y = packed >> 4 & ChunkData.LOCAL_MASK;
        int z = packed >> 8 & ChunkData.LOCAL_MASK;
        // PacketEvents packs y in the high nibble group, z in the middle, and x in the low nibble.
        return y << 8 | z << 4 | x;
    }

    private static char checkedBlockID(int blockID) {
        if (blockID < 0 || blockID > Character.MAX_VALUE) {
            throw new IllegalArgumentException("blockID must fit in an unsigned 16-bit value, but was " + blockID);
        }
        return (char) blockID;
    }
}
''')

write("packetevents/src/main/java/games/cubi/raycastedantiesp/packetevents/viewcontrollers/chunkparser/NonMutatingBlockChunkParser.java", '''package games.cubi.raycastedantiesp.packetevents.viewcontrollers.chunkparser;

import games.cubi.raycastedantiesp.core.chunks.BlockInfoResolver;
import games.cubi.raycastedantiesp.core.policy.VisibilityExemptionPolicy;

import java.util.function.IntUnaryOperator;

public final class NonMutatingBlockChunkParser extends BlockChunkParser {
    public NonMutatingBlockChunkParser(BlockInfoResolver blockInfoResolver, IntUnaryOperator hiddenBlockID) {
        super(blockInfoResolver, false, hiddenBlockID, VisibilityExemptionPolicy.DISABLED);
    }
}
''')

p = Path("packetevents/src/main/java/games/cubi/raycastedantiesp/packetevents/viewcontrollers/chunkparser/OcclusionChunkParser.java")
text = p.read_text()
text = text.replace(
    "import games.cubi.raycastedantiesp.core.chunks.OccludingChunkDataImpl;\n",
    "import games.cubi.raycastedantiesp.core.chunks.OccludingChunkDataImpl;\nimport games.cubi.raycastedantiesp.core.policy.VisibilityExemptionPolicy;\n"
)
text = text.replace(
    '''    public OcclusionChunkParser(BlockInfoResolver blockInfoResolver, IntUnaryOperator hiddenBlockID) {
        this(blockInfoResolver, true, hiddenBlockID);
    }

    protected OcclusionChunkParser(BlockInfoResolver blockInfoResolver, boolean mutatePackets, IntUnaryOperator hiddenBlockID) {
        super(blockInfoResolver, mutatePackets, hiddenBlockID);
    }
''',
    '''    public OcclusionChunkParser(BlockInfoResolver blockInfoResolver, IntUnaryOperator hiddenBlockID) {
        this(blockInfoResolver, hiddenBlockID, VisibilityExemptionPolicy.DISABLED);
    }

    public OcclusionChunkParser(BlockInfoResolver blockInfoResolver, IntUnaryOperator hiddenBlockID,
            VisibilityExemptionPolicy visibilityExemptionPolicy) {
        this(blockInfoResolver, true, hiddenBlockID, visibilityExemptionPolicy);
    }

    protected OcclusionChunkParser(BlockInfoResolver blockInfoResolver, boolean mutatePackets,
            IntUnaryOperator hiddenBlockID, VisibilityExemptionPolicy visibilityExemptionPolicy) {
        super(blockInfoResolver, mutatePackets, hiddenBlockID, visibilityExemptionPolicy);
    }
'''
)
p.write_text(text)

write("packetevents/src/main/java/games/cubi/raycastedantiesp/packetevents/viewcontrollers/chunkparser/NonMutatingOcclusionChunkParser.java", '''package games.cubi.raycastedantiesp.packetevents.viewcontrollers.chunkparser;

import games.cubi.raycastedantiesp.core.chunks.BlockInfoResolver;
import games.cubi.raycastedantiesp.core.policy.VisibilityExemptionPolicy;

import java.util.function.IntUnaryOperator;

public final class NonMutatingOcclusionChunkParser extends OcclusionChunkParser {
    public NonMutatingOcclusionChunkParser(BlockInfoResolver blockInfoResolver, IntUnaryOperator hiddenBlockID) {
        super(blockInfoResolver, false, hiddenBlockID, VisibilityExemptionPolicy.DISABLED);
    }
}
''')

# Paper block controller constructor carries the policy into packet/chunk handling.
p = Path("platform-paper/src/main/java/games/cubi/raycastedantiesp/paper/packets/PaperPacketEventsBlockViewController.java")
text = p.read_text()
text = text.replace(
    "import games.cubi.raycastedantiesp.core.chunks.BlockInfoResolver;\n",
    "import games.cubi.raycastedantiesp.core.chunks.BlockInfoResolver;\nimport games.cubi.raycastedantiesp.core.policy.VisibilityExemptionPolicy;\n"
)
text = text.replace(
    '''    public PaperPacketEventsBlockViewController(BlockInfoResolver blockInfoResolver, boolean trackAllBlocks, IntSupplier currentTickSupplier) {
        super(blockInfoResolver, trackAllBlocks, currentTickSupplier);
''',
    '''    public PaperPacketEventsBlockViewController(BlockInfoResolver blockInfoResolver, boolean trackAllBlocks,
            IntSupplier currentTickSupplier) {
        this(blockInfoResolver, trackAllBlocks, currentTickSupplier, VisibilityExemptionPolicy.DISABLED);
    }

    public PaperPacketEventsBlockViewController(BlockInfoResolver blockInfoResolver, boolean trackAllBlocks,
            IntSupplier currentTickSupplier, VisibilityExemptionPolicy visibilityExemptionPolicy) {
        super(blockInfoResolver, trackAllBlocks, currentTickSupplier, visibilityExemptionPolicy);
'''
)
p.write_text(text)

# Tests: policy recheck cap.
write("core/src/test/java/games/cubi/raycastedantiesp/core/policy/VisibilityExemptionPolicyTest.java", '''package games.cubi.raycastedantiesp.core.policy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VisibilityExemptionPolicyTest {
    private static final VisibilityExemptionPolicy ACTIVE = (world, x, y, z) -> false;

    @Test
    void activePolicyCapsSlowOrDisabledVisibleRechecks() {
        assertEquals(20, ACTIVE.effectiveVisibleRecheckTicks(-1));
        assertEquals(20, ACTIVE.effectiveVisibleRecheckTicks(200));
        assertEquals(5, ACTIVE.effectiveVisibleRecheckTicks(5));
        assertEquals(0, ACTIVE.effectiveVisibleRecheckTicks(0));
    }

    @Test
    void disabledPolicyPreservesConfiguredRecheck() {
        assertEquals(-1, VisibilityExemptionPolicy.DISABLED.effectiveVisibleRecheckTicks(-1));
        assertEquals(200, VisibilityExemptionPolicy.DISABLED.effectiveVisibleRecheckTicks(200));
    }
}
''')

# Movement boundary regression harness.
p = Path("core/src/test/java/games/cubi/raycastedantiesp/core/view/controller/PacketEntityViewControllerTest.java")
text = p.read_text()
text = text.replace(
    "import games.cubi.raycastedantiesp.core.players.PlayerRegistry;\n",
    "import games.cubi.raycastedantiesp.core.players.PlayerRegistry;\nimport games.cubi.raycastedantiesp.core.policy.VisibilityExemptionPolicy;\n"
)
text = text.replace(
    '''        CONTROLLER.directlyShownEntityIDs.clear();
        CONTROLLER.replacementPassengerPackets.clear();
''',
    '''        CONTROLLER.directlyShownEntityIDs.clear();
        CONTROLLER.directlyHiddenEntityIDs.clear();
        CONTROLLER.replacementPassengerPackets.clear();
'''
)
insert_marker = '''    @Test
    void bypassedVehicleFiltersHiddenManagedPassengerWithoutReveal() {
'''
new_tests = '''    @Test
    void hiddenEntityEnteringExemptRegionSuppressesMovementAfterDirectShow() {
        UUID world = UUID.randomUUID();
        PlayerData playerData = registerPlayer(world);
        playerData.updateOwnLocation(world, 0, 64, 0);
        HarnessEntity entity = insertPassenger(playerData, world, 2, false, false);
        entity.setPosition(10, 64, 0);
        HarnessController controller = new HarnessController((ignoredWorld, x, y, z) -> x >= 10);
        controller.movementEntityID = 2;

        boolean cancelled = controller.handleRelativeMove(null, playerData, 20);

        assertTrue(cancelled, "the triggering relative move must not be applied after a direct spawn at the new position");
        assertTrue(entity.visibilityExempt());
        assertTrue(entity.visible());
        assertTrue(entity.clientVisible());
        assertEquals(List.of(2), controller.directlyShownEntityIDs);
    }

    @Test
    void visibleEntityEnteringExemptRegionCanKeepItsMovementPacket() {
        UUID world = UUID.randomUUID();
        PlayerData playerData = registerPlayer(world);
        playerData.updateOwnLocation(world, 0, 64, 0);
        HarnessEntity entity = insertPassenger(playerData, world, 2, true, true);
        entity.setPosition(10, 64, 0);
        HarnessController controller = new HarnessController((ignoredWorld, x, y, z) -> x >= 10);
        controller.movementEntityID = 2;

        boolean cancelled = controller.handleRelativeMove(null, playerData, 20);

        assertFalse(cancelled);
        assertTrue(entity.visibilityExempt());
        assertTrue(controller.directlyShownEntityIDs.isEmpty());
    }

    @Test
    void entityLeavingExemptRegionIsHiddenBeforeMovementIsForwarded() {
        UUID world = UUID.randomUUID();
        PlayerData playerData = registerPlayer(world);
        playerData.updateOwnLocation(world, 0, 64, 0);
        HarnessEntity entity = insertPassenger(playerData, world, 2, true, true);
        entity.setVisibilityExempt(true);
        entity.setPosition(0, 64, 0);
        HarnessController controller = new HarnessController((ignoredWorld, x, y, z) -> x >= 10);
        controller.movementEntityID = 2;

        boolean cancelled = controller.handleRelativeMove(null, playerData, 20);

        assertTrue(cancelled);
        assertFalse(entity.visibilityExempt());
        assertFalse(entity.visible());
        assertFalse(entity.clientVisible());
        assertEquals(List.of(2), controller.directlyHiddenEntityIDs);
    }

'''
if insert_marker not in text: raise RuntimeError("controller test marker missing")
text = text.replace(insert_marker, new_tests + insert_marker, 1)
text = text.replace(
    '''    private static final class HarnessController extends PacketEntityViewController<Void> {
        private final List<Integer> directlyShownEntityIDs = new ArrayList<>();
        private final List<int[]> replacementPassengerPackets = new ArrayList<>();
''',
    '''    private static final class HarnessController extends PacketEntityViewController<Void> {
        private final List<Integer> directlyShownEntityIDs = new ArrayList<>();
        private final List<Integer> directlyHiddenEntityIDs = new ArrayList<>();
        private final List<int[]> replacementPassengerPackets = new ArrayList<>();
        private int movementEntityID = -1;

        private HarnessController() {
            super();
        }

        private HarnessController(VisibilityExemptionPolicy visibilityExemptionPolicy) {
            super(visibilityExemptionPolicy);
        }
'''
)
text = text.replace(
    '''        protected void processDirectEntityHide(PlayerData playerData, EntityView<?> view, NettyEntity<?> entity, int worldEpoch) {
            entity.setClientVisible(false);
        }
''',
    '''        protected void processDirectEntityHide(PlayerData playerData, EntityView<?> view, NettyEntity<?> entity, int worldEpoch) {
            directlyHiddenEntityIDs.add(entity.entityID());
            entity.setClientVisible(false);
        }
'''
)
text = text.replace(
    '''        protected int processRelativeMovePacket(Void packet, PlayerData playerData, int currentTick) {
            return -1;
        }
''',
    '''        protected int processRelativeMovePacket(Void packet, PlayerData playerData, int currentTick) {
            return movementEntityID;
        }
'''
)
p.write_text(text)

# Chunk parser regression: exempt managed tile must stay real and visible in the initial chunk.
p = Path("packetevents/src/test/java/games/cubi/raycastedantiesp/packetevents/viewcontrollers/chunkparser/ChunkParserTest.java")
text = p.read_text()
text = text.replace(
    "import games.cubi.raycastedantiesp.core.chunks.BlockInfoResolver;\n",
    "import games.cubi.raycastedantiesp.core.chunks.BlockInfoResolver;\nimport games.cubi.raycastedantiesp.core.policy.VisibilityExemptionPolicy;\n"
)
marker = '''    @Test
    void packetEventsAndCorePackingKeepYAndZDistinct() {
'''
new_test = '''    @Test
    void mutatingParserLeavesExemptManagedTileRealAndVisible() {
        UUID world = UUID.randomUUID();
        Chunk_v1_18 section = airSection();
        section.set(3, 2, 1, 99);
        TileEntity tileEntity = new TileEntity((byte) (3 << 4 | 1), (short) 2, 0, null);
        Column column = new Column(0, 0, true, new BaseChunk[]{section}, new TileEntity[]{tileEntity});
        PacketEventsBlockView view = new PacketEventsBlockView(RESOLVER, true, STABLE_WORLD_EPOCH);
        view.applyTileEntityCheckMode(true, 0, unused -> {});
        VisibilityExemptionPolicy policy = (worldId, x, y, z) -> world.equals(worldId)
                && x >= 3 && x < 4 && y >= 2 && y < 3 && z >= 1 && z < 2;

        Column replacement = new BlockChunkParser(RESOLVER, ignored -> 1, policy)
                .parse(view, world, column, 0);

        assertNull(replacement);
        assertEquals(99, section.getBlockId(3, 2, 1));
        assertEquals(1, column.getTileEntities().length);
        TrackedTileEntity<?> tracked = view.getTrackedTileEntity(
                world, new ImmutableBlockSpatialImpl(3, 2, 1));
        assertNotNull(tracked);
        assertTrue(tracked.visible());
        assertTrue(tracked.visibilityExempt());
    }

'''
if marker not in text: raise RuntimeError("chunk test marker missing")
text = text.replace(marker, new_test + marker, 1)
p.write_text(text)

# Optional dependency class-loading guard. WorldGuard is compileOnly and intentionally absent from test runtime.
write("platform-paper/src/test/java/games/cubi/raycastedantiesp/paper/OptionalWorldGuardDependencyTest.java", '''package games.cubi.raycastedantiesp.paper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OptionalWorldGuardDependencyTest {
    @Test
    void mainPluginClassLoadsWhenWorldGuardIsAbsent() throws Exception {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.sk89q.worldguard.WorldGuard", false, loader));
        assertNotNull(Class.forName(
                "games.cubi.raycastedantiesp.paper.RaycastedAntiESP", false, loader));
    }
}
''')

print("WorldGuard review fixes applied")
