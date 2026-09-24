package games.cubi.raycastedantiesp.paper.packets;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import games.cubi.raycastedantiesp.core.chunks.BlockInfoResolver;
import games.cubi.raycastedantiesp.core.players.PlayerData;
import games.cubi.raycastedantiesp.core.players.PlayerRegistry;
import games.cubi.raycastedantiesp.core.policy.VisibilityExemptionPolicy;
import games.cubi.raycastedantiesp.packetevents.viewcontrollers.PacketEventsBlockViewController;
import games.cubi.raycastedantiesp.packetevents.viewcontrollers.PacketEventsRespawnStateInvalidator;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Material;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntSupplier;

public class PaperPacketEventsBlockViewController extends PacketEventsBlockViewController implements AutoCloseable {
    private final int stoneBlockId = SpigotConversionUtil.fromBukkitBlockData(Material.STONE.createBlockData()).getGlobalId();
    private final int deepslateBlockId = SpigotConversionUtil.fromBukkitBlockData(Material.DEEPSLATE.createBlockData()).getGlobalId();
    private final PacketListenerCommon registration;
    private final AtomicBoolean closed = new AtomicBoolean();

    public PaperPacketEventsBlockViewController(BlockInfoResolver blockInfoResolver, boolean trackAllBlocks,
            IntSupplier currentTickSupplier) {
        this(blockInfoResolver, trackAllBlocks, currentTickSupplier, VisibilityExemptionPolicy.DISABLED);
    }

    public PaperPacketEventsBlockViewController(BlockInfoResolver blockInfoResolver, boolean trackAllBlocks,
            IntSupplier currentTickSupplier, VisibilityExemptionPolicy visibilityExemptionPolicy) {
        super(blockInfoResolver, trackAllBlocks, currentTickSupplier, visibilityExemptionPolicy);
        PacketListenerCommon createdRegistration = asAbstract(PacketListenerPriority.HIGHEST);
        try {
            registration = PacketEvents.getAPI().getEventManager().registerListener(createdRegistration);
        } catch (RuntimeException | Error throwable) {
            try {
                PacketEvents.getAPI().getEventManager().unregisterListener(createdRegistration);
            } catch (RuntimeException | Error cleanupFailure) {
                throwable.addSuppressed(cleanupFailure);
            }
            closed.set(true);
            throw throwable;
        }
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (PacketEventsRespawnStateInvalidator.isRespawnPacket(event.getPacketType())) {
            UUID playerUUID = event.getUser().getUUID();
            if (playerUUID != null) {
                removeViewer(playerUUID);
            }
            return;
        }

        List<Runnable> afterSendTasks = event.getTasksAfterSend();
        int firstControllerTask = afterSendTasks.size();
        super.onPacketSend(event);

        UUID playerUUID = event.getUser().getUUID();
        PlayerData playerData = playerUUID == null
                ? null
                : PlayerRegistry.getInstance().getPlayerData(playerUUID);
        if (playerData == null) {
            return;
        }
        AfterSendVisibilityRepair.wrapNewTasks(
                afterSendTasks,
                firstControllerTask,
                playerData,
                playerData.acquireWorldEpoch(),
                event.getUser()::flushPackets);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            PacketEvents.getAPI().getEventManager().unregisterListener(registration);
        }
    }

    @Override
    protected int getHiddenBlockId(int blockY) {
        return blockY > 0 ? stoneBlockId : deepslateBlockId;
    }
}
