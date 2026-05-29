package games.cubi.raycastedantiesp.packetevents.viewcontrollers;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.world.blockentity.BlockEntityType;
import com.github.retrooper.packetevents.protocol.world.blockentity.BlockEntityTypes;
import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.protocol.world.chunk.TileEntity;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import games.cubi.locatables.BlockLocatable;
import games.cubi.locatables.Locatable;
import games.cubi.locatables.implementations.ImmutableBlockLocatable;
import games.cubi.logs.Logger;
import games.cubi.raycastedantiesp.core.config.ConfigManager;
import games.cubi.raycastedantiesp.core.config.raycast.TileEntityConfig;
import games.cubi.raycastedantiesp.core.debug.VisibilityTraceService;
import games.cubi.raycastedantiesp.core.players.PlayerData;
import games.cubi.raycastedantiesp.core.players.PlayerRegistry;
import games.cubi.raycastedantiesp.core.view.BlockView;
import games.cubi.raycastedantiesp.core.view.BlockViewTransition;
import games.cubi.raycastedantiesp.core.locatables.TileEntityLocatable;
import games.cubi.raycastedantiesp.packetevents.BlockInfoResolver;
import games.cubi.raycastedantiesp.packetevents.replaydata.PacketEventsTileEntityReplayData;
import games.cubi.raycastedantiesp.packetevents.target.PacketEventsTargetFilter;

import java.util.*;
import java.util.function.IntSupplier;

import static games.cubi.raycastedantiesp.core.view.AbstractBlockView.CHUNK_SIZE;
import static games.cubi.raycastedantiesp.core.view.AbstractBlockView.pack;

public abstract class PacketEventsBlockViewController implements PacketListener {
    private final BlockInfoResolver blockInfoResolver;
    private final PacketEventsTargetFilter targetFilter;
    private final IntSupplier currentTickSupplier;
    private final PacketEventsCommonViewController common;
    private TileEntityConfig tileEntityConfig = null;
    private int hideOnSpawnDistanceSquared = 0;

    protected PacketEventsBlockViewController(BlockInfoResolver blockInfoResolver, PacketEventsTargetFilter targetFilter, IntSupplier currentTickSupplier) {
        this.blockInfoResolver = blockInfoResolver;
        this.targetFilter = targetFilter == null ? PacketEventsTargetFilter.DISABLED : targetFilter;
        this.currentTickSupplier = currentTickSupplier;
        common = PacketEventsCommonViewController.get(currentTickSupplier);
    }

    protected abstract UUID resolveWorldUUID(User user);

    protected abstract int getHiddenBlockId(int blockY);

    public void removeViewer(UUID viewerUUID) {
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        UUID viewerUUID = event.getUser().getUUID();
        if (viewerUUID == null) {
            return;
        }

        if (!(ConfigManager.get().getTileEntityConfig() == tileEntityConfig)) {
            tileEntityConfig = ConfigManager.get().getTileEntityConfig();
            hideOnSpawnDistanceSquared = tileEntityConfig.hideOnSpawnDistance() * tileEntityConfig.hideOnSpawnDistance();
        }

        PlayerData playerData = PlayerRegistry.getInstance().getPlayerData(viewerUUID);
        if (playerData == null) {
            return;
        }

        Locatable ownLocation = playerData.ownLocation();
        UUID world = ownLocation != null ? ownLocation.world() : resolveWorldUUID(event.getUser());
        int currentTick = currentTickSupplier.getAsInt();

        boolean handledWorldStatePacket = handleBlockPackets(event, event.getUser(), playerData, world, currentTick);

        if (!handledWorldStatePacket && playerData.blockView().hasPendingTransitions()) {
            processTileEntityTransitions(event.getUser(), playerData.blockView());
        }
        event.getUser().flushPackets();
    }

    private boolean handleBlockPackets(PacketSendEvent event, User viewer, PlayerData playerData, UUID world, int currentTick) {
        if (world == null) {
            return false;
        }

        BlockView blockView = playerData.blockView();

        if (event.getPacketType() == PacketType.Play.Server.UNLOAD_CHUNK) {
            WrapperPlayServerUnloadChunk packet = new WrapperPlayServerUnloadChunk(event);
            removeChunk(packet, blockView, world);
            return true;
        } else if (event.getPacketType() == PacketType.Play.Server.BLOCK_CHANGE) {
            WrapperPlayServerBlockChange packet = new WrapperPlayServerBlockChange(event);
            handleSingleBlockChange(event, viewer, playerData, world, packet, currentTick);
            return true;
        } else if (event.getPacketType() == PacketType.Play.Server.MULTI_BLOCK_CHANGE) {
            WrapperPlayServerMultiBlockChange packet = new WrapperPlayServerMultiBlockChange(event);
            handleMultiBlockChange(event, playerData, blockView, world, packet, playerData.ownLocation(), currentTick);
            return true;
        } else if (event.getPacketType() == PacketType.Play.Server.BLOCK_ENTITY_DATA) {
            WrapperPlayServerBlockEntityData packet = new WrapperPlayServerBlockEntityData(event);
            ImmutableBlockLocatable location = new ImmutableBlockLocatable(world, packet.getPosition().getX(), packet.getPosition().getY(), packet.getPosition().getZ());
            TileEntityLocatable<PacketEventsTileEntityReplayData> tileEntity = getTrackedTileEntity(blockView, location);
            if (tileEntity == null) {
                if (targetFilter.shouldCullBlockEntity(packet.getBlockEntityType())) {
                    event.setCancelled(true);
                    sendHiddenBlock(viewer, location);
                }
                return true;
            }
            ensureTileReplayData(tileEntity).setBlockEntityData(packet.getBlockEntityType(), packet.getNBT());
            if (!blockView.isVisible(location, currentTick)) {
                event.setCancelled(true);
                sendHiddenBlock(viewer, location);
            }
            return true;
        } else if (event.getPacketType() == PacketType.Play.Server.CHUNK_DATA) {
            WrapperPlayServerChunkData packet = new WrapperPlayServerChunkData(event);
            Column column = ingestChunkAndSetTileEntitiesToHiddenBlocks(
                    playerData,
                    world,
                    packet.getColumn().getX(),
                    packet.getColumn().getZ(),
                    packet.getColumn(),
                    event.getUser().getMinWorldHeight() >> 4,
                    event
            );
            packet.setColumn(column);
            event.markForReEncode(true);
            return true;

        } else if (event.getPacketType() == PacketType.Play.Server.MAP_CHUNK_BULK) {
            WrapperPlayServerChunkDataBulk packet = new WrapperPlayServerChunkDataBulk(event);
            throw new RuntimeException("I didn't think this packet existed. Please report this to the developer with details on how to reproduce it so it can be implemented");
        }
        return false;
    }

    private void removeChunk(WrapperPlayServerUnloadChunk packet, BlockView blockView, UUID world) {
        blockView.removeChunk(world, packet.getChunkX(), packet.getChunkZ());
        removeChunkTileEntities(blockView, world, packet.getChunkX(), packet.getChunkZ());
    }

    private void handleMultiBlockChange(PacketSendEvent event, PlayerData playerData, BlockView blockView, UUID world, WrapperPlayServerMultiBlockChange packet, Locatable playerLocation, int currentTick) {
        for (WrapperPlayServerMultiBlockChange.EncodedBlock change : packet.getBlocks()) {
            int blockID = change.getBlockId();
            boolean occluding = blockID != 0 && blockInfoResolver.isOccluding(blockID);
            boolean tileEntity = blockInfoResolver.isTileEntity(blockID) && targetFilter.shouldCullBlockState(blockID);
            blockView.upsertBlock(world, change.getX(), change.getY(), change.getZ(), occluding);
            ImmutableBlockLocatable location = new ImmutableBlockLocatable(world, change.getX(), change.getY(), change.getZ());
            if (tileEntity) {
                TileEntityLocatable<?> existing = blockView.getTrackedTileEntity(location);
                if (existing == null || existing.blockID() != blockID) {
                    double distanceSquared = location.distanceSquared(playerLocation);
                    blockView.insertTileEntity(location, blockID, ((distanceSquared <= hideOnSpawnDistanceSquared) && tileEntityConfig.enabled()));
                }
                if (!blockView.isVisible(location, currentTick)) {
                    change.setBlockId(getHiddenBlockId(location.blockY()));
                    VisibilityTraceService.get().recordBlockPacket(
                            playerData.getPlayerUUID(),
                            location,
                            "MULTI_BLOCK_CHANGE_REPLACED_WITH_HIDDEN_BLOCK",
                            blockID,
                            "hiddenBlockID=" + getHiddenBlockId(location.blockY())
                    );
                    event.markForReEncode(true);
                }
            } else {
                blockView.removeTileEntity(location);
            }
        }
    }

    private void processTileEntityTransitions(User viewer, BlockView blockView) {
        for (BlockViewTransition transition : blockView.drainTransitions()) {
            BlockLocatable location = transition.location();
            TileEntityLocatable<PacketEventsTileEntityReplayData> state = getTrackedTileEntity(blockView, location);

            switch (transition.type()) {
                case HIDE -> viewer.writePacketSilently(new WrapperPlayServerBlockChange(
                        new Vector3i(location.blockX(), location.blockY(), location.blockZ()),
                        getHiddenBlockId(location.blockY())
                ));
                case SHOW -> {
                    if (state == null || state.blockID() == 0) {
                        continue;
                    }
                    viewer.writePacketSilently(new WrapperPlayServerBlockChange(
                            new Vector3i(location.blockX(), location.blockY(), location.blockZ()),
                            state.blockID()
                    ));
                    PacketEventsTileEntityReplayData replayData = ensureTileReplayData(state);
                    if (replayData.blockEntityType() != null && replayData.nbt() != null) {
                        viewer.writePacketSilently(buildBlockEntityDataPacket(location, replayData));
                    }
                }
            }
            VisibilityTraceService.get().recordBlockPacket(
                    viewer.getUUID(),
                    location,
                    "TRANSITION_" + transition.type(),
                    state == null ? -1 : state.blockID(),
                    "hiddenBlockID=" + getHiddenBlockId(location.blockY())
            );
        }
    }

    private void handleSingleBlockChange(PacketSendEvent event, User viewer, PlayerData playerData, UUID world, WrapperPlayServerBlockChange packet, int currentTick) {
        int blockID = packet.getBlockId();
        boolean occluding = blockID != 0 && blockInfoResolver.isOccluding(blockID);
        boolean tileEntity = blockInfoResolver.isTileEntity(blockID) && targetFilter.shouldCullBlockState(blockID);
        Vector3i position = packet.getBlockPosition();
        ImmutableBlockLocatable location = new ImmutableBlockLocatable(world, position.getX(), position.getY(), position.getZ());

        playerData.blockView().upsertBlock(world, position.getX(), position.getY(), position.getZ(), occluding);
        if (tileEntity) {
            TileEntityLocatable<?> existing = playerData.blockView().getTrackedTileEntity(location);
            if (existing == null || existing.blockID() != blockID) {
                double distanceSquared = location.distanceSquared(playerData.ownLocation());
                playerData.blockView().insertTileEntity(location, blockID, ((distanceSquared <= hideOnSpawnDistanceSquared) && tileEntityConfig.enabled()));
            }
            if (!playerData.blockView().isVisible(location, currentTick)) {
                event.setCancelled(true);
                sendHiddenBlock(viewer, location);
                VisibilityTraceService.get().recordBlockPacket(playerData.getPlayerUUID(), location, "BLOCK_CHANGE_REPLACED_WITH_HIDDEN_BLOCK", blockID, "hiddenBlockID=" + getHiddenBlockId(location.blockY()));
            }
        } else {
            playerData.blockView().removeTileEntity(location);
        }
    }

    private Column ingestChunkAndSetTileEntitiesToHiddenBlocks(
            PlayerData playerData,
            UUID worldID,
            int chunkX,
            int chunkZ,
            Column column,
            int minimumChunkSectionY,
            PacketSendEvent event
    ) {
        Map<Integer, BitSet> occludingBySectionY = new HashMap<>();
        Set<Integer> targetTileEntitySectionYs = new HashSet<>();
        BlockView blockView = playerData.blockView();

        BaseChunk[] sections = column.getChunks();
        TileEntity[] chunkTileEntitiesData = column.getTileEntities();
        List<TileEntity> visibleTileEntities = chunkTileEntitiesData == null ? new ArrayList<>() : new ArrayList<>(chunkTileEntitiesData.length);

        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            BaseChunk section = sections[sectionIndex];
            if (section == null) {
                continue;
            }

            int sectionY = minimumChunkSectionY + sectionIndex;
            BitSet occluding = null;

            boolean chunkSectionHasOccluding = false;

            for (int localX = 0; localX < 16; localX++) {
                for (int localY = 0; localY < 16; localY++) {
                    for (int localZ = 0; localZ < 16; localZ++) {
                        int blockID = section.getBlockId(localX, localY, localZ);
                        if (blockID == 0) {
                            continue;
                        }

                        int blockX = (chunkX << 4) + localX;
                        int blockY = (sectionY << 4) + localY;
                        int blockZ = (chunkZ << 4) + localZ;

                        if (blockInfoResolver.isOccluding(blockID)) {
                            if (occluding == null) {
                                occluding = new BitSet(CHUNK_SIZE * CHUNK_SIZE * CHUNK_SIZE);
                            }
                            occluding.set(pack(localX, localY, localZ));
                            chunkSectionHasOccluding = true;
                        }
                        if (blockInfoResolver.isTileEntity(blockID) && targetFilter.shouldCullBlockState(blockID)) {
                            ImmutableBlockLocatable location = new ImmutableBlockLocatable(worldID, blockX, blockY, blockZ);
                            targetTileEntitySectionYs.add(sectionY);
                            TileEntityLocatable<?> existing = blockView.getTrackedTileEntity(location);
                            if (existing == null) {
                                blockView.insertTileEntityIfAbsent(location, blockID, false);
                                section.set(localX, localY, localZ, getHiddenBlockId(blockY));
                                VisibilityTraceService.get().recordBlockPacket(playerData.getPlayerUUID(), location, "CHUNK_DATA_REPLACED_WITH_HIDDEN_BLOCK", blockID, "hiddenBlockID=" + getHiddenBlockId(blockY) + " existingVisible=false");
                            } else if (!existing.visible()) {
                                section.set(localX, localY, localZ, getHiddenBlockId(blockY));
                                VisibilityTraceService.get().recordBlockPacket(playerData.getPlayerUUID(), location, "CHUNK_DATA_REPLACED_WITH_HIDDEN_BLOCK", blockID, "hiddenBlockID=" + getHiddenBlockId(blockY) + " existingVisible=false");
                            } else {
                                VisibilityTraceService.get().recordBlockPacket(playerData.getPlayerUUID(), location, "CHUNK_DATA_LEFT_VISIBLE", blockID, "existingVisible=true");
                            }
                        }
                    }
                }
            }
            if (chunkSectionHasOccluding) {
                // skip empty sections to save memory
                occludingBySectionY.put(sectionY, occluding);
            }
        }

        if (chunkTileEntitiesData != null) {
            for (TileEntity tileEntity : chunkTileEntitiesData) {
                int blockX = (chunkX << 4) + tileEntity.getX();
                int blockY = tileEntity.getY();
                int blockZ = (chunkZ << 4) + tileEntity.getZ();
                int sectionY = blockY >> 4;
                ImmutableBlockLocatable location = new ImmutableBlockLocatable(worldID, blockX, blockY, blockZ);
                TileEntityLocatable<PacketEventsTileEntityReplayData> state = getTrackedTileEntity(blockView, location);

                if (state == null) {
                    int sectionIndex = sectionY - minimumChunkSectionY;
                    if (sectionIndex < 0 || sectionIndex >= sections.length) {
                        if (!targetFilter.shouldCullBlockEntity(packetTileEntityType(tileEntity))) {
                            visibleTileEntities.add(tileEntity);
                        }
                        continue;
                    }

                    BaseChunk sourceSection = sections[sectionIndex];
                    if (sourceSection == null) {
                        if (!targetFilter.shouldCullBlockEntity(packetTileEntityType(tileEntity))) {
                            visibleTileEntities.add(tileEntity);
                        }
                        continue;
                    }

                    int blockID = sourceSection.getBlockId(tileEntity.getX(), blockY & 15, tileEntity.getZ());
                    if (blockID <= 0) {
                        if (!targetFilter.shouldCullBlockEntity(packetTileEntityType(tileEntity))) {
                            visibleTileEntities.add(tileEntity);
                        }
                        continue;
                    }

                    if (!blockInfoResolver.isTileEntity(blockID) || !targetFilter.shouldCullBlockState(blockID)) {
                        visibleTileEntities.add(tileEntity);
                        continue;
                    }

                    blockView.insertTileEntityIfAbsent(location, blockID, false);
                    targetTileEntitySectionYs.add(sectionY);
                    sourceSection.set(tileEntity.getX(), blockY & 15, tileEntity.getZ(), getHiddenBlockId(blockY));
                    VisibilityTraceService.get().recordBlockPacket(playerData.getPlayerUUID(), location, "CHUNK_DATA_TILE_ENTITY_RECOVERY_REPLACED_WITH_HIDDEN_BLOCK", blockID, "hiddenBlockID=" + getHiddenBlockId(blockY));
                    state = getTrackedTileEntity(blockView, location);
                    if (state == null) {
                        Logger.warning("Skipping uncached chunk block entity because caching recovery failed. Location: " + location.world() + " " + location.blockX() + "," + location.blockY() + "," + location.blockZ(), 3, PacketEventsBlockViewController.class);
                        visibleTileEntities.add(tileEntity);
                        continue;
                    }
                }
                ensureTileReplayData(state).setBlockEntityData(packetTileEntityType(tileEntity), tileEntity.getNBT());
                if (state.visible()) {
                    visibleTileEntities.add(tileEntity);
                    VisibilityTraceService.get().recordBlockPacket(playerData.getPlayerUUID(), location, "CHUNK_DATA_INCLUDED_VISIBLE_BLOCK_ENTITY_DATA", state.blockID(), "blockEntityType=" + packetTileEntityType(tileEntity).getName());
                }
            }
        }

        Set<Integer> sectionYs = new HashSet<>(targetTileEntitySectionYs);
        sectionYs.addAll(occludingBySectionY.keySet());
        for (int sectionY : sectionYs) {
            BitSet occluding = occludingBySectionY.get(sectionY);
            if (occluding != null) {
                blockView.replaceChunkSection(worldID, chunkX, sectionY, chunkZ, occluding);
            }
            else {
                blockView.removeChunkSection(worldID, chunkX, sectionY, chunkZ);
            }
        }

        return copyColumnWithTileEntities(column, visibleTileEntities.toArray(TileEntity[]::new));
    }

    private void removeChunkTileEntities(BlockView blockView, UUID worldID, int chunkX, int chunkZ) {
        for (BlockLocatable known : blockView.getKnownTileEntities()) {
            if (!sameChunk(known, worldID, chunkX, chunkZ)) {
                continue;
            }
            blockView.removeTileEntity(known);
        }
    }

    private void sendHiddenBlock(User viewer, BlockLocatable location) {
        viewer.writePacketSilently(new WrapperPlayServerBlockChange(
                new Vector3i(location.blockX(), location.blockY(), location.blockZ()),
                getHiddenBlockId(location.blockY())
        ));
    }

    private boolean sameChunk(BlockLocatable location, UUID worldID, int chunkX, int chunkZ) {
        return location.world().equals(worldID) && location.chunkX() == chunkX && location.chunkZ() == chunkZ;
    }

    private boolean sameChunkSection(BlockLocatable location, UUID worldID, int chunkX, int chunkY, int chunkZ) {
        return sameChunk(location, worldID, chunkX, chunkZ) && location.chunkY() == chunkY;
    }

    private WrapperPlayServerBlockEntityData copyBlockEntityDataPacket(WrapperPlayServerBlockEntityData packet) {
        return new WrapperPlayServerBlockEntityData(
                copyBlockVector(packet.getPosition()),
                packet.getBlockEntityType(),
                packet.getNBT()
        );
    }

    private WrapperPlayServerBlockEntityData buildBlockEntityDataPacket(BlockLocatable location, PacketEventsTileEntityReplayData replayData) {
        return new WrapperPlayServerBlockEntityData(
                new Vector3i(location.blockX(), location.blockY(), location.blockZ()),
                replayData.blockEntityType(),
                replayData.nbt()
        );
    }

    private Column copyColumnWithTileEntities(Column column, TileEntity[] tileEntities) {
        if (column.hasBiomeData()) {
            int[] biomeInts = column.getBiomeDataInts();
            byte[] biomeBytes = column.getBiomeDataBytes();
            if (biomeInts.length >= biomeBytes.length) {
                return new Column(column.getX(), column.getZ(), column.isFullChunk(), column.getChunks(), tileEntities, column.getHeightMaps(), biomeInts);
            }
            return new Column(column.getX(), column.getZ(), column.isFullChunk(), column.getChunks(), tileEntities, column.getHeightMaps(), biomeBytes);
        }
        if (common.v_1_21_5_orAbove) {
            return new Column(column.getX(), column.getZ(), column.isFullChunk(), column.getChunks(), tileEntities, column.getHeightmaps());
        }
        return new Column(column.getX(), column.getZ(), column.isFullChunk(), column.getChunks(), tileEntities, column.getHeightMaps());
    }

    private BlockEntityType packetTileEntityType(TileEntity tileEntity) {
        return BlockEntityTypes.getById(
                PacketEvents.getAPI().getServerManager().getVersion().toClientVersion(),
                tileEntity.getType()
        );
    }

    private Vector3i copyBlockVector(Vector3i vector) {
        return new Vector3i(vector.getX(), vector.getY(), vector.getZ());
    }

    @SuppressWarnings("unchecked")
    private TileEntityLocatable<PacketEventsTileEntityReplayData> getTrackedTileEntity(BlockView blockView, BlockLocatable location) {
        return (TileEntityLocatable<PacketEventsTileEntityReplayData>) blockView.getTrackedTileEntity(location);
    }

    private PacketEventsTileEntityReplayData ensureTileReplayData(TileEntityLocatable<PacketEventsTileEntityReplayData> tileEntity) {
        PacketEventsTileEntityReplayData replayData = tileEntity.extraData();
        if (replayData == null) {
            replayData = new PacketEventsTileEntityReplayData();
            tileEntity.setExtraData(replayData);
        }
        return replayData;
    }
}
