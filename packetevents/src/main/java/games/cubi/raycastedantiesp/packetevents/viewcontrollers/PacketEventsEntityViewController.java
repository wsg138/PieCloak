package games.cubi.raycastedantiesp.packetevents.viewcontrollers;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.teleport.RelativeFlag;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import games.cubi.raycastedantiesp.core.config.ConfigManager;
import games.cubi.locatables.Locatable;
import games.cubi.logs.Logger;
import games.cubi.raycastedantiesp.core.locatables.NettyEntityLocatable;
import games.cubi.raycastedantiesp.core.players.PlayerData;
import games.cubi.raycastedantiesp.core.players.PlayerRegistry;
import games.cubi.raycastedantiesp.core.utils.PrimitiveIntArrayList;
import games.cubi.raycastedantiesp.core.view.EntityView;
import games.cubi.raycastedantiesp.core.view.EntityViewTransition;
import games.cubi.raycastedantiesp.core.view.controller.PacketEntityViewController;
import games.cubi.raycastedantiesp.packetevents.locatables.PacketEventsEntity;
import games.cubi.raycastedantiesp.packetevents.replaydata.PacketEventsEntityReplayData;
import games.cubi.raycastedantiesp.packetevents.target.PacketEventsTargetFilter;
import games.cubi.raycastedantiesp.packetevents.view.PacketEventsEntityView;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntSupplier;

import static games.cubi.raycastedantiesp.core.locatables.NettyEntityLocatable.NO_LEASHER;
import static games.cubi.raycastedantiesp.core.locatables.NettyEntityLocatable.NO_VEHICLE;

public abstract class PacketEventsEntityViewController extends PacketEntityViewController<PacketWrapper<?>> implements PacketListener {
    private final IntSupplier CURRENT_TICK_SUPPLIER;
    private final PacketEventsCommonViewController COMMON;
    private final PacketEventsTargetFilter targetFilter;
    private static PacketEventsEntityViewController SELF; //TODO Switch to LazyConstant once out of preview (see https://openjdk.org/jeps/526)

    public static PacketEventsEntityViewController get() {
        if (SELF == null) {
            SELF = (PacketEventsEntityViewController) PacketEntityViewController.get();
        }
        return SELF;
    }

    protected PacketEventsEntityViewController(IntSupplier currentTickSupplier, PacketEventsTargetFilter targetFilter) {
        this.CURRENT_TICK_SUPPLIER = currentTickSupplier;
        this.targetFilter = targetFilter == null ? PacketEventsTargetFilter.DISABLED : targetFilter;
        COMMON = PacketEventsCommonViewController.get(currentTickSupplier);
    }

    protected abstract UUID resolveWorldUUID(User user);

    public void removeViewer(UUID viewerUUID) {
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        UUID viewerUUID = event.getUser().getUUID();
        if (viewerUUID == null) {
            return;
        }

        PlayerData playerData = PlayerRegistry.getInstance().getPlayerData(viewerUUID);

        if (event.getPacketType() == PacketType.Login.Server.LOGIN_SUCCESS) {
            handleLoginPhaseLoginPacket(viewerUUID, CURRENT_TICK_SUPPLIER.getAsInt());
            return;
        }

        if (event.getPacketType() == PacketType.Play.Server.JOIN_GAME) {
            WrapperPlayServerJoinGame packet = new WrapperPlayServerJoinGame(event);
            handlePlayPhaseLoginPacket(packet.getEntityId(), viewerUUID, CURRENT_TICK_SUPPLIER.getAsInt());
        }

        if (playerData == null) {
            return;
        }

        if (ConfigManager.get().getEntityConfig() != entityConfig) {
            entityConfig = ConfigManager.get().getEntityConfig();
            hideOnSpawnEntityDistanceSquared = entityConfig.hideOnSpawnDistance() * entityConfig.hideOnSpawnDistance();
            alwaysShowEntityDistanceSquared = entityConfig.getAlwaysShowRadius() * entityConfig.getAlwaysShowRadius();
        }

        if (ConfigManager.get().getPlayerConfig() != playerConfig) {
            playerConfig = ConfigManager.get().getPlayerConfig();
            hideOnSpawnPlayerDistanceSquared = playerConfig.hideOnSpawnDistance() * playerConfig.hideOnSpawnDistance();
        }

        Locatable ownLocation = playerData.ownLocation();
        UUID world = ownLocation != null ? ownLocation.world() : resolveWorldUUID(event.getUser());
        int currentTick = CURRENT_TICK_SUPPLIER.getAsInt();

        if (playerData.hasBypassPermission()) {
            handleBypassPacketLifecycle(event, playerData, currentTick);
        } else {
            handleEntityPackets(event, event.getUser(), playerData, world, currentTick);
        }

        if (playerData.entityView().hasPendingTransitions()) {
            processEntityTransitions(viewerUUID, event.getUser(), cast(playerData.entityView()), playerData);
        }

        if (playerData.playerView().hasPendingTransitions()) {
            processEntityTransitions(viewerUUID, event.getUser(), cast(playerData.playerView()), playerData);
        }
        
        event.getUser().flushPackets();
        playerData.nettyData().evictPendingPostSpawnTasksIfRequired(currentTick);
    }

    public void enableBypass(PlayerData playerData, int currentTick) {
        for (UUID entityUUID : playerData.entityView().getKnownEntities()) {
            playerData.entityView().setVisibility(entityUUID, true, currentTick);
        }
    }

    private void handleBypassPacketLifecycle(PacketSendEvent event, PlayerData playerData, int currentTick) {
        if (event.getPacketType() == PacketType.Play.Server.DESTROY_ENTITIES) {
            handleDestroyEntities(new WrapperPlayServerDestroyEntities(event).getEntityIds(), playerData, currentTick);
        }
    }

    private void handleEntityPackets(PacketSendEvent event, User viewer, PlayerData playerData, UUID world, int currentTick) {
        //code readability helper constant
        final boolean REQUIRE_EVENT_CANCELLATION = true;
        switch (event.getPacketType()) {
            case PacketType.Play.Server.SPAWN_LIVING_ENTITY -> {
                Logger.error("Received spawn living entity packet. This packet type should not be used in modern Minecraft versions, and its presence likely indicates a protocol mapping issue. Viewer=" + viewer.getUUID() + " tick=" + currentTick, 2, PacketEventsEntityViewController.class);
                throw new IllegalStateException("Spawn Living Entity packet appeared. This shouldn't exist");
                //if (handleLivingEntitySpawn(new WrapperPlayServerSpawnLivingEntity(event), playerData, world, currentTick) == REQUIRE_EVENT_CANCELLATION)
                 //   event.setCancelled(true);
            }
            case PacketType.Play.Server.SPAWN_ENTITY -> {
                WrapperPlayServerSpawnEntity packet = new WrapperPlayServerSpawnEntity(event);
                Logger.debug("Spawning entity for player " + viewer.getUUID() + " entity #" + packet.getEntityId() + " tick=" + currentTick + " type=" + packet.getEntityType().getName());
                boolean isPlayer = packet.getEntityType().isInstanceOf(EntityTypes.PLAYER);
                boolean shouldCullEntity = targetFilter.shouldCullEntity(packet.getEntityType(), isPlayer);
                boolean shouldTrackEntity = shouldCullEntity || isMinecartLike(packet.getEntityType());
                if (handleEntitySpawn(packet, packet.getEntityId(), isPlayer, shouldCullEntity, shouldTrackEntity, playerData, world, currentTick) == REQUIRE_EVENT_CANCELLATION)
                    event.setCancelled(true);
            }
            case PacketType.Play.Server.ENTITY_ANIMATION -> {
                int entityID = new WrapperPlayServerEntityAnimation(event).getEntityId();
                if (isManagedEntity(entityID, playerData) && handleEntityAnimation(entityID, playerData) == REQUIRE_EVENT_CANCELLATION)
                    event.setCancelled(true);
            }
            case PacketType.Play.Server.ENTITY_STATUS -> {
                int entityID = new WrapperPlayServerEntityStatus(event).getEntityId();
                if (isManagedEntity(entityID, playerData) && handleEntityEvent(entityID, playerData) == REQUIRE_EVENT_CANCELLATION)
                    event.setCancelled(true);
            }
            case PacketType.Play.Server.HURT_ANIMATION -> {
                int entityID = new WrapperPlayServerHurtAnimation(event).getEntityId();
                if (isManagedEntity(entityID, playerData) && handleHurtAnimation(entityID, playerData) == REQUIRE_EVENT_CANCELLATION)
                    event.setCancelled(true);
            }
            case PacketType.Play.Server.SPAWN_PAINTING -> {
                throw new IllegalStateException("Spawn Painting packet appeared. This shouldn't exist");
                //if (handlePaintingSpawn(new WrapperPlayServerSpawnPainting(event), playerData, world, currentTick) == REQUIRE_EVENT_CANCELLATION)
                //    event.setCancelled(true);
            }
            case PacketType.Play.Server.SPAWN_PLAYER -> {
                Logger.error("Received spawn player entity packet. This packet type should not be used in modern Minecraft versions, and its presence likely indicates a protocol mapping issue. Viewer=" + viewer.getUUID() + " tick=" + currentTick, 2, PacketEventsEntityViewController.class);
                throw new IllegalStateException("Spawn Player packet appeared. This shouldn't exist");
                //if (handlePlayerSpawn(new WrapperPlayServerSpawnPlayer(event), playerData, world, currentTick) == REQUIRE_EVENT_CANCELLATION)
                //    event.setCancelled(true);
            }
            case PacketType.Play.Server.ENTITY_RELATIVE_MOVE -> {
                WrapperPlayServerEntityRelativeMove packet = new WrapperPlayServerEntityRelativeMove(event);
                if (isManagedEntity(packet.getEntityId(), playerData) && handleRelativeMove(packet, playerData, currentTick) == REQUIRE_EVENT_CANCELLATION)
                    event.setCancelled(true);
            }
            case PacketType.Play.Server.ENTITY_RELATIVE_MOVE_AND_ROTATION -> {
                WrapperPlayServerEntityRelativeMoveAndRotation packet = new WrapperPlayServerEntityRelativeMoveAndRotation(event);
                if (isManagedEntity(packet.getEntityId(), playerData) && handleRelativeMoveAndRotation(packet, playerData, currentTick) == REQUIRE_EVENT_CANCELLATION)
                    event.setCancelled(true);
            }
            case PacketType.Play.Server.ENTITY_TELEPORT -> {
                WrapperPlayServerEntityTeleport packet = new WrapperPlayServerEntityTeleport(event);
                if (isManagedEntity(packet.getEntityId(), playerData) && handleTeleport(packet, playerData, currentTick) == REQUIRE_EVENT_CANCELLATION)
                    event.setCancelled(true);
            }
            case PacketType.Play.Server.ENTITY_POSITION_SYNC -> {
                WrapperPlayServerEntityPositionSync packet = new WrapperPlayServerEntityPositionSync(event);
                if (isManagedEntity(packet.getId(), playerData) && handlePositionSync(packet, playerData, currentTick) == REQUIRE_EVENT_CANCELLATION)
                    event.setCancelled(true);
            }
            case PacketType.Play.Server.ENTITY_ROTATION -> {
                WrapperPlayServerEntityRotation packet = new WrapperPlayServerEntityRotation(event);
                if (isManagedEntity(packet.getEntityId(), playerData) && handleEntityRotation(packet, playerData, currentTick) == REQUIRE_EVENT_CANCELLATION)
                    event.setCancelled(true);
            }
            case PacketType.Play.Server.ENTITY_HEAD_LOOK -> {
                WrapperPlayServerEntityHeadLook packet = new WrapperPlayServerEntityHeadLook(event);
                if (isManagedEntity(packet.getEntityId(), playerData) && handleEntityHeadLook(packet, playerData, currentTick) == REQUIRE_EVENT_CANCELLATION)
                    event.setCancelled(true);
            }
            case PacketType.Play.Server.ENTITY_METADATA -> {
                WrapperPlayServerEntityMetadata packet = new WrapperPlayServerEntityMetadata(event);
                if (isManagedEntity(packet.getEntityId(), playerData) && handleEntityMetadata(packet, packet.getEntityId(), playerData, currentTick) == REQUIRE_EVENT_CANCELLATION)
                    event.setCancelled(true);
            }
            case PacketType.Play.Server.REMOVE_ENTITY_EFFECT -> {
                WrapperPlayServerRemoveEntityEffect packet = new WrapperPlayServerRemoveEntityEffect(event);
                if (isManagedEntity(packet.getEntityId(), playerData) && handleRemoveEntityEffect(packet, packet.getEntityId(), playerData, currentTick) == REQUIRE_EVENT_CANCELLATION)
                    event.setCancelled(true);
            }
            case PacketType.Play.Server.ENTITY_EQUIPMENT -> {
                WrapperPlayServerEntityEquipment packet = new WrapperPlayServerEntityEquipment(event);
                if (isManagedEntity(packet.getEntityId(), playerData) && handleEntityEquipment(packet, packet.getEntityId(), playerData, currentTick) == REQUIRE_EVENT_CANCELLATION)
                    event.setCancelled(true);
            }
            case PacketType.Play.Server.ENTITY_VELOCITY -> {
                WrapperPlayServerEntityVelocity packet = new WrapperPlayServerEntityVelocity(event);
                if (isManagedEntity(packet.getEntityId(), playerData) && handleEntityVelocity(packet, packet.getEntityId(), playerData, currentTick) == REQUIRE_EVENT_CANCELLATION)
                    event.setCancelled(true);
            }
            case PacketType.Play.Server.ENTITY_EFFECT -> {
                WrapperPlayServerEntityEffect packet = new WrapperPlayServerEntityEffect(event);
                if (isManagedEntity(packet.getEntityId(), playerData) && handleEntityEffect(packet, packet.getEntityId(), playerData, currentTick) == REQUIRE_EVENT_CANCELLATION)
                    event.setCancelled(true);
            }
            case PacketType.Play.Server.SET_PASSENGERS -> {
                WrapperPlayServerSetPassengers packet = new WrapperPlayServerSetPassengers(event);
                if (handleEntityPassengers(packet.getEntityId(), packet.getPassengers(), playerData, currentTick) == REQUIRE_EVENT_CANCELLATION)
                    event.setCancelled(true);
            }
            case PacketType.Play.Server.DESTROY_ENTITIES -> {
                handleDestroyEntities(new WrapperPlayServerDestroyEntities(event).getEntityIds(), playerData, currentTick);
            }
            case PacketType.Play.Server.UPDATE_ATTRIBUTES -> {
                WrapperPlayServerUpdateAttributes wrapper = new WrapperPlayServerUpdateAttributes(event);
                if (isManagedEntity(wrapper.getEntityId(), playerData) && handleAttributeUpdate(wrapper, wrapper.getEntityId(), playerData, currentTick) == REQUIRE_EVENT_CANCELLATION)
                    event.setCancelled(true);
            }
            case PacketType.Play.Server.ATTACH_ENTITY -> {
                WrapperPlayServerAttachEntity wrapper = new WrapperPlayServerAttachEntity(event);
                if ((isManagedEntity(wrapper.getAttachedId(), playerData) || isManagedEntity(wrapper.getHoldingId(), playerData)) && handleLeashEntity(wrapper.getAttachedId(), wrapper.getHoldingId(), playerData, currentTick) == REQUIRE_EVENT_CANCELLATION)
                    event.setCancelled(true);
            }
            default -> {}
        }
    }

    protected NettyEntityLocatable<?,?> createSelfEntity(PlayerData ownData, int entityID, UUID playerUUID) {
        return PacketEventsEntity.createSelfEntity(ownData, entityID, playerUUID);
    }

    @Override
    protected @NotNull NettyEntityLocatable<?,?> processEntitySpawn(PlayerData playerData, PacketWrapper<?> packetWrapper, UUID world, int currentTick) {
        WrapperPlayServerSpawnEntity packet = (WrapperPlayServerSpawnEntity) packetWrapper;
        if (packet.getUUID().isEmpty()) {
            Logger.errorAndReturn(new RuntimeException("Entity UUID null when handling spawn entity packet, id=" + packet.getEntityId() + " tick=" + currentTick), 2, PacketEventsEntityViewController.class);
            return null;
        }
        UUID entityUUID = packet.getUUID().get();

        PacketEventsEntity entity = trackEntitySpawn(playerData, entityUUID, packet.getEntityId(), world,
                packet.getPosition().getX(), packet.getPosition().getY(), packet.getPosition().getZ(), packet.getEntityType());
        Vector3d velocity = packet.getVelocity().orElseGet(Vector3d::zero);
        entity.setEntityData(packet.getData())
                .setYaw(packet.getYaw())
                .setPitch(packet.getPitch())
                .setHeadYaw(packet.getHeadYaw())
                .setVelocity(velocity.getX(), velocity.getY(), velocity.getZ());
        return entity;
    }

    @Override
    protected int processRelativeMovePacket(PacketWrapper<?> packetWrapper, PlayerData playerData, int currentTick) {
        WrapperPlayServerEntityRelativeMove packet = (WrapperPlayServerEntityRelativeMove) packetWrapper;
        int entityID = packet.getEntityId();

        NettyEntityLocatable<?,?> entity = playerData.trackedEntityFromID(entityID);
        if (entity == null) {
            return entityID;
        }
        entity.add(packet.getDeltaX(), packet.getDeltaY(), packet.getDeltaZ());
        entity.setOnGround(packet.isOnGround());

        return entityID;
    }

    @Override
    protected int processRelativeMoveAndRotationPacket(PacketWrapper<?> packet, PlayerData playerData, int currentTick) {
        WrapperPlayServerEntityRelativeMoveAndRotation packetWrapper = (WrapperPlayServerEntityRelativeMoveAndRotation) packet;
        int entityID = packetWrapper.getEntityId();

        NettyEntityLocatable<?,?> entity = playerData.trackedEntityFromID(entityID);
        if (entity == null) {
            return entityID;
        }
        entity.add(packetWrapper.getDeltaX(), packetWrapper.getDeltaY(), packetWrapper.getDeltaZ());
        entity.setYaw(packetWrapper.getYaw()).setPitch(packetWrapper.getPitch()).setOnGround(packetWrapper.isOnGround());

        return entityID;
    }

    @Override
    protected int processTeleportPacket(PacketWrapper<?> packet, PlayerData playerData, int currentTick) {
        WrapperPlayServerEntityTeleport packetWrapper = (WrapperPlayServerEntityTeleport) packet;
        int entityID = packetWrapper.getEntityId();

        NettyEntityLocatable<?,?> entity = playerData.trackedEntityFromID(entityID);
        if (entity == null) {
            return entityID;
        }
        Vector3d position = packetWrapper.getPosition();
        Vector3d velocity = packetWrapper.getDeltaMovement();
        entity.set(position.getX(), position.getY(), position.getZ());
        entity.setYaw(packetWrapper.getYaw()).setPitch(packetWrapper.getPitch()).setVelocity(velocity.x, velocity.y, velocity.z).setOnGround(packetWrapper.isOnGround());

        return entityID;
    }

    @Override
    protected int processPositionSyncPacket(PacketWrapper<?> packet, PlayerData playerData, int currentTick) {
        WrapperPlayServerEntityPositionSync packetWrapper = (WrapperPlayServerEntityPositionSync) packet;
        int entityID = packetWrapper.getId();

        NettyEntityLocatable<?,?> entity = playerData.trackedEntityFromID(entityID);
        if (entity == null) {
            return entityID;
        }
        Vector3d position = packetWrapper.getValues().getPosition();
        Vector3d velocity = packetWrapper.getValues().getDeltaMovement();
        entity.set(position.getX(), position.getY(), position.getZ());
        entity.setYaw(packetWrapper.getValues().getYaw()).setPitch(packetWrapper.getValues().getPitch()).setVelocity(velocity.x, velocity.y, velocity.z).setOnGround(packetWrapper.isOnGround());

        return entityID;
    }



    @Override
    protected void cachePacket(PacketWrapper<?> packet, int entityID, PlayerData playerData, int currentTick) {
        NettyEntityLocatable<?,?> entity = playerData.trackedEntityFromID(entityID);
        if (entity == null) {
            playerData.nettyData().addPostEntitySpawnTask(entityID, new PECacheablePacketReconciliationTask(playerData, entityID, packet, currentTick));
            return;
        }
        ensureReplayData((PacketEventsEntity) entity).addPacket(packet);
    }

    @Override
    protected int processRotationPacket(PacketWrapper<?> packet, PlayerData playerData, int currentTick) {
        WrapperPlayServerEntityRotation packetWrapper = (WrapperPlayServerEntityRotation) packet;
        int entityID = packetWrapper.getEntityId();

        NettyEntityLocatable<?,?> entity = playerData.trackedEntityFromID(entityID);
        if (entity == null) {
            return entityID;
        }
        entity.setYaw(packetWrapper.getYaw()).setPitch(packetWrapper.getPitch()).setOnGround(packetWrapper.isOnGround());

        return entityID;
    }

    @Override
    protected int processHeadLookPacket(PacketWrapper<?> packet, PlayerData playerData, int currentTick) {
        WrapperPlayServerEntityHeadLook packetWrapper = (WrapperPlayServerEntityHeadLook) packet;
        int entityID = packetWrapper.getEntityId();

        NettyEntityLocatable<?,?> entity = playerData.trackedEntityFromID(entityID);
        if (entity == null) {
            return entityID;
        }
        entity.setHeadYaw(packetWrapper.getHeadYaw());

        return entityID;
    }

    @Override
    protected int processEntityVelocityPacket(PacketWrapper<?> packet, PlayerData playerData, int currentTick) {
        WrapperPlayServerEntityVelocity packetWrapper = (WrapperPlayServerEntityVelocity) packet;
        int entityID = packetWrapper.getEntityId();

        NettyEntityLocatable<?,?> entity = playerData.trackedEntityFromID(entityID);
        if (entity == null) {
            return entityID;
        }
        entity.setVelocity(packetWrapper.getVelocity().getX(), packetWrapper.getVelocity().getY(), packetWrapper.getVelocity().getZ());

        return entityID;
    }

    @Override
    protected void sendEntityPassengerPacket(int vehicle, IntArrayList passengers, PlayerData playerData) {
        NettyEntityLocatable<?,?> entity = playerData.trackedEntityFromID(vehicle);
        if (entity == null) {
            return;
        }
        WrapperPlayServerSetPassengers packet = new WrapperPlayServerSetPassengers(vehicle, passengers.toIntArray());
        Object channel = PacketEvents.getAPI().getProtocolManager().getChannel(playerData.getPlayerUUID());
        PacketEvents.getAPI().getProtocolManager().getUser(channel).writePacketSilently(packet);
    }

    private PacketEventsEntity trackEntitySpawn(PlayerData playerData, UUID entityUUID, int entityID, UUID world, double x, double y, double z, EntityType entityType) {

        PacketEventsEntity entity = new PacketEventsEntity(playerData, world, x, y, z, entityID, entityUUID, false, entityType, true /*default value as this is handled in PacketEntityViewController*/);
        ensureReplayData(entity);
        return entity;
    }

    private void processEntityTransitions(UUID viewerUUID, User viewer, PacketEventsEntityView entityView, PlayerData playerData) {
        for (EntityViewTransition transition : entityView.drainTransitions()) {
            PacketEventsEntity entity = getTrackedEntity(entityView, transition.targetUUID());
            if (entity == null || entity.isSelfEntity() || isStaleTransition(entity, transition)) {
                continue;
            }
            try {
                switch (transition.type()) {
                    case HIDE -> hideMountGroup(viewer, entityView.getMountGroup(entity.entityUUID()));
                    case SHOW -> showMountGroup(viewer, playerData, entityView.getMountGroup(entity.entityUUID()));
                    default -> {
                    }
                }
            } catch (Exception exception) {
                EntityViewTransition retry = transition.retry();
                entityView.requeueTransition(retry);
                if (retry.attempts() == 1 || retry.attempts() % 20 == 0) {
                    Logger.error("Entity visibility transition failed and was queued for retry. viewer=" + viewerUUID
                            + " entityUUID=" + entity.entityUUID()
                            + " entityID=" + entity.entityID()
                            + " entityType=" + entityTypeName(entity)
                            + " transition=" + transition.type()
                            + " attempts=" + retry.attempts(), exception, 1, PacketEventsEntityViewController.class);
                }
            }
        }
    }

    private boolean isStaleTransition(PacketEventsEntity entity, EntityViewTransition transition) {
        return (transition.type() == EntityViewTransition.Type.SHOW && !entity.visible())
                || (transition.type() == EntityViewTransition.Type.HIDE && entity.visible());
    }

    private void hideMountGroup(User viewer, List<PacketEventsEntity> mountGroup) {
        int[] entityIDs = mountGroup.stream()
                .filter(PacketEventsEntity::clientVisible)
                .mapToInt(PacketEventsEntity::entityID)
                .filter(entityID -> entityID >= 0)
                .toArray();
        if (entityIDs.length == 0) {
            return;
        }
        viewer.writePacketSilently(new WrapperPlayServerDestroyEntities(entityIDs));
        for (PacketEventsEntity member : mountGroup) {
            member.setClientVisible(false);
        }
    }

    private void showMountGroup(User viewer, PlayerData playerData, List<PacketEventsEntity> mountGroup) {
        for (PacketEventsEntity member : mountGroup) {
            if (member.isSelfEntity() || member.clientVisible()) {
                continue;
            }
            sendEntityShow(viewer, playerData, member, ensureReplayData(member));
            member.setClientVisible(true);
        }
        replayPassengerState(viewer, playerData, mountGroup);
    }

    private PacketWrapper<?> buildSpawnPacket(PacketEventsEntity entity) {
        if (entity.isSelfEntity()) {
            Logger.errorAndReturn(new RuntimeException("Should not build spawn packet for self entity"), 1, PacketEventsEntityViewController.class);
            return null;
        }
        return new WrapperPlayServerSpawnEntity(
                    entity.entityID(),
                    Optional.of(entity.entityUUID()),
                    entity.entityType(),
                    new Vector3d(entity.x(), entity.y(), entity.z()),
                    entity.pitch(),
                    entity.yaw(),
                    entity.headYaw(),
                    entity.entityData(),
                    Optional.of(new Vector3d(entity.velocityX(), entity.velocityY(), entity.velocityZ()))
            );
    }

    private WrapperPlayServerSetPassengers buildPassengersPacket(NettyEntityLocatable<?,?> vehicle, PlayerData playerData) {
        if (vehicle == null) {
            return null;
        }
        int[] passengerIDs = vehicle.passengerIDs();
        if (passengerIDs == null || passengerIDs.length == 0) {
            return null;
        }
        IntArrayList visiblePassengerIDs = new IntArrayList(passengerIDs.length);
        for (int passengerID : passengerIDs) {
            NettyEntityLocatable<?,?> passenger = playerData.trackedEntityFromID(passengerID);
            if (passenger != null && passenger.clientVisible()) {
                visiblePassengerIDs.add(passengerID);
            }
        }
        return new WrapperPlayServerSetPassengers(vehicle.entityID(), visiblePassengerIDs.toIntArray());
    }

    private @Nullable WrapperPlayServerAttachEntity[] buildLeashPackets(PacketEventsEntity entity, PlayerData playerData) {
        int[] leashedIDs = entity.leashedEntityIDsOrNull();
        int leashingID = entity.leashingEntity();
        WrapperPlayServerAttachEntity leashingShow = null;
        if (leashingID != NO_LEASHER) {
            NettyEntityLocatable<?,?> leashHolder = playerData.trackedEntityFromID(leashingID);
            if (leashHolder != null && leashHolder.visible()) {
                leashingShow = new WrapperPlayServerAttachEntity(entity.entityID(), leashingID, true);
            }
        }
        if (leashedIDs == null || leashedIDs.length == 0) {
            return leashingShow == null ? null : new WrapperPlayServerAttachEntity[]{leashingShow};
        }
        WrapperPlayServerAttachEntity[] packets = new WrapperPlayServerAttachEntity[leashedIDs.length + (leashingShow == null ? 0 : 1)];
        int index = 0;
        if (leashingShow != null) {
            packets[0] = leashingShow;
            index = 1;
        }
        for (int leashedID : leashedIDs) {
            NettyEntityLocatable<?,?> leashHolder = playerData.trackedEntityFromID(leashedID);
            if (leashHolder != null && leashHolder.visible()) {
                packets[index] = new WrapperPlayServerAttachEntity(leashedID, entity.entityID(), true);
                index++;
            }
        }
        return packets;
    }

    @SuppressWarnings("unchecked")
    public  <T> T cast(Object value) {
        return (T) value;
    }

    private PacketEventsEntity getTrackedEntity(EntityView<PacketEventsEntity> entityView, UUID entityUUID) {
        return entityView.getEntity(entityUUID);
    }

    private PacketEventsEntity getTrackedEntity(EntityView<PacketEventsEntity> entityView, int entityID) {
        return entityView.getEntity(entityID);
    }

    PacketEventsEntityReplayData ensureReplayData(PacketEventsEntity entity) {
        PacketEventsEntityReplayData replayData = entity.packetReplayData();
        if (replayData == null) {
            replayData = PacketEventsEntityReplayData.create();
            entity.setPacketReplayData(replayData);
        }
        return replayData;
    }

    private void sendEntityShow(User viewer, PlayerData data, PacketEventsEntity entity, PacketEventsEntityReplayData replayData) {
        viewer.writePacketSilently(buildSpawnPacket(entity));
        sendEntityAbsoluteCorrection(viewer, entity);

        for (PacketWrapper<?> cachedPacket : replayData.snapshotPackets(entity.entityID())) {
            viewer.writePacketSilently(cachedPacket);
        }
        WrapperPlayServerAttachEntity[] leashPackets = buildLeashPackets(entity, data);
        if (leashPackets == null) return;
        for (WrapperPlayServerAttachEntity leashPacket : leashPackets) {
            if (leashPacket != null) {
                viewer.writePacketSilently(leashPacket);
            }
        }
    }

    private void replayPassengerState(User viewer, PlayerData playerData, List<PacketEventsEntity> mountGroup) {
        for (PacketEventsEntity member : mountGroup) {
            COMMON.writeIfPresent(viewer, buildPassengersPacket(member, playerData));
        }
        if (!mountGroup.isEmpty() && mountGroup.getFirst().vehicleID() != NO_VEHICLE) {
            COMMON.writeIfPresent(viewer, buildPassengersPacket(
                    playerData.trackedEntityFromID(mountGroup.getFirst().vehicleID()),
                    playerData
            ));
        }
    }

    private void sendEntityAbsoluteCorrection(User viewer, PacketEventsEntity entity) {
        if (entity.entityID() < 0) {
            return;
        }
        viewer.writePacketSilently(new WrapperPlayServerEntityTeleport(
                entity.entityID(),
                new Vector3d(entity.x(), entity.y(), entity.z()),
                new Vector3d(entity.velocityX(), entity.velocityY(), entity.velocityZ()),
                entity.yaw(),
                entity.pitch(),
                RelativeFlag.NONE,
                entity.onGround()
        ));
        viewer.writePacketSilently(new WrapperPlayServerEntityHeadLook(entity.entityID(), entity.headYaw()));
    }

    protected void insertEntityToPlayerView(NettyEntityLocatable<?,?> entity, PlayerData playerData) {
        playerData.playerView().insertEntity(entity.cast());
        // Passenger relationships can arrive before spawn/pairing completes, so resolve them as soon as the entity becomes known.
        reconcileUnresolvedPassengers(entity, playerData);
        reconcileUnresolvedLeashes(entity, playerData);
    }

    protected void insertEntityToEntityView(NettyEntityLocatable<?,?> entity, PlayerData playerData) {
        playerData.entityView().insertEntity(entity.cast()); //todo: no need to put here, move to abstract packet view controller
        // Passenger relationships can arrive before spawn/pairing completes, so resolve them as soon as the entity becomes known.
        reconcileUnresolvedPassengers(entity, playerData);
        reconcileUnresolvedLeashes(entity, playerData);
    }

    private void reconcileUnresolvedLeashes(NettyEntityLocatable<?,?> insertedEntity, PlayerData playerData) {
        int[] pendingLeashedEntityIDs = playerData.nettyData().consumeUnresolvedLeashes(insertedEntity.entityID());
        if (PrimitiveIntArrayList.isEmpty(pendingLeashedEntityIDs)) {
            return;
        }
        for (int leashedEntityID : pendingLeashedEntityIDs) {
            NettyEntityLocatable<?,?> leashedEntity = playerData.trackedEntityFromID(leashedEntityID);
            if (leashedEntity == null || leashedEntity.leashingEntity() != insertedEntity.entityID()) {
                continue;
            }
            insertedEntity.addLeashedEntity(leashedEntityID);
        }
        if (!insertedEntity.clientVisible()) {
            return;
        }
        WrapperPlayServerAttachEntity[] leashPackets = buildLeashPackets((PacketEventsEntity) insertedEntity, playerData);
        if (leashPackets == null) {
            return;
        }
        Object channel = PacketEvents.getAPI().getProtocolManager().getChannel(playerData.getPlayerUUID());
        User viewer = PacketEvents.getAPI().getProtocolManager().getUser(channel);
        for (WrapperPlayServerAttachEntity leashPacket : leashPackets) {
            if (leashPacket != null) {
                viewer.writePacketSilently(leashPacket);
            }
        }
    }

    private boolean isMinecartLike(EntityType entityType) {
        return entityType != null && entityType.isInstanceOf(EntityTypes.MINECART_ABSTRACT);
    }

    private String entityTypeName(PacketEventsEntity entity) {
        return entity.entityType() == null ? "unknown" : "" + entity.entityType().getName();
    }
}
