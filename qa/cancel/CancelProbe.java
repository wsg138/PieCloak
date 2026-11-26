package qa.cancel;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CancelProbe extends JavaPlugin {
    private static final int EARLY = 421042, LATE = 421043, MONITOR = 421044, ALLOW = 421045, FAR = 421046;
    private final Set<Integer> cancelled = ConcurrentHashMap.newKeySet();

    @Override
    public void onEnable() {
        cancelAt(PacketListenerPriority.LOWEST, EARLY);
        cancelAt(PacketListenerPriority.HIGHEST, LATE);
        cancelAt(PacketListenerPriority.MONITOR, MONITOR);
        getLogger().info("CANCEL_QA READY");
        new BukkitRunnable() {
            @Override
            public void run() {
                if (Bukkit.getOnlinePlayers().size() != 2) {
                    return;
                }
                cancel();
                List<? extends Player> players = List.copyOf(Bukkit.getOnlinePlayers());
                Player viewer = players.stream().filter(p -> p.getName().equals("FancyQaA")).findFirst().orElseThrow();
                Location location = viewer.getLocation();
                Bukkit.getScheduler().runTaskLater(CancelProbe.this, () -> runCases(viewer, location), 60L);
            }
        }.runTaskTimer(this, 10L, 10L);
    }

    private void cancelAt(PacketListenerPriority priority, int target) {
        PacketEvents.getAPI().getEventManager().registerListener(new PacketListenerAbstract(priority) {
            @Override
            public void onPacketSend(PacketSendEvent event) {
                if (event.getPacketType() != PacketType.Play.Server.SPAWN_ENTITY) {
                    return;
                }
                var packet = new WrapperPlayServerSpawnEntity(event);
                if (packet.getEntityId() == target) {
                    event.setCancelled(true);
                    cancelled.add(target);
                    getLogger().info("CANCEL_QA CANCELLED " + priority + " " + target);
                }
            }
        });
    }

    private void runCases(Player viewer, Location location) {
        User user = PacketEvents.getAPI().getPlayerManager().getUser(viewer);
        Vector3d close = new Vector3d(location.getX() + 2, location.getY(), location.getZ());
        for (int id : new int[]{EARLY, LATE, MONITOR, ALLOW}) {
            sendSpawn(user, id, close);
        }
        Vector3d distant = new Vector3d(location.getX() + 100, location.getY(), location.getZ());
        sendSpawn(user, FAR, distant);
        getLogger().info("CANCEL_QA SENT");
        Bukkit.getScheduler().runTaskLater(this, () -> {
            getLogger().info("CANCEL_QA REVEAL");
            user.sendPacket(new WrapperPlayServerEntityTeleport(FAR, close, 0, 0, true));
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (!cancelled.containsAll(Set.of(EARLY, LATE, MONITOR))) {
                    getLogger().severe("CANCEL_QA FAIL missing cancellation callbacks " + cancelled);
                    return;
                }
                getLogger().info("CANCEL_QA DONE");
            }, 160L);
        }, 120L);
    }

    private void sendSpawn(User user, int id, Vector3d pos) {
        user.sendPacket(new WrapperPlayServerSpawnEntity(id, Optional.of(UUID.randomUUID()),
                EntityTypes.VILLAGER, pos, 0, 0, 0, 0, Optional.of(Vector3d.zero())));
    }
}
