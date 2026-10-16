package games.cubi.raycastedantiesp.core.players;

import games.cubi.raycastedantiesp.core.tracked.NettyEntity;
import games.cubi.raycastedantiesp.core.utils.BaseEntitySpawnTask;
import games.cubi.raycastedantiesp.core.utils.Clearable;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Regression contract for issue #17: an expired missing-spawn queue fails closed
 * without poisoning a subsequent real spawn, destroy, or client reset. */
class NettyDataPostSpawnQueueTest {
    private static final int ENTITY_ID = 92;

    @Test
    void expiresAtSixteenTicksWithoutRunningStaleReconciliation() {
        NettyData data = newData();
        AtomicInteger executions = new AtomicInteger();
        TestTask first = new TestTask(100, executions);
        data.addPostEntitySpawnTask(ENTITY_ID, first);

        data.evictOldPendingPostSpawnTasks(115);
        assertSame(first, data.consumePendingPostSpawnTasksForEntity(ENTITY_ID));
        data.addPostEntitySpawnTask(ENTITY_ID, first);
        data.evictOldPendingPostSpawnTasks(116);

        assertNull(data.consumePendingPostSpawnTasksForEntity(ENTITY_ID));
        data.addPostEntitySpawnTask(ENTITY_ID, new TestTask(117, executions));
        assertNull(data.consumePendingPostSpawnTasksForEntity(ENTITY_ID));
        assertEquals(0, executions.get());
    }

    @Test
    void realSpawnReleasesSuppressionAndExecutesOnlyFreshTasks() {
        NettyData data = newData();
        AtomicInteger executions = new AtomicInteger();
        data.addPostEntitySpawnTask(ENTITY_ID, new TestTask(20, executions));
        data.evictOldPendingPostSpawnTasks(36);
        data.addPostEntitySpawnTask(ENTITY_ID, new TestTask(37, executions));

        data.runPendingPostSpawnTaskForEntity(ENTITY_ID);
        assertEquals(0, executions.get());
        data.addPostEntitySpawnTask(ENTITY_ID, new TestTask(38, executions));
        data.runPendingPostSpawnTaskForEntity(ENTITY_ID);
        data.runPendingPostSpawnTaskForEntity(ENTITY_ID);

        assertEquals(1, executions.get());
    }

    @Test
    void destroyAndReusedEntityIdCanQueueAndReplayAgain() {
        NettyData data = newData();
        AtomicInteger executions = new AtomicInteger();
        data.addPostEntitySpawnTask(ENTITY_ID, new TestTask(0, executions));
        data.evictOldPendingPostSpawnTasks(16);
        data.clearPendingPostSpawnTasksForEntity(ENTITY_ID);

        data.addPostEntitySpawnTask(ENTITY_ID, new TestTask(17, executions));
        data.runPendingPostSpawnTaskForEntity(ENTITY_ID);

        assertEquals(1, executions.get());
    }

    @Test
    void expirySuppressesOnlyItsViewerAndEntityId() {
        NettyData firstViewer = newData();
        NettyData secondViewer = newData();
        AtomicInteger executions = new AtomicInteger();
        firstViewer.addPostEntitySpawnTask(ENTITY_ID, new TestTask(10, executions));
        firstViewer.evictOldPendingPostSpawnTasks(26);

        firstViewer.addPostEntitySpawnTask(ENTITY_ID + 1, new TestTask(26, executions));
        secondViewer.addPostEntitySpawnTask(ENTITY_ID, new TestTask(26, executions));
        firstViewer.runPendingPostSpawnTaskForEntity(ENTITY_ID + 1);
        secondViewer.runPendingPostSpawnTaskForEntity(ENTITY_ID);

        assertEquals(2, executions.get());
        firstViewer.addPostEntitySpawnTask(ENTITY_ID, new TestTask(27, executions));
        assertNull(firstViewer.consumePendingPostSpawnTasksForEntity(ENTITY_ID));
    }

    @Test
    void disconnectResetClearsQuarantineAndPendingChain() {
        NettyData data = newData();
        AtomicInteger executions = new AtomicInteger();
        data.addPostEntitySpawnTask(ENTITY_ID, new TestTask(1, executions));
        data.evictOldPendingPostSpawnTasks(17);
        data.addPostEntitySpawnTask(ENTITY_ID + 1, new TestTask(18, executions));
        data.clear();

        data.runPendingPostSpawnTaskForEntity(ENTITY_ID + 1);
        assertEquals(0, executions.get());
        data.addPostEntitySpawnTask(ENTITY_ID, new TestTask(19, executions));
        data.runPendingPostSpawnTaskForEntity(ENTITY_ID);
        assertEquals(1, executions.get());
    }

    @Test
    void scheduledEvictionDoesNotRunUntilExplicitlyMarked() {
        NettyData data = newData();
        AtomicInteger executions = new AtomicInteger();
        TestTask pending = new TestTask(2, executions);
        data.addPostEntitySpawnTask(ENTITY_ID, pending);
        data.evictPendingPostSpawnTasksIfRequired(18);
        assertSame(pending, data.consumePendingPostSpawnTasksForEntity(ENTITY_ID));
        data.addPostEntitySpawnTask(ENTITY_ID, pending);
        data.markPendingPostSpawnTasksForEviction();
        data.evictPendingPostSpawnTasksIfRequired(18);

        assertNull(data.consumePendingPostSpawnTasksForEntity(ENTITY_ID));
        assertEquals(0, executions.get());
    }

    private static NettyData newData() {
        return new NettyData(new SelfEntity());
    }

    private static final class SelfEntity extends NettyEntity<Clearable> {
        private SelfEntity() {
            super(null, 0, 0, 0, 1, UUID.randomUUID(), true, 0, true);
        }
    }

    private static final class TestTask extends BaseEntitySpawnTask {
        private final AtomicInteger executions;

        private TestTask(int submittedTick, AtomicInteger executions) {
            super(submittedTick);
            this.executions = executions;
        }

        @Override
        public void run() {
            executions.incrementAndGet();
        }
    }
}
