package games.cubi.raycastedantiesp.packetevents.viewcontrollers;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.event.UserDisconnectEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockEntityData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUnloadChunk;
import games.cubi.locatables.api.BlockSpatial;
import games.cubi.locatables.api.Locatable;
import games.cubi.locatables.implementations.ImmutableBlockSpatialImpl;
import games.cubi.locatables.implementations.MutableBlockSpatialImpl;
import games.cubi.logs.Logger;
import games.cubi.raycastedantiesp.core.chunks.BlockInfoResolver;
import games.cubi.raycastedantiesp.core.config.ConfigManager;
import games.cubi.raycastedantiesp.core.config.raycast.TileEntityConfig;
import games.cubi.raycastedantiesp.core.players.PlayerData;
import games.cubi.raycastedantiesp.core.players.PlayerRegistry;
import games.cubi.raycastedantiesp.core.tracked.NettyTileEntity;
import games.cubi.raycastedantiesp.core.tracked.TrackedTileEntity;
import games.cubi.raycastedantiesp.core.view.BlockView;
import games.cubi.raycastedantiesp.core.view.BlockViewTransition;
import games.cubi.raycastedantiesp.packetevents.replaydata.PacketEventsTileEntityReplayData;
import games.cubi.raycastedantiesp.packetevents.target.PacketEventsTargetFilter;
import games.cubi.raycastedantiesp.packetevents.viewcontrollers.BlockTransitionRetryQueue.Operation;
import games.cubi.raycastedantiesp.packetevents.viewcontrollers.BlockTransitionRetryQueue.Stage;
import games.cubi.raycastedantiesp.packetevents.viewcontrollers.chunkparser.BlockChunkParser;
import games.cubi.raycastedantiesp.packetevents.viewcontrollers.chunkparser.ChunkParser;
import games.cubi.raycastedantiesp.packetevents.viewcontrollers.chunkparser.NonMutatingBlockChunkParser;
import games.cubi.raycastedantiesp.packetevents.viewcontrollers.chunkparser.NonMutatingOcclusionChunkParser;
import games.cubi.raycastedantiesp.packetevents.viewcontrollers.chunkparser.OcclusionChunkParser;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

public abstract class PacketEventsBlockViewController implements PacketListener {
    private static final int MAX_DIAGNOSTICS_PER_KIND = 5;

    private final BlockInfoResolver blockInfoResolver;
    private final PacketEventsTargetFilter targetFilter;
    private final ChunkParser mutatingChunkParser;
    private final ChunkParser nonMutatingChunkParser;
    private final IntSupplier currentTickSupplier;
    private final PacketEventsCommonViewController common;
    private final BlockTransitionRetryQueue transitionRetries = new BlockTransitionRetryQueue();
    private final AtomicInteger unknownBlockEntityDiagnostics = new AtomicInteger();
    private final AtomicInteger bulkChunkDiagnostics = new AtomicInteger();
    private final AtomicInteger retryOverflowDiagnostics = new AtomicInteger();
    private TileEntityConfig tileEntityConfig = null;
    private int hideOnSpawnDistanceSquared = 0;

    protected PacketEventsBlockViewController(BlockInfoResolver blockInfoResolver, boolean trackAllBlocks,
            IntSupplier currentTickSupplier) {
        this.blockInfoResolver = blockInfoResolver;
        this.targetFilter = blockInfoResolver instanceof PacketEventsTargetFilter filter
                ? filter
                : PacketEventsTargetFilter.DISABLED;
        this.currentTickSupplier = currentTickSupplier;
        common = PacketEventsCommonViewController.get(currentTickSupplier);
        if (trackAllBlocks) {
            mutatingChunkParser = new BlockChunkParser(blockInfoResolver, this::getHiddenBlockId);
            nonMutatingChunkParser = new NonMutatingBlockChunkParser(blockInfoResolver, this::getHiddenBlockId);
        } else {
            mutatingChunkParser = new OcclusionChunkParser(blockInfoResolver, this::getHiddenBlockId);
            nonMutatingChunkParser = new NonMutatingOcclusionChunkParser(blockInfoResolver, this::getHiddenBlockId);
        }
    }

    protected abstract int getHiddenBlockId(int blockY);

    public void removeViewer(UUID viewerUUID) {
        transitionRetries.clear(viewerUUID);
    }

    @Override
    public void onUserDisconnect(UserDisconnectEvent event) {
        removeViewer(event.getUser().getUUID());
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        User viewer = event.getUser();
        UUID viewerUUID = viewer.getUUID();
        if (viewerUUID == null) {
            return;
        }

        if (ConfigManager.get().getTileEntityConfig() != tileEntityConfig) {
            tileEntityConfig = ConfigManager.get().getTileEntityConfig();
            hideOnSpawnDistanceSquared = tileEntityConfig.hideOnSpawnDistance() * tileEntityConfig.hideOnSpawnDistance();
        }

        PlayerData playerData = PlayerRegistry.getInstance().getPlayerData(viewerUUID);
        if (playerData == null) {
            transitionRetries.clear(viewerUUID);
            return;
        }

        UUID world = common.resolvePacketWorld(playerData, viewer);
        int currentTick = currentTickSupplier.getAsInt();
        int worldEpoch = playerData.acquireWorldEpoch();

        boolean tileChecksEnabled = tileChecksEnabledForViewer(
                tileEntityConfig.enabled(), playerData.hasBypassPermission());
        BlockView blockView = playerData.blockView();
        blockView.applyTileEntityCheckMode(tileChecksEnabled, currentTick,
                tileEntity -> processModeRepairSafely(viewerUUID, viewer, blockView, worldEpoch,
                        tileEntity, blockView.tileEntityCheckModeToken(), currentTick, 0, Stage.BLOCK));
        transitionRetries.discardStale(viewerUUID, worldEpoch, blockView.tileEntityCheckModeToken());

        handleBlockPackets(event, viewer, playerData, world, currentTick, tileChecksEnabled);

        processTransitionRetries(viewerUUID, viewer, playerData, currentTick);
        if (blockView.hasPendingTransitions()) {
            processTileEntityTransitions(viewerUUID, viewer, playerData, currentTick);
        }
    }

    static boolean tileChecksEnabledForViewer(boolean configuredEnabled, boolean hasBypassPermission) {
        return configuredEnabled && !hasBypassPermission;
    }

    static boolean shouldFailClosedForUnknownBlockEntity(boolean tileChecksEnabled,
            BlockView.BlockEntityStatus blockStatus, boolean packetTypeManaged) {
        if (!tileChecksEnabled) {
            return false;
        }
        return blockStatus == BlockView.BlockEntityStatus.MANAGED
                || blockStatus == BlockView.BlockEntityStatus.UNKNOWN && packetTypeManaged;
    }

    private void handleBlockPackets(PacketSendEvent event, User viewer, PlayerData playerData, UUID world,
            int currentTick, boolean tileChecksEnabled) {
        if (world == null) {
            return;
        }

        BlockView blockView = playerData.blockView();

        if (event.getPacketType() == PacketType.Play.Server.UNLOAD_CHUNK) {
            WrapperPlayServerUnloadChunk packet = new WrapperPlayServerUnloadChunk(event);
            removeChunk(packet, blockView, world);
        } else if (event.getPacketType() == PacketType.Play.Server.BLOCK_CHANGE) {
            WrapperPlayServerBlockChange packet = new WrapperPlayServerBlockChange(event);
            handleSingleBlockChange(event, viewer, playerData, world, packet, tileChecksEnabled, currentTick);
        } else if (event.getPacketType() == PacketType.Play.Server.MULTI_BLOCK_CHANGE) {
            WrapperPlayServerMultiBlockChange packet = new WrapperPlayServerMultiBlockChange(event);
            handleMultiBlockChange(event, blockView, world, packet, playerData.ownLocation(), tileChecksEnabled);
        } else if (event.getPacketType() == PacketType.Play.Server.BLOCK_ENTITY_DATA) {
            WrapperPlayServerBlockEntityData packet = new WrapperPlayServerBlockEntityData(event);
            ImmutableBlockSpatialImpl position = new ImmutableBlockSpatialImpl(
                    packet.getPosition().getX(), packet.getPosition().getY(), packet.getPosition().getZ());
            TrackedTileEntity<PacketEventsTileEntityReplayData> tileEntity =
                    getTrackedTileEntity(blockView, world, position);
            if (tileEntity == null) {
                BlockView.BlockEntityStatus blockStatus = blockView.getBlockEntityStatus(world, position);
                boolean packetTypeManaged = targetFilter.shouldCullBlockEntity(packet.getBlockEntityType());
                boolean failClosed = shouldFailClosedForUnknownBlockEntity(
                        tileChecksEnabled, blockStatus, packetTypeManaged);
                logUnknownBlockEntity(world, position, packet, blockStatus, packetTypeManaged, failClosed);
                if (failClosed) {
                    event.setCancelled(true);
                    sendUnknownManagedTileFallback(viewer, position);
                }
                return;
            }

            ensureTileReplayData(tileEntity).setBlockEntityData(packet.getBlockEntityType(), packet.getNBT());
            if (tileChecksEnabled && !blockView.isVisible(world, position, currentTick)) {
                event.setCancelled(true);
                processTileEntityOperationSafely(playerData.getPlayerUUID(), viewer, blockView,
                        playerData.acquireWorldEpoch(), Operation.HIDE, tileEntity,
                        blockView.tileEntityCheckModeToken(), currentTick, 0, Stage.BLOCK, tileEntity.blockID());
            }
        } else if (event.getPacketType() == PacketType.Play.Server.CHUNK_DATA) {
            WrapperPlayServerChunkData packet = new WrapperPlayServerChunkData(event);
            ChunkParser parser = tileChecksEnabled ? mutatingChunkParser : nonMutatingChunkParser;
            @Nullable Column result = parser.parse(blockView, world, packet.getColumn(),
                    playerData.nettyData().getCurrentWorldMinHeight() >> 4);
            if (result != null) {
                packet.setColumn(result);
                event.markForReEncode(true);
            }
        } else if (event.getPacketType() == PacketType.Play.Server.MAP_CHUNK_BULK) {
            handleUnexpectedBulkChunkPacket(bulkChunkDiagnostics, viewer.getUUID(), currentTick);
        }
    }

    static void handleUnexpectedBulkChunkPacket(AtomicInteger diagnostics, UUID viewerUUID, int currentTick) {
        handleUnexpectedBulkChunkPacket(diagnostics, viewerUUID, currentTick,
                message -> Logger.warning(message, 3, PacketEventsBlockViewController.class));
    }

    static void handleUnexpectedBulkChunkPacket(AtomicInteger diagnostics, UUID viewerUUID, int currentTick,
            Consumer<String> warningSink) {
        int diagnostic = diagnostics.incrementAndGet();
        if (diagnostic <= MAX_DIAGNOSTICS_PER_KIND) {
            warningSink.accept("Passing unexpected MAP_CHUNK_BULK packet through unchanged. viewer=" + viewerUUID
                    + " tick=" + currentTick + diagnosticSuffix(diagnostic));
        }
    }

    private void logUnknownBlockEntity(UUID world, BlockSpatial position, WrapperPlayServerBlockEntityData packet,
            BlockView.BlockEntityStatus blockStatus, boolean packetTypeManaged, boolean failClosed) {
        int diagnostic = unknownBlockEntityDiagnostics.incrementAndGet();
        if (diagnostic > MAX_DIAGNOSTICS_PER_KIND) {
            return;
        }
        String blockEntityType = String.valueOf(packet.getBlockEntityType());
        Logger.warning("Received standalone block entity data without tracked tile state. world=" + world
                        + " position=" + position.blockX() + "," + position.blockY() + "," + position.blockZ()
                        + " blockEntityType=" + blockEntityType
                        + " blockStatus=" + blockStatus
                        + " packetTypeManaged=" + packetTypeManaged
                        + " action=" + (failClosed ? "cancel-and-hide" : "pass-virtual")
                        + diagnosticSuffix(diagnostic),
                5, PacketEventsBlockViewController.class);
    }

    private static String diagnosticSuffix(int diagnostic) {
        return diagnostic == MAX_DIAGNOSTICS_PER_KIND ? " (further messages suppressed)" : "";
    }

    private void sendUnknownManagedTileFallback(User viewer, BlockSpatial position) {
        try {
            viewer.writePacketSilently(new WrapperPlayServerBlockChange(
                    new Vector3i(position.blockX(), position.blockY(), position.blockZ()),
                    getHiddenBlockId(position.blockY())));
        } catch (RuntimeException exception) {
            Logger.error("Failed to send fail-closed block fallback for untracked managed block entity. viewer="
                            + viewer.getUUID() + " position=" + position.blockX() + "," + position.blockY() + ","
                            + position.blockZ(),
                    exception, 2, PacketEventsBlockViewController.class);
        }
    }

    private void removeChunk(WrapperPlayServerUnloadChunk packet, BlockView blockView, UUID world) {
        blockView.removeChunk(world, packet.getChunkX(), packet.getChunkZ());
    }

    private void handleMultiBlockChange(PacketSendEvent event, BlockView blockView, UUID world,
            WrapperPlayServerMultiBlockChange packet, Locatable playerLocation, boolean tileChecksEnabled) {
        MutableBlockSpatialImpl key = new MutableBlockSpatialImpl(0, 0, 0);
        for (WrapperPlayServerMultiBlockChange.EncodedBlock change : packet.getBlocks()) {
            char blockID = (char) change.getBlockId();
            boolean tileEntity = blockInfoResolver.isTileEntity(blockID);
            blockView.upsertBlock(world, change.getX(), change.getY(), change.getZ(), blockID);
            key.setBlockPosition(change.getX(), change.getY(), change.getZ());
            if (tileEntity) {
                boolean visibleIfNew = !tileChecksEnabled || visibleIfNew(key, playerLocation, world);
                TrackedTileEntity<?> state =
                        blockView.updateOrInsertTileEntity(world, key, blockID, visibleIfNew);
                if (!tileChecksEnabled) {
                    blockView.recordOutboundTileEntityVisibility(state, true);
                } else if (state != null && !state.visible()) {
                    change.setBlockId(getHiddenBlockId(key.blockY()));
                    event.markForReEncode(true);
                }
            } else {
                blockView.removeTileEntity(world, key);
            }
        }
    }

    static class MutableVector3i extends Vector3i {
        int x;
        int y;
        int z;

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public int getZ() {
            return z;
        }

        public MutableVector3i set(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
            return this;
        }
    }

    private final ThreadLocal<MutableVector3i> vector = ThreadLocal.withInitial(MutableVector3i::new);
    private final ThreadLocal<WrapperPlayServerBlockChange> blockChangeWrapper =
            ThreadLocal.withInitial(() -> new WrapperPlayServerBlockChange(vector.get(), 0));

    private WrapperPlayServerBlockChange getBlockChangeWith(int x, int y, int z, int blockID) {
        MutableVector3i vec = vector.get().set(x, y, z);
        WrapperPlayServerBlockChange change = blockChangeWrapper.get();
        change.setBlockID(blockID);
        change.setBlockPosition(vec);
        return change;
    }

    private void processTransitionRetries(UUID viewerUUID, User viewer, PlayerData playerData, int currentTick) {
        int worldEpoch = playerData.acquireWorldEpoch();
        BlockView blockView = playerData.blockView();
        for (BlockTransitionRetryQueue.Retry retry :
                transitionRetries.drainDue(viewerUUID, worldEpoch, currentTick)) {
            processTileEntityOperationSafely(viewerUUID, viewer, blockView, worldEpoch,
                    retry.operation(), retry.tileEntity(), retry.modeToken(), currentTick,
                    retry.attempts(), retry.stage(), retry.expectedBlockID());
        }
    }

    private void processTileEntityTransitions(UUID viewerUUID, User viewer, PlayerData playerData, int currentTick) {
        BlockView blockView = playerData.blockView();
        int worldEpoch = playerData.acquireWorldEpoch();
        blockView.drainTransitions((type, tileEntity, modeToken, transitionWorldEpoch) -> {
            Operation operation = type == BlockViewTransition.Type.HIDE ? Operation.HIDE : Operation.SHOW;
            processTileEntityOperationSafely(viewerUUID, viewer, blockView, worldEpoch, operation,
                    tileEntity, modeToken, currentTick, 0, Stage.BLOCK, tileEntity.blockID(),
                    transitionWorldEpoch);
        });
    }

    private void processModeRepairSafely(UUID viewerUUID, User viewer, BlockView blockView, int worldEpoch,
            TrackedTileEntity<?> tileEntity, long modeToken, int currentTick, int attempts, Stage stage) {
        processTileEntityOperationSafely(viewerUUID, viewer, blockView, worldEpoch,
                Operation.MODE_REPAIR, tileEntity, modeToken, currentTick, attempts, stage, tileEntity.blockID());
    }

    private void processTileEntityOperationSafely(UUID viewerUUID, User viewer, BlockView blockView,
            int currentWorldEpoch, Operation operation, TrackedTileEntity<?> tileEntity, long modeToken,
            int currentTick, int attempts, Stage stage, int expectedBlockID) {
        processTileEntityOperationSafely(viewerUUID, viewer, blockView, currentWorldEpoch, operation,
                tileEntity, modeToken, currentTick, attempts, stage, expectedBlockID, currentWorldEpoch);
    }

    private void processTileEntityOperationSafely(UUID viewerUUID, User viewer, BlockView blockView,
            int currentWorldEpoch, Operation operation, TrackedTileEntity<?> tileEntity, long modeToken,
            int currentTick, int attempts, Stage stage, int expectedBlockID, int transitionWorldEpoch) {
        try {
            processTileEntityOperation(viewer, blockView, currentWorldEpoch, operation, tileEntity,
                    modeToken, stage, expectedBlockID, transitionWorldEpoch);
        } catch (RuntimeException exception) {
            Stage failedStage = exception instanceof TransitionWriteException writeFailure
                    ? writeFailure.stage()
                    : stage;
            int nextAttempt = attempts == Integer.MAX_VALUE ? attempts : attempts + 1;
            boolean evicted = transitionRetries.enqueue(viewerUUID, operation, failedStage, tileEntity,
                    expectedBlockID, transitionWorldEpoch, modeToken, nextAttempt, currentTick);
            if (nextAttempt == 1 || nextAttempt % 20 == 0) {
                Exception loggedException = exception;
                if (exception instanceof TransitionWriteException
                        && exception.getCause() instanceof Exception cause) {
                    loggedException = cause;
                }
                Logger.error("Block visibility synchronization failed and was queued for retry. viewer=" + viewerUUID
                                + " position=" + tileEntity.blockX() + "," + tileEntity.blockY() + ","
                                + tileEntity.blockZ()
                                + " blockID=" + expectedBlockID
                                + " operation=" + operation
                                + " stage=" + failedStage
                                + " attempts=" + nextAttempt,
                        loggedException, 1, PacketEventsBlockViewController.class);
            }
            if (evicted) {
                int diagnostic = retryOverflowDiagnostics.incrementAndGet();
                if (diagnostic <= MAX_DIAGNOSTICS_PER_KIND) {
                    Logger.warning("Block transition retry queue reached its per-viewer limit and evicted its oldest "
                                    + "repair. viewer=" + viewerUUID + " limit="
                                    + BlockTransitionRetryQueue.MAX_RETRIES_PER_VIEWER
                                    + diagnosticSuffix(diagnostic),
                            2, PacketEventsBlockViewController.class);
                }
            }
        }
    }

    private void processTileEntityOperation(User viewer, BlockView blockView, int currentWorldEpoch,
            Operation operation, TrackedTileEntity<?> tileEntity, long modeToken, Stage stage,
            int expectedBlockID, int transitionWorldEpoch) {
        TrackedTileEntity<PacketEventsTileEntityReplayData> state =
                resolveCurrentTransitionState(tileEntity, transitionWorldEpoch, currentWorldEpoch);
        if (state == null || state.blockID() == 0 || state.blockID() != expectedBlockID) {
            return;
        }

        switch (operation) {
            case HIDE -> {
                if (!blockView.isCurrentEnabledTileEntityMode(modeToken) || state.visible()) {
                    return;
                }
                executeTransitionWrites(operation, stage,
                        () -> viewer.writePacketSilently(getBlockChangeWith(
                                state.blockX(), state.blockY(), state.blockZ(),
                                getHiddenBlockId(state.blockY()))),
                        null);
            }
            case SHOW -> {
                if (!blockView.isCurrentEnabledTileEntityMode(modeToken) || !state.visible()) {
                    return;
                }
                sendTileEntityFromStage(viewer, state, operation, stage);
            }
            case MODE_REPAIR -> {
                if (blockView.tileEntityCheckModeToken() != modeToken
                        || (modeToken & 1L) != 0L
                        || !state.visible()) {
                    return;
                }
                sendTileEntityFromStage(viewer, state, operation, stage);
            }
        }
    }

    private void sendTileEntityFromStage(User viewer,
            TrackedTileEntity<PacketEventsTileEntityReplayData> tileEntity,
            Operation operation, Stage stage) {
        PacketEventsTileEntityReplayData replayData = ensureTileReplayData(tileEntity);
        Runnable blockEntityDataWrite = replayData.blockEntityType() != null && replayData.nbt() != null
                ? () -> viewer.writePacketSilently(buildBlockEntityDataPacket(tileEntity, replayData))
                : null;
        executeTransitionWrites(operation, stage,
                () -> viewer.writePacketSilently(getBlockChangeWith(
                        tileEntity.blockX(), tileEntity.blockY(), tileEntity.blockZ(), tileEntity.blockID())),
                blockEntityDataWrite);
    }

    static void executeTransitionWrites(Operation operation, Stage startingStage,
            Runnable blockWrite, @Nullable Runnable blockEntityDataWrite) {
        if (operation == Operation.HIDE) {
            if (startingStage != Stage.BLOCK) {
                throw new IllegalArgumentException("HIDE repair cannot start at block-entity-data stage");
            }
            writeStage(Stage.BLOCK, blockWrite);
            return;
        }

        if (startingStage == Stage.BLOCK) {
            writeStage(Stage.BLOCK, blockWrite);
        }
        if (blockEntityDataWrite != null) {
            writeStage(Stage.BLOCK_ENTITY_DATA, blockEntityDataWrite);
        }
    }

    private static void writeStage(Stage stage, Runnable write) {
        try {
            write.run();
        } catch (RuntimeException exception) {
            throw new TransitionWriteException(stage, exception);
        }
    }

    private void handleSingleBlockChange(PacketSendEvent event, User viewer, PlayerData playerData, UUID world,
            WrapperPlayServerBlockChange packet, boolean tileChecksEnabled, int currentTick) {
        char blockID = (char) packet.getBlockId();
        boolean tileEntity = blockInfoResolver.isTileEntity(blockID);
        Vector3i position = packet.getBlockPosition();
        ImmutableBlockSpatialImpl location =
                new ImmutableBlockSpatialImpl(position.getX(), position.getY(), position.getZ());

        BlockView blockView = playerData.blockView();
        blockView.upsertBlock(world, position.getX(), position.getY(), position.getZ(), blockID);
        if (tileEntity) {
            boolean visibleIfNew = !tileChecksEnabled || visibleIfNew(location, playerData.ownLocation(), world);
            TrackedTileEntity<?> state =
                    blockView.updateOrInsertTileEntity(world, location, blockID, visibleIfNew);
            if (!tileChecksEnabled) {
                blockView.recordOutboundTileEntityVisibility(state, true);
            } else if (state != null && !state.visible()) {
                event.setCancelled(true);
                processTileEntityOperationSafely(playerData.getPlayerUUID(), viewer, blockView,
                        playerData.acquireWorldEpoch(), Operation.HIDE, state,
                        blockView.tileEntityCheckModeToken(), currentTick, 0, Stage.BLOCK, state.blockID());
            }
        } else {
            blockView.removeTileEntity(world, location);
        }
    }

    private boolean visibleIfNew(BlockSpatial location, Locatable playerLocation, UUID packetWorld) {
        if (!tileEntityConfig.enabled()) {
            return false;
        }
        if (playerLocation == null || playerLocation.world() == null
                || !playerLocation.world().equals(packetWorld)) {
            return false;
        }
        return location.distanceSquared(playerLocation) <= hideOnSpawnDistanceSquared;
    }

    private WrapperPlayServerBlockEntityData buildBlockEntityDataPacket(
            BlockSpatial location, PacketEventsTileEntityReplayData replayData) {
        return new WrapperPlayServerBlockEntityData(
                new Vector3i(location.blockX(), location.blockY(), location.blockZ()),
                replayData.blockEntityType(),
                replayData.nbt());
    }

    @SuppressWarnings("unchecked")
    private static TrackedTileEntity<PacketEventsTileEntityReplayData> getTrackedTileEntity(
            BlockView blockView, UUID world, BlockSpatial position) {
        return (TrackedTileEntity<PacketEventsTileEntityReplayData>)
                blockView.getTrackedTileEntity(world, position);
    }

    @SuppressWarnings("unchecked")
    static @Nullable TrackedTileEntity<PacketEventsTileEntityReplayData> resolveCurrentTransitionState(
            BlockViewTransition transition, int currentWorldEpoch) {
        return resolveCurrentTransitionState(
                transition.tileEntity(), transition.worldEpoch(), currentWorldEpoch);
    }

    @SuppressWarnings("unchecked")
    static @Nullable TrackedTileEntity<PacketEventsTileEntityReplayData> resolveCurrentTransitionState(
            TrackedTileEntity<?> tileEntity, int transitionWorldEpoch, int currentWorldEpoch) {
        if (!PlayerData.isStableWorldEpoch(currentWorldEpoch)
                || transitionWorldEpoch != currentWorldEpoch
                || !(tileEntity instanceof NettyTileEntity<?> nettyTileEntity)
                || nettyTileEntity.isRemoved()) {
            return null;
        }
        return (TrackedTileEntity<PacketEventsTileEntityReplayData>) tileEntity;
    }

    private PacketEventsTileEntityReplayData ensureTileReplayData(
            TrackedTileEntity<PacketEventsTileEntityReplayData> tileEntity) {
        PacketEventsTileEntityReplayData replayData = tileEntity.extraData();
        if (replayData == null) {
            replayData = new PacketEventsTileEntityReplayData();
            tileEntity.setExtraData(replayData);
        }
        return replayData;
    }

    static final class TransitionWriteException extends RuntimeException {
        private final Stage stage;

        private TransitionWriteException(Stage stage, RuntimeException cause) {
            super(cause);
            this.stage = stage;
        }

        Stage stage() {
            return stage;
        }
    }
}
