package qa.burst;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityVelocity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import games.cubi.raycastedantiesp.core.entity.EntityBypassRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.Optional;
import java.util.UUID;

public final class BurstProbe extends JavaPlugin {
    static final int FIRST = 430000, COUNT = 48, REARM = 8, PROMPT = 430100, BYPASS = 430200;
    private int stage;
    private Player viewer;
    private User user;
    private Location origin;

    @Override
    public void onEnable() {
        getLogger().info("BURST_QA READY");
        new BukkitRunnable() {
            @Override public void run() {
                if (Bukkit.getOnlinePlayers().size() != 2) return;
                viewer = Bukkit.getPlayerExact("FancyQaA");
                if (viewer == null) return;
                user = PacketEvents.getAPI().getPlayerManager().getUser(viewer);
                origin = viewer.getLocation();
                cancel();
                Bukkit.getScheduler().runTaskTimer(BurstProbe.this, task -> {
                    switch (stage++) {
                        case 0 -> firstBurst();
                        case 1 -> repeatBurst();
                        case 2 -> destroyAndRearm();
                        case 3 -> lateSpawn();
                        case 4 -> { getLogger().info("BURST_QA DONE"); task.cancel(); }
                        default -> task.cancel();
                    }
                }, 60L, 60L);
            }
        }.runTaskTimer(this, 10L, 10L);
    }
    private void state(int id, int type) {
        switch (type % 3) {
            case 0 -> user.sendPacket(new WrapperPlayServerEntityVelocity(id, new Vector3d(0.01, 0, 0)));
            case 1 -> user.sendPacket(new WrapperPlayServerEntityRelativeMove(id, 0.125, 0, 0, true));
            case 2 -> user.sendPacket(new WrapperPlayServerEntityHeadLook(id, 90));
            default -> throw new AssertionError();
        }
    }
    private void spawn(int id) {
        user.sendPacket(new WrapperPlayServerSpawnEntity(id, Optional.of(UUID.randomUUID()),
            EntityTypes.VILLAGER, new Vector3d(origin.getX()+2, origin.getY(), origin.getZ()),
            0, 0, 0, 0, Optional.of(Vector3d.zero())));
    }
    private void firstBurst() {
        getLogger().info("BURST_QA FIRST");
        for (int i=0; i<COUNT; i++) state(FIRST+i, i);
        for (int i=0; i<6; i++) {
            state(PROMPT+i, i);
            spawn(PROMPT+i);
            EntityBypassRegistry.addEntity(BYPASS+i);
            state(BYPASS+i, i);
            spawn(BYPASS+i);
        }
    }
    private void repeatBurst() {
        getLogger().info("BURST_QA REPEAT");
        for (int i=0; i<COUNT; i++) state(FIRST+i, i);
    }
    private void destroyAndRearm() {
        getLogger().info("BURST_QA REARM");
        for (int i=0; i<REARM; i++) {
            user.sendPacket(new WrapperPlayServerDestroyEntities(FIRST+i));
            state(FIRST+i, i);
        }
    }
    private void lateSpawn() {
        getLogger().info("BURST_QA LATE");
        for (int i=0; i<REARM; i++) spawn(FIRST+i);
        for (int i=0; i<6; i++) {
            user.sendPacket(new WrapperPlayServerDestroyEntities(BYPASS+i));
            EntityBypassRegistry.markEntityDespawned(BYPASS+i);
        }
    }
}
