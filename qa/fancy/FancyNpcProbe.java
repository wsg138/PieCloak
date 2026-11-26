package qa.fancy;

import de.oliver.fancynpcs.api.FancyNpcsPlugin;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.NpcData;
import de.oliver.fancynpcs.api.events.NpcInteractEvent;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Test-only plugin. Never package or deploy with PieCloak. */
public final class FancyNpcProbe extends JavaPlugin implements Listener {
    private Npc cancelOwner;
    private volatile boolean failed;
    private volatile Npc liveNpc;
    private final Set<String> interacted = ConcurrentHashMap.newKeySet();

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
                    Npc persisted = FancyNpcsPlugin.get().getNpcManager().getNpc("qa_restart");
                    if (persisted != null) {
                        marker("RESTART_READY");
                        waitForViewers(persisted);
                    } else {
                        zeroViewer();
                        marker("READY");
                        waitForViewers(null);
                    }
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteraction(NpcInteractEvent event) {
        if (event.getNpc() == liveNpc) {
            interacted.add(event.getPlayer().getName());
        }
    }

    private void waitForViewers(Npc persisted) {
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
                    later(40L, () -> {
                        if (persisted == null) {
                            beginLive(players, location.clone().add(0, 0, 2));
                        } else {
                            restartLive(players, persisted);
                        }
                    });
                });
            }
        }.runTaskTimer(this, 10L, 10L);
    }

    private void beginLive(List<? extends Player> players, Location loc) {
        Npc npc = uncreated("qa_live", loc, EntityType.VILLAGER);
        npc.getData().setVisibilityDistance(12);
        npc.create();
        FancyNpcsPlugin.get().getNpcManager().registerNpc(npc);
        liveNpc = npc;
        int oldId = npc.getEntityId();
        npc.spawnForAll();
        later(80L, () -> {
            require(bypassed(oldId), "real spawn callback registered old ID");
            marker("LIVE_OLD " + oldId);
            marker("INTERACT " + oldId);
            later(120L, () -> {
                for (Player player : players) {
                    require(interacted.contains(player.getName()), "actual NPC click from " + player.getName());
                }
                marker("INTERACT_PASS");
                moveAndReenter(players, npc, oldId);
            });
        });
    }

    private void moveAndReenter(List<? extends Player> players, Npc npc, int oldId) {
        Location next = npc.getData().getLocation().clone().add(1, 0, 0);
        npc.getData().setLocation(next);
        FancyNpcsPlugin.get().getNpcThread().submit(() -> {
            for (Player player : players) {
                npc.move(player);
            }
        });
        later(40L, () -> {
            Player moving = players.getFirst();
            Player witness = players.get(1);
            marker("MOVED " + moving.getName());
            moving.teleport(next.clone().add(40, 0, 0));
            later(100L, () -> {
                require(!npc.getIsVisibleForPlayer().getOrDefault(moving.getUniqueId(), false), "out-of-range viewer hidden");
                require(npc.getIsVisibleForPlayer().getOrDefault(witness.getUniqueId(), false), "nearby witness unaffected");
                require(bypassed(oldId), "shared bypass survives one viewer leaving");
                moving.teleport(next.clone().add(0, 0, -2));
                later(100L, () -> {
                    require(npc.getIsVisibleForPlayer().getOrDefault(moving.getUniqueId(), false), "returning viewer sees NPC again");
                    require(bypassed(oldId), "reentry preserves NPC bypass");
                    marker("RANGE_PASS");
                    require(Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "npc type qa_live iron_golem"), "type command accepted");
                    later(120L, () -> checkReplacement(players, npc, oldId));
                });
            });
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
                later(1L, () -> newOwnerAndPersist(players, npc));
            }));
        });
    }

    private void newOwnerAndPersist(List<? extends Player> players, Npc retired) {
        Npc replacement = uncreated("qa_live", retired.getData().getLocation(), EntityType.PLAYER);
        replacement.create();
        FancyNpcsPlugin.get().getNpcManager().registerNpc(replacement);
        int id = replacement.getEntityId();
        replacement.spawnForAll();
        later(80L, () -> {
            require(bypassed(id), "new player-type NPC can reuse deleted name");
            marker("LIVE_PLAYER " + id);
            require(Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "npc remove qa_live"), "player NPC remove command");
            later(60L, () -> {
                require(!bypassed(id), "player NPC deletion clears bypass");
                Npc saved = uncreated("qa_restart", players.getFirst().getLocation().clone().add(0, 0, 2), EntityType.VILLAGER);
                saved.create();
                saved.setSaveToFile(true);
                FancyNpcsPlugin.get().getNpcManager().registerNpc(saved);
                FancyNpcsPlugin.get().getNpcManager().saveNpcs(true);
                marker("PERSISTED");
                marker("PASS");
            });
        });
    }

    private void restartLive(List<? extends Player> players, Npc npc) {
        Location location = npc.getData().getLocation().clone().add(0, 0, -2);
        for (Player player : players) {
            player.teleport(location);
        }
        int id = npc.getEntityId();
        later(40L, npc::spawnForAll);
        later(120L, () -> {
            require(bypassed(id), "persisted NPC registered after full JVM restart");
            marker("RESTART_ID " + id);
            require(Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "npc remove qa_restart"), "persisted NPC remove command");
            later(60L, () -> {
                require(!bypassed(id), "persisted NPC deletion clears bypass");
                marker("RESTART_PASS");
            });
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
