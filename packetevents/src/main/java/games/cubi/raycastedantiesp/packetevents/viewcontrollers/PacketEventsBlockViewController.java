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
    private static final int FIRST_FAILURE = 1;
    private static final String POSITION_LABEL = " position=";

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
                tileEntity -> processModeRepairSafely(playerData, viewer, tileEntity,
                        blockView.tileEntityCheckModeToken(), currentTick, Stage.BLOCK));
        transitionRetries.discardStale(viewerUUID, worldEpoch, blockView.tileEntityCheckModeToken());

        handleBlockPackets(event, viewer, playerData, world, currentTick, tileChecksEnabled);

        processTransitionRetries(viewer, playerData, currentTick);
        if (blockView.hasPendingTransitions()) {
            processTileEntityTransitions(viewer, playerData, currentTick);
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
            handleBlockEntityData(event, viewer, playerData, world, currentTick, tileChecksEnabled);
        } else if (event.getPacketType() == PacketType.Play.Server.CHUNK_DATA) {
            handleChunkData(event, playerData, world, tileChecksEnabled);
        } else if (event.getPacketType() == PacketType.Play.Server.MAP_CHUNK_BULK) {
            handleUnexpectedBulkChunkPacket(bulkChunkDiagnostics, viewer.getUUID(), currentTick);
        }
    }

    private void handleBlockEntityData(PacketSendEvent event, User viewer, PlayerData playerData,
            UUID world, int currentTick, boolean tileChecksEnabled) {
        WrapperPlayServerBlockEntityData packet = new WrapperPlayServerBlockEntityData(event);
        ImmutableBlockSpatialImpl position = new ImmutableBlockSpatialImpl(
                packet.getPosition().getX(), packet.getPosition().getY(), packet.getPosition().getZ());
        BlockView blockView = playerData.blockView();
        TrackedTileEntity<PacketEventsTileEntityReplayData> tileEntity =
                getTrackedTileEntity(blockView, world, position);
        if (tileEntity == null) {
            handleUnknownBlockEntity(event, viewer, blockView, world, position, packet, tileChecksEnabled);
            return;
        }

        ensureTileReplayData(tileEntity).setBlockEntityData(packet.getBlockEntityType(), packet.getNBT());
        if (tileChecksEnabled && !blockView.isVisible(world, position, currentTick)) {
            event.setCancelled(true);
            processInitialTileEntityOperationSafely(playerData, viewer, Operation.HIDE, tileEntity,
                    blockView.tileEntityCheckModeToken(), currentTick, Stage.BLOCK,
                    playerData.acquireWorldEpoch());
        }
    }

    private void handleUnknownBlockEntity(PacketSendEvent event, User viewer, BlockView blockView,
            UUID world, ImmutableBlockSpatialImpl position, WrapperPlayServerBlockEntityData packet,
            boolean tileChecksEnabled) {
        BlockView.BlockEntityStatus blockStatus = blockView.getBlockEntityStatus(world, position);
        boolean packetTypeManaged = targetFilter.shouldCullBlockEntity(packet.getBlockEntityType());
        boolean failClosed = shouldFailClosedForUnknownBlockEntity(
                tileChecksEnabled, blockStatus, packetTypeManaged);
        logUnknownBlockEntity(world, position, packet, blockStatus, packetTypeManaged, failClosed);
        if (failClosed) {
            event.setCancelled(true);
            sendUnknownManagedTileFallback(viewer, position);
        }
    }

    private void handleChunkData(PacketSendEvent event, PlayerData playerData,
            UUID world, boolean tileChecksEnabled) {
        WrapperPlayServerChunkData packet = new WrapperPlayServerChunkData(event);
        ChunkParser parser = tileChecksEnabled ? mutatingChunkParser : nonMutatingChunkParser;
        @Nullable Column result = parser.parse(playerData.blockView(), world, packet.getColumn(),
                playerData.nettyData().getCurrentWorldMinHeight() >> 4);
        if (result != null) {
            packet.setColumn(result);
            event.markForReEncode(true);
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

    @SuppressWarnings("PMD.GuardLogStatement") // CubiLogging performs its own level filtering.
    private void logUnknownBlockEntity(UUID world, BlockSpatial position, WrapperPlayServerBlockEntityData packet,
            BlockView.BlockEntityStatus blockStatus, boolean packetTypeManaged, boolean failClosed) {
        int diagnostic = unknownBlockEntityDiagnostics.incrementAndGet();
        if (diagnostic > MAX_DIAGNOSTICS_PER_KIND) {
            return;
        }
        String blockEntityType = String.valueOf(packet.getBlockEntityType());
        Logger.warning("Received standalone block entity data without tracked tile state. world=" + world
                        + POSITION_LABEL + position.blockX() + "," + position.blockY() + "," + position.blockZ()
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

    @SuppressWarnings("PMD.GuardLogStatement") // CubiLogging performs its own level filtering.
    private void sendUnknownManagedTileFallback(User viewer, BlockSpatial position) {
        try {
            viewer.writePacketSilently(new WrapperPlayServerBlockChange(
                    new Vector3i(position.blockX(), position.blockY(), position.blockZ()),
                    getHiddenBlockId(position.blockY())));
        } catch (RuntimeException exception) {
            Logger.error("Failed to send fail-closed block fallback for untracked managed block entity. viewer="
                            + viewer.getUUID() + POSITION_LABEL + position.blockX() + "," + position.blockY() + ","
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

    private void processTransitionRetries(User viewer, PlayerData playerData, int currentTick) {
        UUID viewerUUID = playerData.getPlayerUUID();
        int worldEpoch = playerData.acquireWorldEpoch();
        for (BlockTransitionRetryQueue.Retry retry :
                transitionRetries.drainDue(viewerUUID, worldEpoch, currentTick)) {
            processRetrySafely(playerData, viewer, retry, currentTick);
        }
    }

    private void processTileEntityTransitions(User viewer, PlayerData playerData, int currentTick) {
        BlockView blockView = playerData.blockView();
        blockView.drainTransitions((type, tileEntity, modeToken, transitionWorldEpoch) -> {
            Operation operation = type == BlockViewTransition.Type.HIDE ? Operation.HIDE : Operation.SHOW;
            processInitialTileEntityOperationSafely(playerData, viewer, operation, tileEntity,
                    modeToken, currentTick, Stage.BLOCK, transitionWorldEpoch);
        });
    }

    private void processModeRepairSafely(PlayerData playerData, User viewer,
            TrackedTileEntity<?> tileEntity, long modeToken, int currentTick, Stage stage) {
        processInitialTileEntityOperationSafely(playerData, viewer, Operation.MODE_REPAIR, tileEntity,
                modeToken, currentTick, stage, playerData.acquireWorldEpoch());
    }

    private void processRetrySafely(PlayerData playerData, User viewer,
            BlockTransitionRetryQueue.Retry retry, int currentTick) {
        try {
            processTileEntityOperation(playerData, viewer, retry.operation(), retry.tileEntity(),
                    retry.modeToken(), retry.stage(), retry.expectedBlockID(), retry.worldEpoch());
        } catch (TransitionWriteException exception) {
            handleTransitionFailure(playerData, viewer, new TransitionFailure(
                    retry.operation(), retry.tileEntity(), retry.modeToken(), currentTick, retry.attempts(),
                    exception.stage(), retry.expectedBlockID(), retry.worldEpoch(), loggedCause(exception)));
        } catch (RuntimeException exception) {
            handleTransitionFailure(playerData, viewer, new TransitionFailure(
                    retry.operation(), retry.tileEntity(), retry.modeToken(), currentTick, retry.attempts(),
                    retry.stage(), retry.expectedBlockID(), retry.worldEpoch(), exception));
        }
    }

    private void processInitialTileEntityOperationSafely(PlayerData playerData, User viewer,
            Operation operation, TrackedTileEntity<?> tileEntity, long modeToken, int currentTick,
            Stage stage, int transitionWorldEpoch) {
        int expectedBlockID = tileEntity.blockID();
        try {
            processTileEntityOperation(playerData, viewer, operation, tileEntity,
                    modeToken, stage, expectedBlockID, transitionWorldEpoch);
        } catch (TransitionWriteException exception) {
            handleTransitionFailure(playerData, viewer, new TransitionFailure(
                    operation, tileEntity, modeToken, currentTick, 0,
                    exception.stage(), expectedBlockID, transitionWorldEpoch, loggedCause(exception)));
        } catch (RuntimeException exception) {
            handleTransitionFailure(playerData, viewer, new TransitionFailure(
                    operation, tileEntity, modeToken, currentTick, 0,
                    stage, expectedBlockID, transitionWorldEpoch, exception));
        }
    }

    private static Exception loggedCause(TransitionWriteException exception) {
        return exception.getCause() instanceof Exception cause ? cause : exception;
    }

    private void handleTransitionFailure(PlayerData playerData, User viewer, TransitionFailure failure) {
        int nextAttempt = failure.attempts() == Integer.MAX_VALUE
                ? failure.attempts()
                : failure.attempts() + 1;
        if (nextAttempt >= BlockTransitionRetryQueue.MAX_FAILURES) {
            logTerminalTransitionFailure(playerData, failure, nextAttempt);
            return;
        }

        boolean rejected = transitionRetries.enqueue(new BlockTransitionRetryQueue.RetryRequest(
                playerData.getPlayerUUID(), failure.operation(), failure.failedStage(), failure.tileEntity(),
                failure.expectedBlockID(), failure.transitionWorldEpoch(), failure.modeToken(),
                nextAttempt, failure.currentTick()));
        if (nextAttempt == FIRST_FAILURE) {
            logInitialTransitionFailure(playerData, failure, nextAttempt, rejected);
        }
        if (rejected) {
            logRetryOverflow(playerData.getPlayerUUID());
        }
    }

    @SuppressWarnings("PMD.GuardLogStatement") // CubiLogging performs its own level filtering.
    private static void logTerminalTransitionFailure(
            PlayerData playerData, TransitionFailure failure, int nextAttempt) {
        Logger.error("Block visibility synchronization reached its terminal retry limit. viewer="
                        + playerData.getPlayerUUID() + transitionDescription(failure, nextAttempt),
                failure.loggedException(), 1, PacketEventsBlockViewController.class);
    }

    @SuppressWarnings("PMD.GuardLogStatement") // CubiLogging performs its own level filtering.
    private static void logInitialTransitionFailure(
            PlayerData playerData, TransitionFailure failure, int nextAttempt, boolean rejected) {
        Logger.error("Block visibility synchronization failed "
                        + (rejected ? "and could not be queued for retry. viewer="
                                    : "and was queued for retry. viewer=")
                        + playerData.getPlayerUUID() + transitionDescription(failure, nextAttempt),
                failure.loggedException(), 1, PacketEventsBlockViewController.class);
    }

    private static String transitionDescription(TransitionFailure failure, int attempts) {
        TrackedTileEntity<?> tileEntity = failure.tileEntity();
        return POSITION_LABEL + tileEntity.blockX() + "," + tileEntity.blockY() + "," + tileEntity.blockZ()
                + " blockID=" + failure.expectedBlockID()
                + " operation=" + failure.operation()
                + " stage=" + failure.failedStage()
                + " attempts=" + attempts;
    }

    @SuppressWarnings("PMD.GuardLogStatement") // CubiLogging performs its own level filtering.
    private void logRetryOverflow(UUID viewerUUID) {
        int diagnostic = retryOverflowDiagnostics.incrementAndGet();
        if (diagnostic <= MAX_DIAGNOSTICS_PER_KIND) {
            Logger.warning("Block transition retry queue reached its per-viewer limit and rejected its newest "
                            + "repair to preserve existing staged repairs. viewer=" + viewerUUID + " limit="
                            + BlockTransitionRetryQueue.MAX_RETRIES_PER_VIEWER
                            + diagnosticSuffix(diagnostic),
                    2, PacketEventsBlockViewController.class);
        }
    }

    private void processTileEntityOperation(PlayerData playerData, User viewer,
            Operation operation, TrackedTileEntity<?> tileEntity, long modeToken, Stage stage,
            int expectedBlockID, int transitionWorldEpoch) {
        int currentWorldEpoch = playerData.acquireWorldEpoch();
        BlockView blockView = playerData.blockView();
        TrackedTileEntity<PacketEventsTileEntityReplayData> state =
                resolveCurrentTransitionState(tileEntity, transitionWorldEpoch, currentWorldEpoch);
        if (state == null || state.blockID() == 0 || state.blockID() != expectedBlockID) {
            return;
        }

        switch (operation) {
            case HIDE -> hideTileEntity(viewer, blockView, state, modeToken, stage);
            case SHOW -> showTileEntity(viewer, blockView, state, modeToken, operation, stage);
            case MODE_REPAIR -> repairTileEntityMode(viewer, blockView, state, modeToken, operation, stage);
            default -> throw new IllegalStateException("Unknown block transition operation: " + operation);
        }
    }

    private void hideTileEntity(User viewer, BlockView blockView,
            TrackedTileEntity<PacketEventsTileEntityReplayData> state, long modeToken, Stage stage) {
        if (!blockView.isCurrentEnabledTileEntityMode(modeToken) || state.visible()) {
            return;
        }
        executeTransitionWrites(Operation.HIDE, stage,
                () -> viewer.writePacketSilently(getBlockChangeWith(
                        state.blockX(), state.blockY(), state.blockZ(), getHiddenBlockId(state.blockY()))),
                null);
    }

    private void showTileEntity(User viewer, BlockView blockView,
            TrackedTileEntity<PacketEventsTileEntityReplayData> state, long modeToken,
            Operation operation, Stage stage) {
        if (!blockView.isCurrentEnabledTileEntityMode(modeToken) || !state.visible()) {
            return;
        }
        sendTileEntityFromStage(viewer, state, operation, stage);
    }

    private void repairTileEntityMode(User viewer, BlockView blockView,
            TrackedTileEntity<PacketEventsTileEntityReplayData> state, long modeToken,
            Operation operation, Stage stage) {
        if (blockView.tileEntityCheckModeToken() != modeToken
                || (modeToken & 1L) != 0L
                || !state.visible()) {
            return;
        }
        sendTileEntityFromStage(viewer, state, operation, stage);
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
                processInitialTileEntityOperationSafely(playerData, viewer, Operation.HIDE, state,
                        blockView.tileEntityCheckModeToken(), currentTick, Stage.BLOCK,
                        playerData.acquireWorldEpoch());
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

    private record TransitionFailure(
            Operation operation,
            TrackedTileEntity<?> tileEntity,
            long modeToken,
            int currentTick,
            int attempts,
            Stage failedStage,
            int expectedBlockID,
            int transitionWorldEpoch,
            Exception loggedException) {
    }

    static final class TransitionWriteException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final Stage failedStage;

        private TransitionWriteException(Stage stage, RuntimeException cause) {
            super(cause);
            this.failedStage = stage;
        }

        Stage stage() {
            return failedStage;
        }
    }
}
