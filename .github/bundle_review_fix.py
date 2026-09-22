from pathlib import Path


def replace_once(path, old, new):
    p = Path(path)
    text = p.read_text()
    if old not in text:
        raise RuntimeError(f"expected text not found in {path}: {old[:160]!r}")
    p.write_text(text.replace(old, new, 1))


# Per-viewer protocol bundle state and direct-visibility deferral.
p = Path("core/src/main/java/games/cubi/raycastedantiesp/core/players/NettyData.java")
text = p.read_text()
marker = '''public class NettyData implements Clearable {
    private static final int DEFAULT_MAP_SIZE = 16;
'''
insert = '''public class NettyData implements Clearable {
    private static final int DEFAULT_MAP_SIZE = 16;

    // Outbound bundle state is packet-thread-only. Visibility repair packets must not be injected
    // between an unrelated bundle's opening and closing delimiters.
    private boolean packetsAreWithinBundle;
    private IntOpenHashSet deferredDirectVisibilityEntityIDs;

    public boolean packetsAreWithinBundle() {
        return packetsAreWithinBundle;
    }

    /** Toggles on each clientbound bundle delimiter and returns the state after the delimiter. */
    public boolean togglePacketBundleState() {
        packetsAreWithinBundle = !packetsAreWithinBundle;
        return packetsAreWithinBundle;
    }

    public void deferDirectVisibilityEntity(int entityID) {
        if (deferredDirectVisibilityEntityIDs == null) {
            deferredDirectVisibilityEntityIDs = new IntOpenHashSet(DEFAULT_MAP_SIZE);
        }
        deferredDirectVisibilityEntityIDs.add(entityID);
    }

    public boolean hasDeferredDirectVisibilityEntities() {
        return deferredDirectVisibilityEntityIDs != null && !deferredDirectVisibilityEntityIDs.isEmpty();
    }

    public int[] drainDeferredDirectVisibilityEntityIDs() {
        if (!hasDeferredDirectVisibilityEntities()) {
            return null;
        }
        int[] entityIDs = deferredDirectVisibilityEntityIDs.toIntArray();
        deferredDirectVisibilityEntityIDs.clear();
        return entityIDs;
    }
'''
if marker not in text:
    raise RuntimeError("NettyData class marker missing")
text = text.replace(marker, insert, 1)
old = '''        suppressedPostEntitySpawnTaskEntityIDs.clear();
        evictPendingPostSpawnTasksOnNextPacket = false;
    }
'''
new = '''        suppressedPostEntitySpawnTaskEntityIDs.clear();
        if (deferredDirectVisibilityEntityIDs != null) {
            deferredDirectVisibilityEntityIDs.clear();
        }
        evictPendingPostSpawnTasksOnNextPacket = false;
    }
'''
if old not in text: raise RuntimeError("NettyData reconciliation clear marker missing")
text = text.replace(old, new, 1)
old = '''        currentWorldMinHeight = Integer.MIN_VALUE;
        currentWorldName = null;
    }
}
'''
new = '''        currentWorldMinHeight = Integer.MIN_VALUE;
        currentWorldName = null;
        packetsAreWithinBundle = false;
    }
}
'''
if old not in text: raise RuntimeError("NettyData clear marker missing")
text = text.replace(old, new, 1)
p.write_text(text)

# Expose current tile-check mode so a block controller can keep the existing mode throughout a bundle
# and apply a config/permission mode change only after the closing delimiter is sent.
replace_once(
    "core/src/main/java/games/cubi/raycastedantiesp/core/view/BlockView.java",
    '''    /** Returns an opaque enabled-state/generation snapshot for rejecting results that cross a mode change. */
    long tileEntityCheckModeToken();
''',
    '''    /** Returns whether tile visibility checks are enabled in the current mode generation. */
    boolean tileEntityChecksEnabled();

    /** Returns an opaque enabled-state/generation snapshot for rejecting results that cross a mode change. */
    long tileEntityCheckModeToken();
'''
)
replace_once(
    "core/src/main/java/games/cubi/raycastedantiesp/core/view/AbstractBlockView.java",
    '''    @Override
    public long tileEntityCheckModeToken() {
        return tileEntityCheckModeTokenAcquire();
    }
''',
    '''    @Override
    public boolean tileEntityChecksEnabled() {
        return modeEnabled(tileEntityCheckModeTokenAcquire());
    }

    @Override
    public long tileEntityCheckModeToken() {
        return tileEntityCheckModeTokenAcquire();
    }
'''
)

# Entity controller: track bundle delimiters, suppress ordinary transition drains while inside a
# bundle, and defer packet-thread direct SHOW/HIDE repairs until the closing delimiter has actually
# been sent to the client.
p = Path("packetevents/src/main/java/games/cubi/raycastedantiesp/packetevents/viewcontrollers/PacketEventsEntityViewController.java")
text = p.read_text()
old = '''        refreshVisibilityConfigs();
        int currentTick = CURRENT_TICK_SUPPLIER.getAsInt();
        processViewerPacket(event, viewer, playerData, currentTick);
        schedulePendingTransitions(event, viewer, playerData, viewerUUID);
        playerData.nettyData().evictPendingPostSpawnTasksIfRequired(currentTick);
    }
'''
new = '''        refreshVisibilityConfigs();
        int currentTick = CURRENT_TICK_SUPPLIER.getAsInt();
        processViewerPacket(event, viewer, playerData, currentTick);
        boolean withinBundle = updatePacketBundleState(event, playerData);
        schedulePendingTransitions(event, viewer, playerData, viewerUUID, withinBundle);
        playerData.nettyData().evictPendingPostSpawnTasksIfRequired(currentTick);
    }

    private static boolean updatePacketBundleState(PacketSendEvent event, PlayerData playerData) {
        if (event.getPacketType() == PacketType.Play.Server.BUNDLE) {
            return playerData.nettyData().togglePacketBundleState();
        }
        return playerData.nettyData().packetsAreWithinBundle();
    }
'''
if old not in text: raise RuntimeError("entity onPacketSend marker missing")
text = text.replace(old, new, 1)
old = '''    private void schedulePendingTransitions(
            PacketSendEvent event,
            User viewer,
            PlayerData playerData,
            UUID viewerUUID) {
        if (!hasPendingTransitions(playerData, viewerUUID)) {
            return;
        }
        event.getTasksAfterSend().add(() -> processPendingEntityTransitions(playerData, viewer));
    }

    private boolean hasPendingTransitions(PlayerData playerData, UUID viewerUUID) {
        return playerData.entityView().hasPendingTransitions()
                || playerData.playerView().hasPendingTransitions()
                || transitionRetries.hasPending(viewerUUID);
    }
'''
new = '''    private void schedulePendingTransitions(
            PacketSendEvent event,
            User viewer,
            PlayerData playerData,
            UUID viewerUUID,
            boolean withinBundle) {
        if (withinBundle || !hasPendingTransitions(playerData, viewerUUID)) {
            return;
        }
        event.getTasksAfterSend().add(() -> {
            processDeferredDirectVisibility(playerData);
            processPendingEntityTransitions(playerData, viewer);
        });
    }

    private boolean hasPendingTransitions(PlayerData playerData, UUID viewerUUID) {
        return playerData.nettyData().hasDeferredDirectVisibilityEntities()
                || playerData.entityView().hasPendingTransitions()
                || playerData.playerView().hasPendingTransitions()
                || transitionRetries.hasPending(viewerUUID);
    }

    private void processDeferredDirectVisibility(PlayerData playerData) {
        int[] entityIDs = playerData.nettyData().drainDeferredDirectVisibilityEntityIDs();
        if (entityIDs == null) {
            return;
        }
        int worldEpoch = playerData.acquireWorldEpoch();
        for (int entityID : entityIDs) {
            EntityView<?> view = playerData.entityView().exists(entityID)
                    ? playerData.entityView()
                    : playerData.playerView().exists(entityID) ? playerData.playerView() : null;
            if (view == null) {
                continue;
            }
            NettyEntity<?> entity = (NettyEntity<?>) view.getEntity(entityID);
            if (entity == null || entity.isSelfEntity()) {
                continue;
            }
            processDirectEntityVisibilityNow(
                    playerData,
                    view,
                    entity,
                    worldEpoch,
                    entity.visible() ? EntityViewTransition.Type.SHOW : EntityViewTransition.Type.HIDE);
        }
    }
'''
if old not in text: raise RuntimeError("entity schedule marker missing")
text = text.replace(old, new, 1)
old = '''    private void processDirectEntityVisibility(
            PlayerData playerData, EntityView<?> view, NettyEntity<?> entity,
            int worldEpoch, EntityViewTransition.Type type) {
        Object channel = PacketEvents.getAPI().getProtocolManager().getChannel(playerData.getPlayerUUID());
'''
new = '''    private void processDirectEntityVisibility(
            PlayerData playerData, EntityView<?> view, NettyEntity<?> entity,
            int worldEpoch, EntityViewTransition.Type type) {
        if (playerData.nettyData().packetsAreWithinBundle()) {
            playerData.nettyData().deferDirectVisibilityEntity(entity.entityID());
            return;
        }
        processDirectEntityVisibilityNow(playerData, view, entity, worldEpoch, type);
    }

    private void processDirectEntityVisibilityNow(
            PlayerData playerData, EntityView<?> view, NettyEntity<?> entity,
            int worldEpoch, EntityViewTransition.Type type) {
        Object channel = PacketEvents.getAPI().getProtocolManager().getChannel(playerData.getPlayerUUID());
'''
if old not in text: raise RuntimeError("direct visibility marker missing")
text = text.replace(old, new, 1)
p.write_text(text)

# Block retry queue exposes a cheap pending predicate so we do not schedule an after-send callback
# for every unrelated outgoing packet.
replace_once(
    "packetevents/src/main/java/games/cubi/raycastedantiesp/packetevents/viewcontrollers/BlockTransitionRetryQueue.java",
    '''    List<Retry> drainDue(UUID viewerUUID, int currentWorldEpoch, int currentTick) {
''',
    '''    boolean hasPending(UUID viewerUUID) {
        synchronized (this) {
            LinkedHashMap<Key, Retry> retries = retriesByViewer.get(viewerUUID);
            return retries != null && !retries.isEmpty();
        }
    }

    List<Retry> drainDue(UUID viewerUUID, int currentWorldEpoch, int currentTick) {
'''
)

# Block controller: hold mode changes and queued/retry repairs through the bundle, then execute them
# only in an after-send callback on the closing delimiter. This covers the fork's retry/mode-repair
# paths in addition to upstream #88's ordinary pending transitions.
p = Path("packetevents/src/main/java/games/cubi/raycastedantiesp/packetevents/viewcontrollers/PacketEventsBlockViewController.java")
text = p.read_text()
old = '''        boolean tileChecksEnabled = tileChecksEnabledForViewer(
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
'''
new = '''        boolean requestedTileChecksEnabled = tileChecksEnabledForViewer(
                tileEntityConfig.enabled(), playerData.hasBypassPermission());
        BlockView blockView = playerData.blockView();
        boolean bundleDelimiter = event.getPacketType() == PacketType.Play.Server.BUNDLE;
        boolean withinBundle = playerData.nettyData().packetsAreWithinBundle();
        boolean deferModeChange = withinBundle || bundleDelimiter;
        boolean tileChecksEnabled = deferModeChange
                ? blockView.tileEntityChecksEnabled()
                : requestedTileChecksEnabled;
        if (!deferModeChange) {
            applyTileEntityCheckMode(
                    blockView, requestedTileChecksEnabled, playerData, viewer, currentTick);
        }
        transitionRetries.discardStale(viewerUUID, worldEpoch, blockView.tileEntityCheckModeToken());

        handleBlockPackets(event, viewer, playerData, world, currentTick, tileChecksEnabled);
        scheduleVisibilityRepairsAfterSend(event, viewer, playerData, blockView,
                viewerUUID, currentTick, requestedTileChecksEnabled, withinBundle, bundleDelimiter);
    }

    private void applyTileEntityCheckMode(
            BlockView blockView, boolean enabled, PlayerData playerData, User viewer, int currentTick) {
        blockView.applyTileEntityCheckMode(enabled, currentTick,
                tileEntity -> processModeRepairSafely(playerData, viewer, tileEntity,
                        blockView.tileEntityCheckModeToken(), currentTick, Stage.BLOCK));
    }

    private void scheduleVisibilityRepairsAfterSend(
            PacketSendEvent event,
            User viewer,
            PlayerData playerData,
            BlockView blockView,
            UUID viewerUUID,
            int currentTick,
            boolean requestedTileChecksEnabled,
            boolean withinBundle,
            boolean bundleDelimiter) {
        if (withinBundle) {
            return;
        }
        boolean modeChangeAfterDelimiter = bundleDelimiter
                && blockView.tileEntityChecksEnabled() != requestedTileChecksEnabled;
        if (!modeChangeAfterDelimiter
                && !blockView.hasPendingTransitions()
                && !transitionRetries.hasPending(viewerUUID)) {
            return;
        }
        event.getTasksAfterSend().add(() -> {
            if (modeChangeAfterDelimiter) {
                applyTileEntityCheckMode(
                        blockView, requestedTileChecksEnabled, playerData, viewer, currentTick);
            }
            processTransitionRetries(viewer, playerData, currentTick);
            if (blockView.hasPendingTransitions()) {
                processTileEntityTransitions(viewer, playerData, currentTick);
            }
        });
    }
'''
if old not in text: raise RuntimeError("block controller scheduling marker missing")
text = text.replace(old, new, 1)
p.write_text(text)

# Core regression tests for bundle state/deferral ownership.
p = Path("core/src/test/java/games/cubi/raycastedantiesp/core/view/controller/PacketEntityViewControllerTest.java")
text = p.read_text()
text = text.replace(
    "import static org.junit.jupiter.api.Assertions.assertEquals;\n",
    "import static org.junit.jupiter.api.Assertions.assertArrayEquals;\nimport static org.junit.jupiter.api.Assertions.assertEquals;\n"
)
marker = '''    @Test
    void hiddenEntityEnteringExemptRegionSuppressesMovementAfterDirectShow() {
'''
new_tests = '''    @Test
    void nettyBundleStateDefersAndDrainsDirectVisibilityIds() {
        UUID world = UUID.randomUUID();
        PlayerData playerData = registerPlayer(world);

        assertFalse(playerData.nettyData().packetsAreWithinBundle());
        assertTrue(playerData.nettyData().togglePacketBundleState());
        playerData.nettyData().deferDirectVisibilityEntity(7);
        playerData.nettyData().deferDirectVisibilityEntity(7);
        playerData.nettyData().deferDirectVisibilityEntity(8);
        assertTrue(playerData.nettyData().hasDeferredDirectVisibilityEntities());

        int[] deferred = playerData.nettyData().drainDeferredDirectVisibilityEntityIDs();
        java.util.Arrays.sort(deferred);
        assertArrayEquals(new int[]{7, 8}, deferred);
        assertFalse(playerData.nettyData().hasDeferredDirectVisibilityEntities());
        assertFalse(playerData.nettyData().togglePacketBundleState());
    }

    @Test
    void worldTransitionClearsDeferredDirectVisibilityWithoutCorruptingBundleState() {
        UUID world = UUID.randomUUID();
        PlayerData playerData = registerPlayer(world);
        assertTrue(playerData.nettyData().togglePacketBundleState());
        playerData.nettyData().deferDirectVisibilityEntity(7);

        playerData.nettyData().clearPendingReconciliationState();

        assertFalse(playerData.nettyData().hasDeferredDirectVisibilityEntities());
        assertTrue(playerData.nettyData().packetsAreWithinBundle());
        assertFalse(playerData.nettyData().togglePacketBundleState());
    }

'''
if marker not in text: raise RuntimeError("core bundle test marker missing")
text = text.replace(marker, new_tests + marker, 1)
p.write_text(text)

# Block retry queue pending predicate regression.
p = Path("packetevents/src/test/java/games/cubi/raycastedantiesp/packetevents/viewcontrollers/BlockTransitionRetryQueueTest.java")
text = p.read_text()
marker = '''class BlockTransitionRetryQueueTest {
'''
new_test = '''class BlockTransitionRetryQueueTest {
    @Test
    void pendingPredicateTracksViewerQueueLifecycle() {
        BlockTransitionRetryQueue queue = new BlockTransitionRetryQueue();
        UUID viewer = UUID.randomUUID();
        TrackedTileEntity<?> tile = tileEntity();
        assertFalse(queue.hasPending(viewer));
        assertFalse(queue.enqueue(request(viewer, tile, BlockTransitionRetryQueue.Operation.SHOW,
                BlockTransitionRetryQueue.Stage.BLOCK, 1, 2, 0, 0)));
        assertTrue(queue.hasPending(viewer));
        queue.clear(viewer);
        assertFalse(queue.hasPending(viewer));
    }

'''
if marker not in text: raise RuntimeError("retry queue test marker missing")
text = text.replace(marker, new_test, 1)
p.write_text(text)

print("Bundle-boundary repairs applied")
