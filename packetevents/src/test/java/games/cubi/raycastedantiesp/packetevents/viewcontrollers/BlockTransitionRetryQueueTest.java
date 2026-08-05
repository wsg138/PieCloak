package games.cubi.raycastedantiesp.packetevents.viewcontrollers;

import games.cubi.raycastedantiesp.core.tracked.TrackedTileEntity;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;

import static games.cubi.raycastedantiesp.packetevents.viewcontrollers.BlockTransitionRetryQueue.Operation.HIDE;
import static games.cubi.raycastedantiesp.packetevents.viewcontrollers.BlockTransitionRetryQueue.Operation.SHOW;
import static games.cubi.raycastedantiesp.packetevents.viewcontrollers.BlockTransitionRetryQueue.Stage.BLOCK;
import static games.cubi.raycastedantiesp.packetevents.viewcontrollers.BlockTransitionRetryQueue.Stage.BLOCK_ENTITY_DATA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockTransitionRetryQueueTest {
    @Test
    void duplicateRepairKeepsEarliestIncompleteStage() {
        BlockTransitionRetryQueue queue = new BlockTransitionRetryQueue();
        UUID viewer = UUID.randomUUID();
        TrackedTileEntity<?> tile = tileEntity();

        queue.enqueue(viewer, SHOW, BLOCK_ENTITY_DATA, tile, 5, 2, 7L, 1, 10);
        queue.enqueue(viewer, SHOW, BLOCK, tile, 5, 2, 7L, 2, 10);

        assertEquals(1, queue.size(viewer));
        List<BlockTransitionRetryQueue.Retry> due = queue.drainDue(viewer, 2, 11);
        assertEquals(1, due.size());
        assertEquals(BLOCK, due.getFirst().stage());
        assertEquals(2, due.getFirst().attempts());
        assertFalse(queue.hasPending(viewer));
    }

    @Test
    void retriesUseBoundedBackoffAndCanConverge() {
        BlockTransitionRetryQueue queue = new BlockTransitionRetryQueue();
        UUID viewer = UUID.randomUUID();
        TrackedTileEntity<?> tile = tileEntity();

        queue.enqueue(viewer, HIDE, BLOCK, tile, 5, 2, 7L, 1, 0);
        assertTrue(queue.drainDue(viewer, 2, 0).isEmpty());
        BlockTransitionRetryQueue.Retry first = queue.drainDue(viewer, 2, 1).getFirst();

        queue.enqueue(viewer, first.operation(), first.stage(), first.tileEntity(), first.expectedBlockID(),
                first.worldEpoch(), first.modeToken(), 2, 1);
        assertTrue(queue.drainDue(viewer, 2, 2).isEmpty());
        assertEquals(1, queue.drainDue(viewer, 2, 3).size());
        assertFalse(queue.hasPending(viewer));
    }

    @Test
    void worldChangeDiscardsStaleRepairs() {
        BlockTransitionRetryQueue queue = new BlockTransitionRetryQueue();
        UUID viewer = UUID.randomUUID();
        queue.enqueue(viewer, SHOW, BLOCK, tileEntity(), 5, 2, 7L, 1, 0);

        assertTrue(queue.drainDue(viewer, 4, 10).isEmpty());
        assertFalse(queue.hasPending(viewer));
    }

    @Test
    void modeChangeDiscardsStaleRepairsImmediately() {
        BlockTransitionRetryQueue queue = new BlockTransitionRetryQueue();
        UUID viewer = UUID.randomUUID();
        queue.enqueue(viewer, SHOW, BLOCK, tileEntity(), 5, 2, 7L, 1, 0);

        queue.discardStale(viewer, 2, 9L);

        assertFalse(queue.hasPending(viewer));
    }

    @Test
    void disconnectCleanupClearsViewerRepairs() {
        BlockTransitionRetryQueue queue = new BlockTransitionRetryQueue();
        UUID viewer = UUID.randomUUID();
        queue.enqueue(viewer, SHOW, BLOCK, tileEntity(), 5, 2, 7L, 1, 0);

        queue.clear(viewer);

        assertFalse(queue.hasPending(viewer));
    }

    @Test
    void retryQueueIsBoundedPerViewer() {
        BlockTransitionRetryQueue queue = new BlockTransitionRetryQueue();
        UUID viewer = UUID.randomUUID();
        boolean evicted = false;
        for (int index = 0; index <= BlockTransitionRetryQueue.MAX_RETRIES_PER_VIEWER; index++) {
            evicted |= queue.enqueue(viewer, SHOW, BLOCK, tileEntity(), index + 1,
                    2, 7L, 1, 0);
        }

        assertTrue(evicted);
        assertEquals(BlockTransitionRetryQueue.MAX_RETRIES_PER_VIEWER, queue.size(viewer));
    }

    @Test
    void operationAndModeGenerationArePartOfDeduplicationKey() {
        BlockTransitionRetryQueue queue = new BlockTransitionRetryQueue();
        UUID viewer = UUID.randomUUID();
        TrackedTileEntity<?> tile = tileEntity();

        queue.enqueue(viewer, SHOW, BLOCK, tile, 5, 2, 7L, 1, 0);
        queue.enqueue(viewer, HIDE, BLOCK, tile, 5, 2, 7L, 1, 0);
        queue.enqueue(viewer, SHOW, BLOCK, tile, 5, 2, 9L, 1, 0);

        assertEquals(3, queue.size(viewer));
    }

    @SuppressWarnings("unchecked")
    private static TrackedTileEntity<?> tileEntity() {
        return (TrackedTileEntity<?>) Proxy.newProxyInstance(
                TrackedTileEntity.class.getClassLoader(),
                new Class<?>[]{TrackedTileEntity.class},
                (object, method, args) -> {
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    if (method.getReturnType() == long.class) return 0L;
                    if (method.getReturnType() == char.class) return (char) 0;
                    return null;
                });
    }
}
