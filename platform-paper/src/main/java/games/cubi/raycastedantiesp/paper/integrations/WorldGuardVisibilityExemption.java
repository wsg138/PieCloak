package games.cubi.raycastedantiesp.paper.integrations;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.association.RegionAssociable;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
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
