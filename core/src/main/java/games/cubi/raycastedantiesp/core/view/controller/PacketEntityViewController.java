package games.cubi.raycastedantiesp.core.view.controller;

import games.cubi.logs.Logger;
import games.cubi.raycastedantiesp.core.config.raycast.EntityConfig;
import games.cubi.raycastedantiesp.core.config.raycast.PlayerConfig;
import games.cubi.raycastedantiesp.core.config.raycast.RaycastConfig;
import games.cubi.raycastedantiesp.core.locatables.NettyEntityLocatable;
import games.cubi.raycastedantiesp.core.players.PlayerData;
import games.cubi.raycastedantiesp.core.players.PlayerRegistry;
import games.cubi.raycastedantiesp.core.players.NettyData;
import games.cubi.raycastedantiesp.core.utils.IntArrayList;
import games.cubi.raycastedantiesp.core.utils.Packet;
import games.cubi.raycastedantiesp.core.view.EntityView;

import java.util.ArrayList;
import java.util.UUID;

import static games.cubi.raycastedantiesp.core.locatables.NettyEntityLocatable.NO_LEASHER;
import static games.cubi.raycastedantiesp.core.locatables.NettyEntityLocatable.NO_VEHICLE;

/**
 * @param <P> The platform's packet wrapper (PacketWrapper<?>)
 */
public abstract class PacketEntityViewController<P> {
    private static PacketEntityViewController<?> SELF; //TODO Switch to LazyConstant once out of preview (see https://openjdk.org/jeps/526)

    {
        synchronized (PacketEntityViewController.class) {
            if (SELF != null) {
                throw new IllegalStateException("Multiple instances of PacketEventsEntityViewController created.");
            }
            SELF = this;

        }
    }

    protected static PacketEntityViewController<?> get() {
        return SELF;
    }

    protected EntityConfig entityConfig;
    protected PlayerConfig playerConfig;
    protected double hideOnSpawnEntityDistanceSquared;
    protected double hideOnSpawnPlayerDistanceSquared;
    protected double alwaysShowEntityDistanceSquared;

    protected void handlePlayPhaseLoginPacket(int entityID, UUID playerUUID, int currentTick) {
        PlayerData playerData = PlayerRegistry.getInstance().getPlayerData(playerUUID);
        playerData.playerView().insertEntity(createSelfEntity(playerData, entityID, playerUUID).cast());
    }

    protected PlayerData handleLoginPhaseLoginPacket(UUID playerUUID, int currentTick) {
        return PlayerRegistry.getInstance().registerAndGetPlayer(playerUUID, currentTick);
    }

    protected abstract NettyEntityLocatable<?,?> createSelfEntity(PlayerData ownData, int entityID, UUID playerUUID);

    /**
     * @return Whether or not to cancel the packet event. <code>true</code> to cancel, <code>false</code> to do nothing.
     */
    @Packet(Packet.Packets.SPAWN_ENTITY)
    protected boolean handleEntitySpawn(P packet, int entityID, boolean isPlayer, boolean shouldCullEntity, boolean shouldTrackEntity, PlayerData playerData, UUID world, int currentTick) {
        boolean returnValue = handleEntitySpawn0(packet, isPlayer, shouldCullEntity, shouldTrackEntity, playerData, world, currentTick);
        playerData.nettyData().runPendingPostSpawnTaskForEntity(entityID);
        return returnValue;
    }

    protected boolean handleEntitySpawn0(P packet, boolean isPlayer, boolean shouldCullEntity, boolean shouldTrackEntity, PlayerData playerData, UUID world, int currentTick) {
        if (world == null) {
            Logger.error(new RuntimeException("World null when handling spawn entity packet, uuid=" + playerData.getPlayerUUID() + " tick=" + currentTick), 2, PacketEntityViewController.class);
            return false;
        }
        NettyEntityLocatable<?,?> entity = processEntitySpawn(playerData, packet, world, currentTick);
        if (entity == null) {
            return false;
        }
        entity.setCullTarget(shouldCullEntity);
        reconcileEntityRelationships(entity, playerData);
        if (isPlayer) {
            entity.setVisible(true);
            entity.setClientVisible(true);
            insertEntityToPlayerView(entity, playerData);
            return false;
        }
        if (!shouldCullEntity && !shouldTrackEntity) {
            entity.setVisible(true);
            entity.setClientVisible(true);
            return false;
        }
        if (!shouldCullEntity || playerData.hasBypassPermission() || shouldForceEntityVisible(entity, playerData)) {
            entity.setVisible(true);
            entity.setClientVisible(true);
        } else if (entityConfig.enabled()) {
            if (!hasUsableViewerLocation(playerData, entity)) {
                entity.setVisible(true);
                entity.setClientVisible(true);
                insertEntityToEntityView(entity, playerData);
                return false;
            }
            double distanceSquared = playerData.ownLocation().distanceSquared(entity);
            if (distanceSquared > alwaysShowEntityDistanceSquared && distanceSquared > hideOnSpawnEntityDistanceSquared) {
                entity.setVisible(false);
                entity.setClientVisible(false);
                insertEntityToEntityView(entity, playerData);
                return true;
            }
        } else {
            entity.setClientVisible(true);
        }
        insertEntityToEntityView(entity, playerData);
        return false;
    }

    private boolean hasUsableViewerLocation(PlayerData playerData, NettyEntityLocatable<?,?> entity) {
        if (playerData.ownLocation() == null || playerData.ownLocation().world() == null || entity.world() == null) {
            return false;
        }
        return playerData.ownLocation().world().equals(entity.world());
    }

    @Packet(Packet.Packets.ENTITY_ANIMATION)
    protected boolean handleEntityAnimation(int entityID, PlayerData playerData) {
        return cancelIfEnabledAndHidden(entityID, playerData);
    }

    @Packet(Packet.Packets.ENTITY_EVENT)
    protected boolean handleEntityEvent(int entityID, PlayerData playerData) {
        return cancelIfEnabledAndHidden(entityID, playerData);
    }

    @Packet(Packet.Packets.HURT_ANIMATION)
    protected boolean handleHurtAnimation(int entityID, PlayerData playerData) {
        return cancelIfEnabledAndHidden(entityID, playerData);
    }

    /**
     * @return Whether or not to cancel the packet event. <code>true</code> to cancel, <code>false</code> to do nothing.
     */
    protected boolean handleRelativeMove(P packet, PlayerData playerData, int currentTick) {
        int entityID = processRelativeMovePacket(packet, playerData, currentTick);
        return cancelIfEnabledAndHidden(entityID, playerData);
    }
    /**
     * @return Whether or not to cancel the packet event. <code>true</code> to cancel, <code>false</code> to do nothing.
     */
    protected boolean handleRelativeMoveAndRotation(P packet, PlayerData playerData, int currentTick) {
        int entityID = processRelativeMoveAndRotationPacket(packet, playerData, currentTick);
        return cancelIfEnabledAndHidden(entityID, playerData);
    }
    /**
     * @return Whether or not to cancel the packet event. <code>true</code> to cancel, <code>false</code> to do nothing.
     */
    protected boolean handleTeleport(P packet, PlayerData playerData, int currentTick) {
        int entityID = processTeleportPacket(packet, playerData, currentTick);
        return cancelIfEnabledAndHidden(entityID, playerData);
    }
    /**
     * @return Whether or not to cancel the packet event. <code>true</code> to cancel, <code>false</code> to do nothing.
     */
    protected boolean handlePositionSync(P packet, PlayerData playerData, int currentTick) {
        int entityID = processPositionSyncPacket(packet, playerData, currentTick);
        return cancelIfEnabledAndHidden(entityID, playerData);
    }
    /**
     * @return Whether or not to cancel the packet event. <code>true</code> to cancel, <code>false</code> to do nothing.
     */
    protected boolean handleEntityRotation(P packet, PlayerData playerData, int currentTick) {
        int entityID = processRotationPacket(packet, playerData, currentTick);
        return cancelIfEnabledAndHidden(entityID, playerData);
    }
    /**
     * @return Whether or not to cancel the packet event. <code>true</code> to cancel, <code>false</code> to do nothing.
     */
    protected boolean handleEntityHeadLook(P packet, PlayerData playerData, int currentTick) {
        int entityID = processHeadLookPacket(packet, playerData, currentTick);
        return cancelIfEnabledAndHidden(entityID, playerData);
    }
    /**
     * @return Whether or not to cancel the packet event. <code>true</code> to cancel, <code>false</code> to do nothing.
     */
    protected boolean handleEntityMetadata(P packet, int entityID, PlayerData playerData, int currentTick) {
        cachePacket(packet, entityID, playerData, currentTick);
        return cancelIfEnabledAndHidden(entityID, playerData);
    }

    /**
     * @return Whether or not to cancel the packet event. <code>true</code> to cancel, <code>false</code> to do nothing.
     */
    protected boolean handleRemoveEntityEffect(P packet, int entityID, PlayerData playerData, int currentTick) {
        cachePacket(packet, entityID, playerData, currentTick);
        return cancelIfEnabledAndHidden(entityID, playerData);
    }

    /**
     * @return Whether or not to cancel the packet event. <code>true</code> to cancel, <code>false</code> to do nothing.
     */
    @Packet(Packet.Packets.ENTITY_EQUIPMENT)
    protected boolean handleEntityEquipment(P packet, int entityID, PlayerData playerData, int currentTick) {
        cachePacket(packet, entityID, playerData, currentTick);
        return cancelIfEnabledAndHidden(entityID, playerData);
    }
    /**
     * @return Whether or not to cancel the packet event. <code>true</code> to cancel, <code>false</code> to do nothing.
     */
    protected boolean handleEntityVelocity(P packet, int entityID, PlayerData playerData, int currentTick) {
        processEntityVelocityPacket(packet, playerData, currentTick);
        cachePacket(packet, entityID, playerData, currentTick); //todo: may be wrong?
        return cancelIfEnabledAndHidden(entityID, playerData);
    }
    /**
     * @return Whether or not to cancel the packet event. <code>true</code> to cancel, <code>false</code> to do nothing.
     */
    protected boolean handleEntityEffect(P packet, int entityID, PlayerData playerData, int currentTick) {
        cachePacket(packet, entityID, playerData, currentTick);
        return cancelIfEnabledAndHidden(entityID, playerData);
    }
    /**
     * @return Whether or not to cancel the packet event. <code>true</code> to cancel, <code>false</code> to do nothing.
     */
    protected boolean handleEntityPassengers(int entityID, int[] passengers, PlayerData playerData, int currentTick) {
        int[] previousPassengers = playerData.nettyData().updatePassengers(entityID, passengers);
        clearRemovedPassengers(entityID, previousPassengers, passengers, playerData);
        NettyEntityLocatable<?,?> entity = playerData.trackedEntityFromID(entityID);
        if (entity == null) {
            forceManagedPassengersVisibleOnUntrackedVehicle(passengers, playerData, currentTick);
            return false;
        }
        return handleEntityPassengersNow(entity, passengers, playerData, currentTick);
    }

    //This (and leash handling) leaks some info to the client, as it will receive the passenger packet even if the passengers are auto-hidden once parsed, but as the packet doesn't include any location or type info, this shouldn't be too incriminating.
    boolean handleEntityPassengersNow(NettyEntityLocatable<?,?> entity, int[] passengers, PlayerData playerData, int currentTick) {
        int entityID = entity.entityID();
        entity.setPassengerIDs(passengers);
        for (int passengerID : passengers) {
            NettyEntityLocatable<?,?> passenger = playerData.trackedEntityFromID(passengerID);
            if (passenger != null) {
                passenger.setVehicleID(entityID);
            }
        }

        boolean hasCullTarget = entity.cullTarget();
        boolean incompleteGroup = false;
        for (int passengerID : passengers) {
            NettyEntityLocatable<?,?> passenger = playerData.trackedEntityFromID(passengerID);
            if (passenger == null) {
                incompleteGroup = true;
            } else if (passenger.cullTarget()) {
                hasCullTarget = true;
            }
        }

        if (hasCullTarget && incompleteGroup) {
            forceMountGroupVisible(entity, playerData, currentTick);
        } else if (hasCullTarget && hasMixedClientVisibility(entity, passengers, playerData)) {
            forceMountGroupVisible(entity, playerData, currentTick);
        } else if (!hasCullTarget && !entity.cullTarget()) {
            playerData.entityView().setVisibility(entity.entityUUID(), true, currentTick);
        }
        return cancelIfEnabledAndHidden(entityID, playerData) || hasHiddenPassenger(passengers, playerData);
    }

    protected void handleDestroyEntities(int[] entityIDs, PlayerData playerData, int currentTick) {
        for (int entityID : entityIDs) {
            clearMountRelationships(entityID, playerData, currentTick);
            clearPendingHolderReference(entityID, playerData);
            playerData.nettyData().removeUnresolvedLeashedEntityFromAll(entityID);
            playerData.nettyData().clearPendingPostSpawnTasksForEntity(entityID);
            EntityView<?> entityView = trackedViewFromEntityID(entityID, playerData);
            if (entityView == null) {
                continue;
            }
            Logger.debug("Removing entity from view due to destroy packet, entityID=" + entityID + " player=" + playerData.getPlayerUUID() + " tick=" + currentTick);
            entityView.removeEntity(entityID, currentTick);
        }
    }

    private void clearPendingHolderReference(int holderEntityID, PlayerData playerData) {
        int[] pendingLeashedEntityIDs = playerData.nettyData().consumeUnresolvedLeashes(holderEntityID);
        if (IntArrayList.isEmpty(pendingLeashedEntityIDs)) {
            return;
        }
        for (int leashedEntityID : pendingLeashedEntityIDs) {
            NettyEntityLocatable<?,?> leashedEntity = playerData.trackedEntityFromID(leashedEntityID);
            if (leashedEntity != null && leashedEntity.leashingEntity() == holderEntityID) {
                leashedEntity.setLeashingEntity(NO_LEASHER);
            }
        }
    }

    /**
     * @return Whether or not to cancel the packet event. <code>true</code> to cancel, <code>false</code> to do nothing.
     */
    protected boolean handleAttributeUpdate(P packet, int entityID, PlayerData playerData, int currentTick) {
        cachePacket(packet, entityID, playerData, currentTick);
        return cancelIfEnabledAndHidden(entityID, playerData);
    }

    /**
     * @return Whether or not to cancel the packet event. <code>true</code> to cancel, <code>false</code> to do nothing.
     */
    @Packet(Packet.Packets.LEASH_ENTITY)
    protected boolean handleLeashEntity(int leashedEntity, int leashingEntity, PlayerData playerData, int currentTick) {
        NettyEntityLocatable<?,?> leashed = playerData.trackedEntityFromID(leashedEntity);
        if (leashed == null) {
            if (isManagedEntity(leashingEntity, playerData)) return cancelIfEnabledAndHidden(leashingEntity, playerData);
            playerData.nettyData().addPostEntitySpawnTask(leashedEntity, new LeashReconciliationTask(playerData, leashedEntity, leashingEntity, currentTick));
            return false;
        }
        return handleLeashEntityNow(leashed, leashingEntity, playerData);
    }

    boolean handleLeashEntityNow(NettyEntityLocatable<?,?> leashedEntity, int leashingEntity, PlayerData playerData) {
        //Note, leashing entity ID will be -1 to unleash. From testing it sometimes seems to be 0?
        removeExistingLeashReference(leashedEntity.entityID(), leashedEntity, playerData);
        if (leashingEntity == -1 || leashingEntity == 0) {
            if (leashedEntity.leashingEntity() == NO_LEASHER) {
                Logger.warning("Entity was already unleashing when handling leash entity packet, leashedEntityID=" + leashedEntity + " for player: " + playerData.getPlayerUUID(), 4, PacketEntityViewController.class);
                return false;
            }
            leashedEntity.setLeashingEntity(NO_LEASHER);
            return cancelIfEnabledAndHidden(leashedEntity, playerData);
        }
        else {
            leashedEntity.setLeashingEntity(leashingEntity);
            NettyEntityLocatable<?,?> leashing = playerData.trackedEntityFromID(leashingEntity);
            if (leashing == null) {
                playerData.nettyData().addUnresolvedLeash(leashingEntity, leashedEntity.entityID());
                return cancelIfEnabledAndHidden(leashedEntity, playerData);
            }
            else {
                leashing.addLeashedEntity(leashedEntity.entityID());
                return cancelIfEnabledAndHidden(leashedEntity, playerData) || cancelIfEnabledAndHidden(leashingEntity, playerData);
            }
        }
    }

    private void removeExistingLeashReference(int leashedEntityID, NettyEntityLocatable<?,?> leashed, PlayerData playerData) {
        int previousLeashingEntityID = leashed.leashingEntity();
        if (previousLeashingEntityID == NO_LEASHER) {
            return;
        }
        if (playerData.nettyData().removeUnresolvedLeash(previousLeashingEntityID, leashedEntityID)) {
            return;
        }
        NettyEntityLocatable<?,?> previouslyLeashing = playerData.trackedEntityFromID(previousLeashingEntityID);
        if (previouslyLeashing == null) {
            return;
        }
        previouslyLeashing.removeLeashedEntity(leashedEntityID);
    }

    protected boolean isManagedEntity(int entityID, PlayerData playerData) {
        return playerData.trackedViewFromEntityID(entityID) != null;
    }

    private EntityView<?> trackedViewFromEntityID(int entityID, PlayerData playerData) {
        return playerData.trackedViewFromEntityID(entityID);
    }

    private boolean hasManagedEntity(int[] entityIDs, PlayerData playerData) {
        if (entityIDs == null) return false;
        for (int entityID : entityIDs) {
            if (isManagedEntity(entityID, playerData)) return true;
        }
        return false;
    }

    protected boolean hasManagedEntity(int entityID, int[] entityIDs, PlayerData playerData) {
        if (isManagedEntity(entityID, playerData)) return true;
        return hasManagedEntity(entityIDs, playerData);
    }

    protected RaycastConfig getCorrectConfig(EntityView<?> entityView) {
        if (entityView.isPlayerView()) {
            return playerConfig;
        } else {
            return entityConfig;
        }
    }

    /**
     * @return True if the packet should be suppressed
     */
    protected boolean cancelIfEnabledAndHidden(int entityID, PlayerData playerData) {
        if (playerData.hasBypassPermission()) {
            return false;
        }
        EntityView<?> entityView = trackedViewFromEntityID(entityID, playerData);

        if (entityView == null) {
            return false;
        }

        NettyEntityLocatable<?,?> entity = playerData.trackedEntityFromID(entityID);
        if (entity == null || entity.clientVisible()) {
            return false;
        }

        return getCorrectConfig(entityView).enabled(); // If this statement is reached, the entity should be hidden, so if the config is enabled it is hidden.
    }

    /**
     * @return True if the packet should be suppressed
     */
    protected boolean cancelIfEnabledAndHidden(NettyEntityLocatable<?,?> entity, PlayerData playerData) {
        if (playerData.hasBypassPermission() || entity.clientVisible()) {
            return false;
        }

        EntityView<?> entityView = trackedViewFromEntityID(entity.entityID(), playerData);
        return entityView != null && getCorrectConfig(entityView).enabled(); // If this statement is reached, the entity should be hidden, so if the config is enabled it is hidden.
    }

    /**
     * @return The created entity, with a default visibility of <code>true</code>. Does not insert the entity into any views, that is the responsibility of the caller.
     */
    protected abstract NettyEntityLocatable<?,?> processEntitySpawn(PlayerData playerData, P packet, UUID world, int currentTick);

    /**   @return The entity ID of the entity   */
    protected abstract int processRelativeMovePacket(P packet, PlayerData playerData, int currentTick);

    /**   @return The entity ID of the entity   */
    protected abstract int processRelativeMoveAndRotationPacket(P packet, PlayerData playerData, int currentTick);

    /**   @return The entity ID of the entity   */
    protected abstract int processTeleportPacket(P packet, PlayerData playerData, int currentTick);

    /**   @return The entity ID of the entity   */
    protected abstract int processPositionSyncPacket(P packet, PlayerData playerData, int currentTick);

    protected abstract void cachePacket(P packet, int entityID, PlayerData playerData, int currentTick);

    protected void reconcileEntityRelationships(NettyEntityLocatable<?,?> entity, PlayerData playerData) {
        entity.setVehicleID(playerData.nettyData().vehicleID(entity.entityID()));
        entity.setPassengerIDs(playerData.nettyData().passengerIDs(entity.entityID()));
    }

    protected boolean shouldForceEntityVisible(NettyEntityLocatable<?,?> entity, PlayerData playerData) {
        if (entity.vehicleID() != NO_VEHICLE && playerData.trackedEntityFromID(entity.vehicleID()) == null) {
            return true;
        }
        for (int passengerID : playerData.nettyData().passengerIDs(entity.entityID())) {
            if (playerData.trackedEntityFromID(passengerID) == null) {
                return true;
            }
        }
        return false;
    }
    /**   @return The entity ID of the entity   */
    protected abstract int processRotationPacket(P packet, PlayerData playerData, int currentTick);

    /**   @return The entity ID of the entity   */
    protected abstract int processHeadLookPacket(P packet, PlayerData playerData, int currentTick);

    /**   @return The entity ID of the entity   */
    protected abstract int processEntityVelocityPacket(P packet, PlayerData playerData, int currentTick);

    /**Silently sends the provided array of entities as passengers for the required vehicle.*/
    protected abstract void sendEntityPassengerPacket(int vehicle, ArrayList<Integer> passengers, PlayerData playerData);

    protected abstract void insertEntityToPlayerView(NettyEntityLocatable<?,?> entity, PlayerData playerData);

    protected abstract void insertEntityToEntityView(NettyEntityLocatable<?,?> entity, PlayerData playerData);

    private void clearRemovedPassengers(int vehicleID, int[] previousPassengers, int[] passengers, PlayerData playerData) {
        for (int previousPassengerID : previousPassengers) {
            if (contains(passengers, previousPassengerID)) {
                continue;
            }
            NettyEntityLocatable<?,?> previousPassenger = playerData.trackedEntityFromID(previousPassengerID);
            if (previousPassenger != null && previousPassenger.vehicleID() == vehicleID) {
                previousPassenger.setVehicleID(NO_VEHICLE);
            }
        }
    }

    private void forceManagedPassengersVisibleOnUntrackedVehicle(int[] passengers, PlayerData playerData, int currentTick) {
        for (int passengerID : passengers) {
            NettyEntityLocatable<?,?> passenger = playerData.trackedEntityFromID(passengerID);
            if (passenger == null) {
                continue;
            }
            passenger.setVehicleID(playerData.nettyData().vehicleID(passengerID));
            if (passenger.cullTarget()) {
                playerData.entityView().setVisibility(passenger.entityUUID(), true, currentTick);
            }
        }
    }

    private void forceMountGroupVisible(NettyEntityLocatable<?,?> entity, PlayerData playerData, int currentTick) {
        playerData.entityView().setVisibility(entity.entityUUID(), true, currentTick);
    }

    private boolean hasMixedClientVisibility(NettyEntityLocatable<?,?> vehicle, int[] passengers, PlayerData playerData) {
        boolean clientVisible = vehicle.clientVisible();
        for (int passengerID : passengers) {
            NettyEntityLocatable<?,?> passenger = playerData.trackedEntityFromID(passengerID);
            if (passenger != null && passenger.clientVisible() != clientVisible) {
                return true;
            }
        }
        return false;
    }

    private boolean hasHiddenPassenger(int[] passengers, PlayerData playerData) {
        for (int passengerID : passengers) {
            NettyEntityLocatable<?,?> passenger = playerData.trackedEntityFromID(passengerID);
            if (passenger != null && !passenger.clientVisible()) {
                return true;
            }
        }
        return false;
    }

    private void clearMountRelationships(int entityID, PlayerData playerData, int currentTick) {
        NettyData.MountRelationships relationships = playerData.nettyData().removeEntityRelationships(entityID);
        for (int passengerID : relationships.passengerIDs()) {
            NettyEntityLocatable<?,?> passenger = playerData.trackedEntityFromID(passengerID);
            if (passenger != null && passenger.vehicleID() == entityID) {
                passenger.setVehicleID(NO_VEHICLE);
                if (passenger.cullTarget()) {
                    playerData.entityView().setVisibility(passenger.entityUUID(), true, currentTick);
                }
            }
        }
        if (relationships.vehicleID() == NO_VEHICLE) {
            return;
        }
        NettyEntityLocatable<?,?> vehicle = playerData.trackedEntityFromID(relationships.vehicleID());
        if (vehicle != null) {
            vehicle.setPassengerIDs(playerData.nettyData().passengerIDs(relationships.vehicleID()));
            if (!vehicle.cullTarget() && !hasCullTargetPassenger(vehicle, playerData)) {
                playerData.entityView().setVisibility(vehicle.entityUUID(), true, currentTick);
            }
        }
    }

    private boolean hasCullTargetPassenger(NettyEntityLocatable<?,?> vehicle, PlayerData playerData) {
        int[] passengerIDs = vehicle.passengerIDs();
        if (passengerIDs == null) {
            return false;
        }
        for (int passengerID : passengerIDs) {
            NettyEntityLocatable<?,?> passenger = playerData.trackedEntityFromID(passengerID);
            if (passenger != null && passenger.cullTarget()) {
                return true;
            }
        }
        return false;
    }

    private boolean contains(int[] values, int target) {
        if (values == null) {
            return false;
        }
        for (int value : values) {
            if (value == target) {
                return true;
            }
        }
        return false;
    }
}
