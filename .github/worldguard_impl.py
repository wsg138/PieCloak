from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected exactly one match, found {count}\nOLD:\n{old}")
    file.write_text(text.replace(old, new, 1))


# Core policy abstraction: keeps WorldGuard out of core/PacketEvents modules.
policy = Path("core/src/main/java/games/cubi/raycastedantiesp/core/policy/VisibilityExemptionPolicy.java")
policy.parent.mkdir(parents=True, exist_ok=True)
policy.write_text('''package games.cubi.raycastedantiesp.core.policy;

import games.cubi.locatables.api.Locatable;

import java.util.UUID;

/**
 * Target-location policy which can opt a position out of normal visibility hiding.
 * Implementations must be safe for concurrent packet-thread and engine-thread queries.
 */
@FunctionalInterface
public interface VisibilityExemptionPolicy {
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
}
''')

# Bukkit/WorldGuard adapter. World objects are adapted only on the server thread; hot-path
# queries do not call Bukkit APIs from Netty/the async visibility engine.
integration = Path("platform-paper/src/main/java/games/cubi/raycastedantiesp/paper/integrations/WorldGuardVisibilityExemption.java")
integration.parent.mkdir(parents=True, exist_ok=True)
integration.write_text('''package games.cubi.raycastedantiesp.paper.integrations;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.association.RegionAssociable;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.FlagConflictException;
import com.sk89q.worldguard.protection.flags.FlagRegistry;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import games.cubi.logs.Logger;
import games.cubi.raycastedantiesp.core.policy.VisibilityExemptionPolicy;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Optional WorldGuard-backed target-location visibility exemption. */
public final class WorldGuardVisibilityExemption implements VisibilityExemptionPolicy, Listener {
    public static final String FLAG_NAME = "piecloak-skip";
    private static final int MAX_QUERY_FAILURE_DIAGNOSTICS = 5;

    private final StateFlag flag;
    private final RegionContainer regionContainer;
    private final Map<UUID, com.sk89q.worldedit.world.World> worlds = new ConcurrentHashMap<>();
    private final AtomicInteger queryFailures = new AtomicInteger();

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

    /** Must be called on the server thread during plugin enable. */
    public void enable(JavaPlugin plugin) {
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            rememberWorld(world);
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
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
            return query.testState(location, (RegionAssociable) null, flag);
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

build = "platform-paper/build.gradle.kts"
replace_once(
    build,
    '    maven("https://repo.fancyinnovations.com/releases")\n',
    '    maven("https://repo.fancyinnovations.com/releases")\n    maven { url = uri("https://maven.enginehub.org/repo/") }\n'
)
replace_once(
    build,
    '    compileOnly("de.oliver:FancyNpcs:2.9.2")\n',
    '    compileOnly("de.oliver:FancyNpcs:2.9.2")\n    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.16")\n'
)

plugin = "platform-paper/src/main/resources/plugin.yml"
replace_once(plugin, '    - FancyHolograms\n', '    - FancyHolograms\n    - WorldGuard\n')

tracked_entity = "core/src/main/java/games/cubi/raycastedantiesp/core/tracked/TrackedEntity.java"
replace_once(
    tracked_entity,
    '    boolean sneaking();\n\n    int lastChecked();',
    '    boolean sneaking();\n\n    /** Whether a target-location exemption currently overrides normal hiding for this entity. */\n    boolean visibilityExempt();\n    TrackedEntity<?> setVisibilityExempt(boolean visibilityExempt);\n\n    int lastChecked();'
)

netty_entity = "core/src/main/java/games/cubi/raycastedantiesp/core/tracked/NettyEntity.java"
replace_once(
    netty_entity,
    '    private volatile byte trackedFlags; private static final VarHandle TRACKED_FLAGS = VarHandler.get(NettyEntity.class, "trackedFlags", byte.class);\n',
    '    private volatile byte trackedFlags; private static final VarHandle TRACKED_FLAGS = VarHandler.get(NettyEntity.class, "trackedFlags", byte.class);\n    private volatile boolean visibilityExempt;\n'
)
replace_once(
    netty_entity,
    '    @Override\n    public boolean sneaking() {\n        return (((byte) TRACKED_FLAGS.getOpaque(this)) & FLAG_SNEAKING) != 0;\n    }\n\n    public boolean setGlowing(boolean glowing) {',
    '    @Override\n    public boolean sneaking() {\n        return (((byte) TRACKED_FLAGS.getOpaque(this)) & FLAG_SNEAKING) != 0;\n    }\n\n    @Override\n    public boolean visibilityExempt() {\n        return visibilityExempt;\n    }\n\n    @Override\n    public TrackedEntity<?> setVisibilityExempt(boolean visibilityExempt) {\n        this.visibilityExempt = visibilityExempt;\n        return this;\n    }\n\n    public boolean setGlowing(boolean glowing) {'
)

tracked_tile = "core/src/main/java/games/cubi/raycastedantiesp/core/tracked/TrackedTileEntity.java"
replace_once(
    tracked_tile,
    '    boolean visible();\n    TrackedTileEntity<T> setVisible(boolean visible);\n\n    int lastChecked();',
    '    boolean visible();\n    TrackedTileEntity<T> setVisible(boolean visible);\n\n    /** Whether a target-location exemption currently overrides normal hiding for this tile entity. */\n    boolean visibilityExempt();\n    TrackedTileEntity<T> setVisibilityExempt(boolean visibilityExempt);\n\n    int lastChecked();'
)

netty_tile = "core/src/main/java/games/cubi/raycastedantiesp/core/tracked/NettyTileEntity.java"
replace_once(netty_tile, '    private volatile boolean visible;\n', '    private volatile boolean visible;\n    private volatile boolean visibilityExempt;\n')
replace_once(
    netty_tile,
    '    @Override\n    public TrackedTileEntity<PacketReplayData> setVisible(boolean visible) {\n        this.visible = visible;\n        return this;\n    }\n\n    @Override\n    public int lastChecked() {',
    '    @Override\n    public TrackedTileEntity<PacketReplayData> setVisible(boolean visible) {\n        this.visible = visible;\n        return this;\n    }\n\n    @Override\n    public boolean visibilityExempt() {\n        return visibilityExempt;\n    }\n\n    @Override\n    public TrackedTileEntity<PacketReplayData> setVisibilityExempt(boolean visibilityExempt) {\n        this.visibilityExempt = visibilityExempt;\n        return this;\n    }\n\n    @Override\n    public int lastChecked() {'
)

base_controller = "core/src/main/java/games/cubi/raycastedantiesp/core/view/controller/PacketEntityViewController.java"
replace_once(base_controller, 'import games.cubi.raycastedantiesp.core.players.PlayerRegistry;\n', 'import games.cubi.raycastedantiesp.core.players.PlayerRegistry;\nimport games.cubi.raycastedantiesp.core.policy.VisibilityExemptionPolicy;\n')
replace_once(base_controller, 'import java.util.UUID;\n', 'import java.util.Objects;\nimport java.util.UUID;\n')
replace_once(
    base_controller,
    '    protected double hideOnSpawnPlayerDistanceSquared = 0;\n\n    protected void handleWorldStatePacket',
    '    protected double hideOnSpawnPlayerDistanceSquared = 0;\n    private final VisibilityExemptionPolicy visibilityExemptionPolicy;\n\n    protected PacketEntityViewController() {\n        this(VisibilityExemptionPolicy.DISABLED);\n    }\n\n    protected PacketEntityViewController(VisibilityExemptionPolicy visibilityExemptionPolicy) {\n        this.visibilityExemptionPolicy = Objects.requireNonNull(visibilityExemptionPolicy, "visibilityExemptionPolicy");\n    }\n\n    protected void handleWorldStatePacket'
)
replace_once(
    base_controller,
    '    /** Applies a SHOW transition immediately on the Netty thread without publishing it to the engine SPSC queue. */\n    protected abstract void processDirectEntityShow(PlayerData playerData, EntityView<?> view, NettyEntity<?> entity, int worldEpoch);\n',
    '    /** Applies a SHOW transition immediately on the Netty thread without publishing it to the engine SPSC queue. */\n    protected abstract void processDirectEntityShow(PlayerData playerData, EntityView<?> view, NettyEntity<?> entity, int worldEpoch);\n\n    /** Applies a HIDE transition immediately on the Netty thread without publishing it to the engine SPSC queue. */\n    protected abstract void processDirectEntityHide(PlayerData playerData, EntityView<?> view, NettyEntity<?> entity, int worldEpoch);\n'
)
replace_once(
    base_controller,
    '        NettyEntity<?> entity = Logger.requireNonNull(processEntitySpawn(playerData, packet, world, currentTick), "processEntitySpawn returned null", 3, PacketEntityViewController.class);\n        boolean relationshipSupportEntity = EntityBypassRegistry.isRelationshipSupportEntity(entity.entityID());\n\n        if (!relationshipSupportEntity && ((!isPlayer && entityConfig.enabled()) || isPlayer && playerConfig.enabled())) {',
    '        NettyEntity<?> entity = Logger.requireNonNull(processEntitySpawn(playerData, packet, world, currentTick), "processEntitySpawn returned null", 3, PacketEntityViewController.class);\n        boolean exempt = visibilityExemptionPolicy.isExempt(world, entity.x(), entity.y(), entity.z());\n        entity.setVisibilityExempt(exempt);\n        if (exempt) {\n            entity.setVisible(true);\n            entity.setClientVisible(true);\n            if (isPlayer) {\n                insertEntityToPlayerView(entity, playerData, world);\n            } else {\n                insertEntityToEntityView(entity, playerData, world);\n            }\n            return false;\n        }\n        boolean relationshipSupportEntity = EntityBypassRegistry.isRelationshipSupportEntity(entity.entityID());\n\n        if (!relationshipSupportEntity && ((!isPlayer && entityConfig.enabled()) || isPlayer && playerConfig.enabled())) {'
)

for method_name, process_call in [
    ("handleRelativeMove", "processRelativeMovePacket(packet, playerData, currentTick)"),
    ("handleRelativeMoveAndRotation", "processRelativeMoveAndRotationPacket(packet, playerData, currentTick)"),
    ("handleTeleport", "processTeleportPacket(packet, playerData, currentTick)"),
    ("handlePositionSync", "processPositionSyncPacket(packet, playerData, currentTick)"),
]:
    old = f'''    protected boolean {method_name}(P packet, PlayerData playerData, int currentTick) {{
        int entityID = {process_call};
        return cancelIfEnabledAndHidden(entityID, playerData);
    }}'''
    new = f'''    protected boolean {method_name}(P packet, PlayerData playerData, int currentTick) {{
        int entityID = {process_call};
        return reconcileVisibilityExemptionAfterMovement(entityID, playerData, currentTick);
    }}'''
    replace_once(base_controller, old, new)

replace_once(
    base_controller,
    '    protected boolean handlePositionSync(P packet, PlayerData playerData, int currentTick) {\n        int entityID = processPositionSyncPacket(packet, playerData, currentTick);\n        return reconcileVisibilityExemptionAfterMovement(entityID, playerData, currentTick);\n    }\n    /**\n     * @return Whether or not to cancel the packet event. <code>true</code> to cancel, <code>false</code> to do nothing.\n     */\n    protected boolean handleEntityRotation',
    '''    protected boolean handlePositionSync(P packet, PlayerData playerData, int currentTick) {
        int entityID = processPositionSyncPacket(packet, playerData, currentTick);
        return reconcileVisibilityExemptionAfterMovement(entityID, playerData, currentTick);
    }

    private boolean reconcileVisibilityExemptionAfterMovement(
            int entityID, PlayerData playerData, int currentTick) {
        NettyEntity<?> entity = playerData.entityFromID(entityID);
        if (entity == null || entity.isSelfEntity()) {
            return cancelIfEnabledAndHidden(entityID, playerData);
        }
        Locatable viewerLocation = playerData.ownLocation();
        UUID world = viewerLocation == null ? null : viewerLocation.world();
        boolean wasExempt = entity.visibilityExempt();
        boolean exempt = world != null
                && visibilityExemptionPolicy.isExempt(world, entity.x(), entity.y(), entity.z());
        entity.setVisibilityExempt(exempt);

        if (exempt) {
            if (!entity.visible() || !entity.clientVisible()) {
                applyDirectVisibility(playerData, entity, true, currentTick);
            }
            return false;
        }
        if (wasExempt) {
            applyDirectVisibility(playerData, entity, false, currentTick);
            // Fail closed at the boundary. The async engine evaluates normal visibility next tick.
            return true;
        }
        return cancelIfEnabledAndHidden(entityID, playerData);
    }

    private boolean applyDirectVisibility(
            PlayerData playerData, NettyEntity<?> entity, boolean visible, int currentTick) {
        EntityView<?> view = playerData.viewFromEntityID(entity.entityID());
        int worldEpoch = playerData.acquireWorldEpoch();
        if (view == null || !PlayerData.isStableWorldEpoch(worldEpoch)) {
            return false;
        }
        boolean recorded = view.recordDirectVisibility(entity, visible, currentTick, worldEpoch);
        if (!recorded) {
            return false;
        }
        if (visible) {
            processDirectEntityShow(playerData, view, entity, worldEpoch);
        } else {
            processDirectEntityHide(playerData, view, entity, worldEpoch);
        }
        return true;
    }

    /**
     * @return Whether or not to cancel the packet event. <code>true</code> to cancel, <code>false</code> to do nothing.
     */
    protected boolean handleEntityRotation'''
)

pe_controller = "packetevents/src/main/java/games/cubi/raycastedantiesp/packetevents/viewcontrollers/PacketEventsEntityViewController.java"
replace_once(pe_controller, 'import games.cubi.raycastedantiesp.core.players.PlayerRegistry;\n', 'import games.cubi.raycastedantiesp.core.players.PlayerRegistry;\nimport games.cubi.raycastedantiesp.core.policy.VisibilityExemptionPolicy;\n')
replace_once(
    pe_controller,
    '    protected PacketEventsEntityViewController(IntSupplier currentTickSupplier, PacketEventsTargetFilter targetFilter) {\n        this.CURRENT_TICK_SUPPLIER = currentTickSupplier;',
    '    protected PacketEventsEntityViewController(IntSupplier currentTickSupplier, PacketEventsTargetFilter targetFilter) {\n        this(currentTickSupplier, targetFilter, VisibilityExemptionPolicy.DISABLED);\n    }\n\n    protected PacketEventsEntityViewController(\n            IntSupplier currentTickSupplier, PacketEventsTargetFilter targetFilter,\n            VisibilityExemptionPolicy visibilityExemptionPolicy) {\n        super(visibilityExemptionPolicy);\n        this.CURRENT_TICK_SUPPLIER = currentTickSupplier;'
)
replace_once(
    pe_controller,
    '    @Override\n    protected void processDirectEntityShow(PlayerData playerData, EntityView<?> view, NettyEntity<?> entity, int worldEpoch) {\n        Object channel = PacketEvents.getAPI().getProtocolManager().getChannel(playerData.getPlayerUUID());\n        User viewer = PacketEvents.getAPI().getProtocolManager().getUser(channel);\n        beginEntityTransition(\n                playerData,\n                viewer,\n                cast(view),\n                EntityViewTransition.Type.SHOW,\n                entity,\n                worldEpoch,\n                CURRENT_TICK_SUPPLIER.getAsInt()\n        );\n    }',
    '''    @Override
    protected void processDirectEntityShow(PlayerData playerData, EntityView<?> view, NettyEntity<?> entity, int worldEpoch) {
        processDirectEntityVisibility(playerData, view, entity, worldEpoch, EntityViewTransition.Type.SHOW);
    }

    @Override
    protected void processDirectEntityHide(PlayerData playerData, EntityView<?> view, NettyEntity<?> entity, int worldEpoch) {
        processDirectEntityVisibility(playerData, view, entity, worldEpoch, EntityViewTransition.Type.HIDE);
    }

    private void processDirectEntityVisibility(
            PlayerData playerData, EntityView<?> view, NettyEntity<?> entity,
            int worldEpoch, EntityViewTransition.Type type) {
        Object channel = PacketEvents.getAPI().getProtocolManager().getChannel(playerData.getPlayerUUID());
        if (channel == null) {
            return;
        }
        User viewer = PacketEvents.getAPI().getProtocolManager().getUser(channel);
        if (viewer == null) {
            return;
        }
        beginEntityTransition(
                playerData,
                viewer,
                cast(view),
                type,
                entity,
                worldEpoch,
                CURRENT_TICK_SUPPLIER.getAsInt()
        );
    }'''
)

paper_entity_controller = "platform-paper/src/main/java/games/cubi/raycastedantiesp/paper/packets/PaperPacketEventsEntityViewController.java"
replace_once(paper_entity_controller, 'import games.cubi.raycastedantiesp.core.players.WorldEpochGuard;\n', 'import games.cubi.raycastedantiesp.core.players.WorldEpochGuard;\nimport games.cubi.raycastedantiesp.core.policy.VisibilityExemptionPolicy;\n')
replace_once(
    paper_entity_controller,
    '    public static PaperPacketEventsEntityViewController create(\n            IntSupplier currentTickSupplier, PacketEventsTargetFilter targetFilter,\n            Runnable markUnsafeCleanup) {',
    '    public static PaperPacketEventsEntityViewController create(\n            IntSupplier currentTickSupplier, PacketEventsTargetFilter targetFilter,\n            VisibilityExemptionPolicy visibilityExemptionPolicy, Runnable markUnsafeCleanup) {'
)
replace_once(
    paper_entity_controller,
    '                () -> new PaperPacketEventsEntityViewController(\n                        currentTickSupplier, targetFilter, markUnsafeCleanup),',
    '                () -> new PaperPacketEventsEntityViewController(\n                        currentTickSupplier, targetFilter, visibilityExemptionPolicy, markUnsafeCleanup),'
)
replace_once(
    paper_entity_controller,
    '    private PaperPacketEventsEntityViewController(\n            IntSupplier currentTickSupplier, PacketEventsTargetFilter targetFilter,\n            Runnable markUnsafeCleanup) {\n        super(currentTickSupplier, targetFilter);',
    '    private PaperPacketEventsEntityViewController(\n            IntSupplier currentTickSupplier, PacketEventsTargetFilter targetFilter,\n            VisibilityExemptionPolicy visibilityExemptionPolicy, Runnable markUnsafeCleanup) {\n        super(currentTickSupplier, targetFilter, visibilityExemptionPolicy);'
)

visibility_checks = "core/src/main/java/games/cubi/raycastedantiesp/core/engine/AsyncVisibilityChecks.java"
replace_once(visibility_checks, 'import games.cubi.raycastedantiesp.core.players.PlayerData;\n', 'import games.cubi.raycastedantiesp.core.players.PlayerData;\nimport games.cubi.raycastedantiesp.core.policy.VisibilityExemptionPolicy;\n')
replace_once(
    visibility_checks,
    'final class AsyncVisibilityChecks {\n    private final ParticleSpawner particleSpawner;\n\n    AsyncVisibilityChecks(ParticleSpawner particleSpawner) {\n        this.particleSpawner = particleSpawner;\n    }',
    '''final class AsyncVisibilityChecks {
    private static final int EXEMPTION_RECHECK_TICKS = 20;

    private final ParticleSpawner particleSpawner;
    private final VisibilityExemptionPolicy visibilityExemptionPolicy;

    AsyncVisibilityChecks(ParticleSpawner particleSpawner) {
        this(particleSpawner, VisibilityExemptionPolicy.DISABLED);
    }

    AsyncVisibilityChecks(ParticleSpawner particleSpawner, VisibilityExemptionPolicy visibilityExemptionPolicy) {
        this.particleSpawner = particleSpawner;
        this.visibilityExemptionPolicy = visibilityExemptionPolicy;
    }'''
)
replace_once(
    visibility_checks,
    '        int checked = entityView.forEachNeedingRecheckEntity(\n                entityConfig.getVisibleRecheckIntervalTicks(), currentTick,',
    '        int configuredRecheckTicks = entityConfig.getVisibleRecheckIntervalTicks();\n        int checked = entityView.forEachNeedingRecheckEntity(\n                effectiveRecheckTicks(configuredRecheckTicks), currentTick,'
)
replace_once(
    visibility_checks,
    '                    if (EntityBypassRegistry.isRelationshipSupportEntity(entity.entityID())) {',
    '''                    boolean wasExempt = entity.visibilityExempt();
                    boolean exempt = visibilityExemptionPolicy.isExempt(
                            playerLocation.world(), entity.x(), entity.y(), entity.z());
                    entity.setVisibilityExempt(exempt);
                    if (exempt) {
                        setEntityAndSupportVehicleVisibility(
                                entityView, entity, true, currentTick, worldEpoch);
                        return;
                    }
                    if (!wasExempt && entity.visible() && configuredRecheckTicks < 0) {
                        entity.setLastChecked(currentTick);
                        return;
                    }
                    if (EntityBypassRegistry.isRelationshipSupportEntity(entity.entityID())) {'''
)
replace_once(
    visibility_checks,
    '        int checked = playerView.forEachNeedingRecheckEntity(\n                playerConfig.getVisibleRecheckIntervalTicks(), currentTick,',
    '        int configuredRecheckTicks = playerConfig.getVisibleRecheckIntervalTicks();\n        int checked = playerView.forEachNeedingRecheckEntity(\n                effectiveRecheckTicks(configuredRecheckTicks), currentTick,'
)
replace_once(
    visibility_checks,
    '                !(timings instanceof TickTimingBatchNoOp), worldEpoch, otherPlayer -> {\n                    if (otherPlayer.glowing()',
    '''                !(timings instanceof TickTimingBatchNoOp), worldEpoch, otherPlayer -> {
                    boolean wasExempt = otherPlayer.visibilityExempt();
                    boolean exempt = visibilityExemptionPolicy.isExempt(
                            playerLocation.world(), otherPlayer.x(), otherPlayer.y(), otherPlayer.z());
                    otherPlayer.setVisibilityExempt(exempt);
                    if (exempt) {
                        playerView.setVisibility(otherPlayer, true, currentTick, worldEpoch);
                        return;
                    }
                    if (!wasExempt && otherPlayer.visible() && configuredRecheckTicks < 0) {
                        otherPlayer.setLastChecked(currentTick);
                        return;
                    }
                    if (otherPlayer.glowing()'''
)
replace_once(
    visibility_checks,
    '    private RaycastUtil.Settings raycastSettings(',
    '''    private int effectiveRecheckTicks(int configuredRecheckTicks) {
        if (configuredRecheckTicks >= 0 || !visibilityExemptionPolicy.isActive()) {
            return configuredRecheckTicks;
        }
        return EXEMPTION_RECHECK_TICKS;
    }

    private RaycastUtil.Settings raycastSettings('''
)

async_engine = "core/src/main/java/games/cubi/raycastedantiesp/core/engine/AsyncEngine.java"
replace_once(async_engine, 'import games.cubi.raycastedantiesp.core.players.PlayerRegistry;\n', 'import games.cubi.raycastedantiesp.core.players.PlayerRegistry;\nimport games.cubi.raycastedantiesp.core.policy.VisibilityExemptionPolicy;\n')
replace_once(async_engine, '    private final AsyncRunner asyncRunner;\n', '    private final AsyncRunner asyncRunner;\n    private final VisibilityExemptionPolicy visibilityExemptionPolicy;\n')
replace_once(
    async_engine,
    '    public AsyncEngine(ConfigManager config, ParticleSpawner particleSpawner, IntSupplier currentTickSupplier, AsyncRunner asyncRunner) {\n        this.config = config;\n        this.particleSpawner = particleSpawner;\n        this.visibilityChecks = new AsyncVisibilityChecks(particleSpawner);\n        this.currentTickSupplier = currentTickSupplier;\n        this.asyncRunner = asyncRunner;\n    }',
    '''    public AsyncEngine(ConfigManager config, ParticleSpawner particleSpawner,
            IntSupplier currentTickSupplier, AsyncRunner asyncRunner) {
        this(config, particleSpawner, currentTickSupplier, asyncRunner, VisibilityExemptionPolicy.DISABLED);
    }

    public AsyncEngine(ConfigManager config, ParticleSpawner particleSpawner,
            IntSupplier currentTickSupplier, AsyncRunner asyncRunner,
            VisibilityExemptionPolicy visibilityExemptionPolicy) {
        this.config = config;
        this.particleSpawner = particleSpawner;
        this.visibilityExemptionPolicy = visibilityExemptionPolicy;
        this.visibilityChecks = new AsyncVisibilityChecks(particleSpawner, visibilityExemptionPolicy);
        this.currentTickSupplier = currentTickSupplier;
        this.asyncRunner = asyncRunner;
    }'''
)
replace_once(
    async_engine,
    '        int checked = blockView.updateVisibilityForEachNeedingRecheck(tileEntityConfig.getVisibleRecheckIntervalTicks(), currentTick, modeToken, worldEpoch, tileEntityLocation -> {\n\n            if (playerLocation.distanceSquared(tileEntityLocation)',
    '''        int configuredRecheckTicks = tileEntityConfig.getVisibleRecheckIntervalTicks();
        int recheckTicks = configuredRecheckTicks >= 0 || !visibilityExemptionPolicy.isActive()
                ? configuredRecheckTicks : 20;
        int checked = blockView.updateVisibilityForEachNeedingRecheck(
                recheckTicks, currentTick, modeToken, worldEpoch, tileEntityLocation -> {
            boolean wasExempt = tileEntityLocation.visibilityExempt();
            boolean exempt = visibilityExemptionPolicy.isExempt(
                    playerLocation.world(),
                    tileEntityLocation.blockX() + 0.5,
                    tileEntityLocation.blockY() + 0.5,
                    tileEntityLocation.blockZ() + 0.5);
            tileEntityLocation.setVisibilityExempt(exempt);
            if (exempt) {
                return BlockView.VisibilityResolver.SHOW;
            }
            if (!wasExempt && tileEntityLocation.visible() && configuredRecheckTicks < 0) {
                tileEntityLocation.setLastChecked(currentTick);
                return BlockView.VisibilityResolver.SKIPPED;
            }

            if (playerLocation.distanceSquared(tileEntityLocation)'''
)

paper_engine = "platform-paper/src/main/java/games/cubi/raycastedantiesp/paper/engine/PaperAsyncEngine.java"
replace_once(paper_engine, 'import games.cubi.raycastedantiesp.core.engine.AsyncEngine;\n', 'import games.cubi.raycastedantiesp.core.engine.AsyncEngine;\nimport games.cubi.raycastedantiesp.core.policy.VisibilityExemptionPolicy;\n')
replace_once(
    paper_engine,
    '    public PaperAsyncEngine(RaycastedAntiESP plugin, ConfigManager cfg, IntSupplier currentTickSupplier) {\n        super(cfg, new PaperParticleSpawner(), currentTickSupplier, new PaperAsyncRunner(plugin.getServer().getAsyncScheduler()));\n    }',
    '''    public PaperAsyncEngine(RaycastedAntiESP plugin, ConfigManager cfg,
            IntSupplier currentTickSupplier, VisibilityExemptionPolicy visibilityExemptionPolicy) {
        super(cfg, new PaperParticleSpawner(), currentTickSupplier,
                new PaperAsyncRunner(plugin.getServer().getAsyncScheduler()), visibilityExemptionPolicy);
    }'''
)

main = "platform-paper/src/main/java/games/cubi/raycastedantiesp/paper/RaycastedAntiESP.java"
replace_once(main, 'import games.cubi.raycastedantiesp.core.players.PlayerRegistry;\n', 'import games.cubi.raycastedantiesp.core.players.PlayerRegistry;\nimport games.cubi.raycastedantiesp.core.policy.VisibilityExemptionPolicy;\n')
replace_once(main, 'import games.cubi.raycastedantiesp.paper.engine.PaperAsyncEngine;\n', 'import games.cubi.raycastedantiesp.paper.engine.PaperAsyncEngine;\nimport games.cubi.raycastedantiesp.paper.integrations.WorldGuardVisibilityExemption;\n')
replace_once(main, '    private static volatile boolean reenableBlocked;\n', '    private static volatile boolean reenableBlocked;\n    private static VisibilityExemptionPolicy visibilityExemptionPolicy = VisibilityExemptionPolicy.DISABLED;\n    private static WorldGuardVisibilityExemption worldGuardVisibilityExemption;\n')
replace_once(
    main,
    '        instance = this;\n        Core.initialize(loggerAdapter);\n        initialiseConfigIfNeeded();\n        Plugin packetEvents',
    '        instance = this;\n        Core.initialize(loggerAdapter);\n        worldGuardVisibilityExemption = initialiseWorldGuardVisibilityExemption();\n        visibilityExemptionPolicy = worldGuardVisibilityExemption == null\n                ? VisibilityExemptionPolicy.DISABLED : worldGuardVisibilityExemption;\n        initialiseConfigIfNeeded();\n        Plugin packetEvents'
)
replace_once(
    main,
    '        finishPriorShutdownOrThrow();\n        initialiseConfigIfNeeded();\n\n        LifecycleScope startup',
    '        finishPriorShutdownOrThrow();\n        initialiseConfigIfNeeded();\n        if (worldGuardVisibilityExemption != null) {\n            worldGuardVisibilityExemption.enable(this);\n        }\n\n        LifecycleScope startup'
)
replace_once(main, '                engine = new PaperAsyncEngine(this, config, currentTickSupplier);', '                engine = new PaperAsyncEngine(this, config, currentTickSupplier, visibilityExemptionPolicy);')
replace_once(
    main,
    '                            currentTickSupplier, targetFilter, () -> teardownSafe.set(false)));',
    '                            currentTickSupplier, targetFilter, visibilityExemptionPolicy,\n                            () -> teardownSafe.set(false)));'
)
replace_once(
    main,
    '    private void initialiseConfigIfNeeded() {',
    '''    private WorldGuardVisibilityExemption initialiseWorldGuardVisibilityExemption() {
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") == null) {
            return null;
        }
        return WorldGuardVisibilityExemption.register();
    }

    private void initialiseConfigIfNeeded() {'''
)

# Existing test controller must satisfy the new direct-HIDE contract.
test_controller = "core/src/test/java/games/cubi/raycastedantiesp/core/view/controller/PacketEntityViewControllerTest.java"
text = Path(test_controller).read_text()
marker = '''        @Override
        protected void processDirectEntityShow(PlayerData playerData, EntityView<?> view, NettyEntity<?> entity, int worldEpoch) {
            directlyShownEntityIDs.add(entity.entityID());
            entity.setClientVisible(true);
        }
'''
if marker not in text:
    raise SystemExit("PacketEntityViewControllerTest direct-show override not found")
text = text.replace(marker, marker + '''
        @Override
        protected void processDirectEntityHide(PlayerData playerData, EntityView<?> view, NettyEntity<?> entity, int worldEpoch) {
            entity.setClientVisible(false);
        }
''', 1)
Path(test_controller).write_text(text)
