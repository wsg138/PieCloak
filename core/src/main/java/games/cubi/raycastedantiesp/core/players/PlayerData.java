package games.cubi.raycastedantiesp.core.players;

import games.cubi.locatables.Locatable;
import games.cubi.locatables.implementations.ThreadSafeLocatable;
import games.cubi.logs.Logger;
import games.cubi.raycastedantiesp.core.locatables.NettyEntityLocatable;
import games.cubi.raycastedantiesp.core.view.BlockView;
import games.cubi.raycastedantiesp.core.view.EntityView;
import games.cubi.raycastedantiesp.core.view.ViewRegistry;
import games.cubi.raycastedantiesp.core.view.controller.PacketEntityViewController;

import java.util.UUID;

public class PlayerData {
    private final UUID playerUUID;
    private final int joinTick;
    private volatile boolean hasBypassPermission;
    private final ThreadSafeLocatable ownLocation;
    private volatile float yaw;
    private volatile float pitch;
    private volatile double lookX;
    private volatile double lookY;
    private volatile double lookZ = 1;

    private final BlockView blockView;
    private final EntityView<?> entityView;
    private final EntityView<?> playerView;
    private final NettyData nettyData;

    public PlayerData(UUID player, boolean hasBypassPermission, int joinTick) {
        this(player, joinTick);
        this.hasBypassPermission = hasBypassPermission;
    }

    public PlayerData(UUID player, int joinTick) {
        this.joinTick = joinTick;
        this.playerUUID = player;

        blockView = ViewRegistry.createBlockView();
        entityView = ViewRegistry.createEntityView();
        playerView = ViewRegistry.createPlayerEntityView();
        nettyData = new NettyData();
        ownLocation = new ThreadSafeLocatable(null, 0, 0, 0);
    }

    public EntityView<?> entityView() {
        return entityView;
    }

    public EntityView<?> playerView() {
        return playerView;
    }

    public NettyData nettyData() {
        return nettyData;
    }

    public BlockView blockView() {
        return blockView;
    }

    public void updateOwnLocation(UUID world, double x, double y, double z) {
        ownLocation.set(x, y, z, world);
    }

    public void updateOwnLocationAndLook(UUID world, double x, double y, double z, float yaw, float pitch, double lookX, double lookY, double lookZ) {
        updateOwnLocation(world, x, y, z);
        this.yaw = yaw;
        this.pitch = pitch;
        this.lookX = lookX;
        this.lookY = lookY;
        this.lookZ = lookZ;
    }

    public Locatable ownLocation() {
        ThreadSafeLocatable existing = ownLocation;
        return existing == null ? null : existing.clonePlainAndCentreIfBlockLocation();
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    public double lookX() {
        return lookX;
    }

    public double lookY() {
        return lookY;
    }

    public double lookZ() {
        return lookZ;
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public boolean hasBypassPermission() {
        return hasBypassPermission;
    }

    public int getJoinTick() {
        return joinTick;
    }

    /**
     * @return Either the entity or player view for this player, depending on the entity ID
     */
    public EntityView<?> viewFromEntityID(int entityID) {
        if (entityView.exists(entityID)) {
            return entityView;
        }
        if (playerView.exists(entityID)) {
            return playerView;
        }
        Logger.warning("Could not find view for entityID=" + entityID + " uuid=" + playerUUID, 6, PacketEntityViewController.class);
        return null;
    }

    public EntityView<?> trackedViewFromEntityID(int entityID) {
        if (entityView.getEntity(entityID) != null) {
            return entityView;
        }
        if (playerView.getEntity(entityID) != null) {
            return playerView;
        }
        return null;
    }

    public NettyEntityLocatable<?,?> entityFromID(int entityID) {
        EntityView<?> entityView = viewFromEntityID(entityID);
        if (entityView == null) {
            return null;
        }
        return (NettyEntityLocatable<?, ?>) entityView.getEntity(entityID);
    }

    public NettyEntityLocatable<?,?> trackedEntityFromID(int entityID) {
        EntityView<?> entityView = trackedViewFromEntityID(entityID);
        if (entityView == null) {
            return null;
        }
        return (NettyEntityLocatable<?, ?>) entityView.getEntity(entityID);
    }

    public void setBypassPermission(boolean hasBypassPermission) {
        this.hasBypassPermission = hasBypassPermission;
    } //todo: need to link up

    @Override
    public String toString() {
        return "PlayerData{" +
                "playerUUID=" + playerUUID +
                ", joinTick=" + joinTick +
                ", hasBypassPermission=" + hasBypassPermission +
                ", ownLocation=" + ownLocation +
                ", blockView=" + blockView +
                ", entityView=" + entityView +
                ", playerView=" + playerView +
                ", nettyData=" + nettyData +
                '}';
    }
}
