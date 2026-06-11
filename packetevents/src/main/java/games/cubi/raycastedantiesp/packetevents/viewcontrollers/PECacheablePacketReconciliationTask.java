package games.cubi.raycastedantiesp.packetevents.viewcontrollers;

import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import games.cubi.raycastedantiesp.core.locatables.NettyEntityLocatable;
import games.cubi.raycastedantiesp.core.players.PlayerData;
import games.cubi.raycastedantiesp.core.utils.BaseEntitySpawnTask;
import games.cubi.raycastedantiesp.packetevents.locatables.PacketEventsEntity;
import games.cubi.raycastedantiesp.packetevents.replaydata.PacketEventsEntityReplayData;

final class PECacheablePacketReconciliationTask extends BaseEntitySpawnTask {
    private final PlayerData playerData;
    private final int entityID;
    private final String packetType;
    private final PacketEventsEntityReplayData pendingReplayData;

    PECacheablePacketReconciliationTask(PlayerData playerData, int entityID, PacketWrapper<?> packet, int submittedTick) {
        super(submittedTick);
        this.playerData = playerData;
        this.entityID = entityID;
        this.packetType = packet.getClass().getSimpleName();
        this.pendingReplayData = PacketEventsEntityReplayData.create();
        this.pendingReplayData.addPacket(packet);
    }

    @Override
    public void run() {
        NettyEntityLocatable<?,?> entity = playerData.trackedEntityFromID(entityID);
        if (entity == null) {
            return;
        }
        PacketEventsEntityReplayData replayData = PacketEventsEntityViewController.get().ensureReplayData((PacketEventsEntity) entity);
        for (PacketWrapper<?> packet : pendingReplayData.snapshotPackets(entityID)) {
            replayData.addPacket(packet);
        }
    }

    @Override
    public String toString() {
        return "PECacheablePacketReconciliationTask{" +
                "submittedTick=" + submittedTick +
                ", entityID=" + entityID +
                ", packetType=" + packetType +
                ", playerUUID=" + playerData.getPlayerUUID() +
                '}';
    }
}
