package games.cubi.raycastedantiesp.paper.packets;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import games.cubi.raycastedantiesp.core.chunks.BlockInfoResolver;
import games.cubi.raycastedantiesp.packetevents.viewcontrollers.PacketEventsBlockViewController;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Material;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntSupplier;

public class PaperPacketEventsBlockViewController extends PacketEventsBlockViewController implements AutoCloseable {
    private final int stoneBlockId = SpigotConversionUtil.fromBukkitBlockData(Material.STONE.createBlockData()).getGlobalId();
    private final int deepslateBlockId = SpigotConversionUtil.fromBukkitBlockData(Material.DEEPSLATE.createBlockData()).getGlobalId();
    private final PacketListenerCommon registration;
    private final AtomicBoolean closed = new AtomicBoolean();

    public PaperPacketEventsBlockViewController(BlockInfoResolver blockInfoResolver, boolean trackAllBlocks, IntSupplier currentTickSupplier) {
        super(blockInfoResolver, trackAllBlocks, currentTickSupplier);
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
