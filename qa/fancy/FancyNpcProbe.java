package qa.fancy;

import de.oliver.fancynpcs.api.FancyNpcsPlugin;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.NpcData;
import de.oliver.fancynpcs.api.events.NpcModifyEvent;
import de.oliver.fancynpcs.api.events.NpcRemoveEvent;
import de.oliver.fancynpcs.api.events.NpcSpawnEvent;
import games.cubi.raycastedantiesp.core.entity.EntityBypassRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.UUID;

/** Test-only plugin. Never package or deploy with PieCloak. */
public final class FancyNpcProbe extends JavaPlugin implements Listener {
    private Npc cancelOwner;
    private boolean failed;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        new BukkitRunnable() {
            @Override
            public void run() {
                guarded(() -> {
                    if (!FancyNpcsPlugin.get().getNpcManager().isLoaded() || Bukkit.getWorlds().isEmpty()) {
                        return;
                    }
                    cancel();
                    require(Bukkit.getPluginManager().isPluginEnabled("PieCloak"), "PieCloak enabled");
                    require(Bukkit.getOnlinePlayers().isEmpty(), "zero-viewer control");
                    zeroViewer();
                    marker("READY");
                    waitForViewers();
                });
            }
        }.runTaskTimer(this, 20L, 20L);
    }

    private void zeroViewer() {
        Location loc = Bukkit.getWorlds().getFirst().getSpawnLocation();
        Npc unborn = uncreated("qa_unborn", loc, EntityType.VILLAGER);
        modify(unborn, EntityType.COW);
        require(new NpcRemoveEvent(unborn, Bukkit.getConsoleSender()).callEvent(), "uncreated deletion");
        unborn.create();
        modify(unborn, EntityType.IRON_GOLEM);
        require(!bypassed(unborn.getEntityId()), "uncreated deleted owner cannot register unseen ID");

        Npc npc = uncreated("qa_empty", loc, EntityType.VILLAGER);
        npc.create();
        FancyNpcsPlugin.get().getNpcManager().registerNpc(npc);
        int oldId = npc.getEntityId();
        modify(npc, EntityType.IRON_GOLEM);
        require(bypassed(oldId), "old ID initially claimed");
        cancelOwner = npc;
        require(!new NpcRemoveEvent(npc, Bukkit.getConsoleSender()).callEvent(), "cancelled deletion");
        cancelOwner = null;
        require(bypassed(oldId), "cancelled deletion preserves valid bypass");

        // Match FancyNpcs TypeCMD: modify callback precedes replacing its native entity.
        npc.getData().setType(EntityType.IRON_GOLEM);
        npc.create();
        int newId = npc.getEntityId();
        require(newId != oldId, "native replacement allocated a distinct ID");
        require(bypassed(oldId), "old ID was registered without any viewer");
        require(new NpcRemoveEvent(npc, Bukkit.getConsoleSender()).callEvent(), "accepted deletion");
        npc.removeForAll();
        FancyNpcsPlugin.get().getNpcManager().removeNpc(npc);
        require(!bypassed(oldId) && !bypassed(newId), "deletion removes both old and unseen new ID");
        npc.getData().setType(EntityType.COW);
        npc.create();
        int delayedId = npc.getEntityId();
        modify(npc, EntityType.VILLAGER);
        require(!bypassed(delayedId), "deleted owner cannot claim third incarnation");
        marker("ZERO_VIEWER_PASS " + oldId + " " + newId + " " + delayedId);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void cancelChosenRemoval(NpcRemoveEvent event) {
        if (event.getNpc() == cancelOwner) {
            event.setCancelled(true);
        }
    }

    private void waitForViewers() {
        new BukkitRunnable() {
            @Override
            public void run() {
                guarded(() -> {
                    if (Bukkit.getOnlinePlayers().size() != 2) {
                        return;
                    }
                    cancel();
                    List<? extends Player> players = List.copyOf(Bukkit.getOnlinePlayers());
                    Location location = players.getFirst().getLocation();
                    players.get(1).teleport(location);
                    later(40L, () -> beginLive(players, location.clone().add(0, 0, 2)));
                });
            }
        }.runTaskTimer(this, 10L, 10L);
    }

    private void beginLive(List<? extends Player> players, Location loc) {
        Npc npc = uncreated("qa_live", loc, EntityType.VILLAGER);
        npc.create();
        FancyNpcsPlugin.get().getNpcManager().registerNpc(npc);
        int oldId = npc.getEntityId();
        npc.spawnForAll();
        later(80L, () -> {
            require(bypassed(oldId), "real spawn callback registered old ID");
            marker("LIVE_OLD " + oldId);
            require(Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "npc type qa_live iron_golem"), "type command accepted");
            later(120L, () -> checkReplacement(players, npc, oldId));
        });
    }

    private void checkReplacement(List<? extends Player> players, Npc npc, int oldId) {
        int newId = npc.getEntityId();
        require(newId != oldId, "real type command allocated replacement ID");
        require(!bypassed(oldId), "real type change retired previous ID");
        require(bypassed(newId), "replacement spawn registered new ID");
        marker("LIVE_NEW " + newId);
        require(Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "npc remove qa_live"), "remove command accepted");
        later(60L, () -> {
            require(!bypassed(oldId) && !bypassed(newId), "real remove command retired all IDs");
            // Synthetic delayed callback uses FancyNpcs' real async event and a real player.
            npc.getData().setType(EntityType.COW);
            npc.create();
            int delayedId = npc.getEntityId();
            FancyNpcsPlugin.get().getNpcThread().submit(() -> guarded(() -> {
                new NpcSpawnEvent(npc, players.getFirst()).callEvent();
                require(!bypassed(delayedId), "async late spawn cannot reactivate deleted owner");
                marker("LIVE_LATE " + delayedId);
                marker("PASS");
            }));
        });
    }

    private Npc uncreated(String name, Location location, EntityType type) {
        NpcData data = new NpcData(name, UUID.randomUUID(), location);
        data.setType(type);
        return FancyNpcsPlugin.get().getNpcAdapter().apply(data);
    }

    private void modify(Npc npc, EntityType type) {
        require(new NpcModifyEvent(npc, NpcModifyEvent.NpcModification.TYPE, type,
                Bukkit.getConsoleSender()).callEvent(), "modify callback accepted");
    }

    private boolean bypassed(int id) {
        return EntityBypassRegistry.isBypassed(id);
    }

    private void later(long delay, Runnable task) {
        Bukkit.getScheduler().runTaskLater(this, () -> guarded(task), delay);
    }

    private void require(boolean condition, String description) {
        if (!condition) {
            throw new AssertionError(description);
        }
    }

    private void guarded(Runnable task) {
        if (failed) {
            return;
        }
        try {
            task.run();
        } catch (Throwable failure) {
            failed = true;
            getLogger().severe("FANCY_QA FAIL " + failure);
            failure.printStackTrace();
        }
    }

    private void marker(String value) {
        getLogger().info("FANCY_QA " + value);
    }
}
