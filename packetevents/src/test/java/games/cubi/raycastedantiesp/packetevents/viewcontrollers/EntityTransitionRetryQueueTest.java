package games.cubi.raycastedantiesp.packetevents.viewcontrollers;

import games.cubi.raycastedantiesp.core.view.EntityView;
import games.cubi.raycastedantiesp.core.view.EntityViewTransition;
import games.cubi.raycastedantiesp.packetevents.tracked.PacketEventsEntity;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static games.cubi.raycastedantiesp.packetevents.viewcontrollers.EntityTransitionRetryQueue.RetryResult.CAPACITY_REJECTED;
import static games.cubi.raycastedantiesp.packetevents.viewcontrollers.EntityTransitionRetryQueue.RetryResult.ENQUEUED;

class EntityTransitionRetryQueueTest {
    @Test
    void newerOppositeTransitionSupersedesStaleRepair() {
        EntityTransitionRetryQueue<String> queue = new EntityTransitionRetryQueue<>();
        UUID viewer = UUID.randomUUID();
        EntityView<PacketEventsEntity> view = view(false);
        PacketEventsEntity entity = entity(17);

        EntityTransitionWork<String> show = work(viewer, view, entity, EntityViewTransition.Type.SHOW);
        assertTrue(show.recordFailure(10));
        assertEquals(ENQUEUED, queue.retry(show));

        assertSame(show, queue.cancel(viewer, view, entity.entityUUID()));
        EntityTransitionWork<String> hide = work(viewer, view, entity, EntityViewTransition.Type.HIDE);
        assertTrue(hide.recordFailure(10));
        assertEquals(ENQUEUED, queue.retry(hide));

        List<EntityTransitionWork<String>> retries = queue.drainDue(viewer, 11);
        assertEquals(1, retries.size());
        assertSame(hide, retries.getFirst());
    }

    @Test
    void canceledRepairRetainsPacketConfirmedClientState() {
        EntityTransitionRetryQueue<String> queue = new EntityTransitionRetryQueue<>();
        UUID viewer = UUID.randomUUID();
        EntityView<PacketEventsEntity> view = view(false);
        PacketEventsEntity entity = entity(19);
        EntityTransitionWork<String> show = work(viewer, view, entity, EntityViewTransition.Type.SHOW);
        FailingVisibility visibility = new FailingVisibility();

        try {
            show.execute(packet -> {}, visibility::set);
        } catch (TestFailure expected) {
            assertTrue(show.recordFailure(0));
            assertEquals(ENQUEUED, queue.retry(show));
        }

        EntityTransitionWork<String> canceled = queue.cancel(viewer, view, entity.entityUUID());
        assertSame(show, canceled);
        assertEquals(Boolean.TRUE, canceled.confirmedClientVisibility());
        assertFalse(queue.hasPending(viewer));
    }

    @Test
    void retryBackoffDefersWorkUntilItsDueTick() {
        EntityTransitionRetryQueue<String> queue = new EntityTransitionRetryQueue<>();
        UUID viewer = UUID.randomUUID();
        EntityTransitionWork<String> work = work(viewer, view(false), entity(18), EntityViewTransition.Type.SHOW);

        assertTrue(work.recordFailure(100));
        assertEquals(ENQUEUED, queue.retry(work));

        assertTrue(queue.drainDue(viewer, 100).isEmpty());
        assertEquals(List.of(work), queue.drainDue(viewer, 101));
        assertFalse(queue.hasPending(viewer));
    }

    @Test
    void viewerAndEntityCleanupRemovePendingWork() {
        EntityTransitionRetryQueue<String> queue = new EntityTransitionRetryQueue<>();
        UUID viewer = UUID.randomUUID();
        EntityView<PacketEventsEntity> view = view(false);
        PacketEventsEntity first = entity(20);
        PacketEventsEntity second = entity(21);

        EntityTransitionWork<String> firstWork = work(viewer, view, first, EntityViewTransition.Type.SHOW);
        EntityTransitionWork<String> secondWork = work(viewer, view, second, EntityViewTransition.Type.SHOW);
        assertTrue(firstWork.recordFailure(0));
        assertTrue(secondWork.recordFailure(0));
        assertEquals(ENQUEUED, queue.retry(firstWork));
        assertEquals(ENQUEUED, queue.retry(secondWork));

        queue.clearEntities(viewer, new int[]{first.entityID()});
        assertEquals(1, queue.pendingCount(viewer));

        queue.clear(viewer);
        assertEquals(0, queue.pendingCount());
        assertFalse(queue.hasPending(viewer));
    }

    @Test
    void capacityRejectsNewWorkWithoutEvictingQueuedRepairs() {
        EntityTransitionRetryQueue<String> queue = new EntityTransitionRetryQueue<>();
        UUID crowdedViewer = UUID.randomUUID();
        EntityView<PacketEventsEntity> crowdedView = view(false);

        for (int id = 0; id < EntityTransitionRetryQueue.MAX_PER_VIEWER; id++) {
            EntityTransitionWork<String> work = work(
                    crowdedViewer,
                    crowdedView,
                    entity(1000 + id),
                    EntityViewTransition.Type.SHOW
            );
            assertTrue(work.recordFailure(0));
            assertEquals(ENQUEUED, queue.retry(work));
        }
        EntityTransitionWork<String> rejectedViewerWork = work(
                crowdedViewer,
                crowdedView,
                entity(5000),
                EntityViewTransition.Type.SHOW
        );
        assertTrue(rejectedViewerWork.recordFailure(0));
        assertEquals(CAPACITY_REJECTED, queue.retry(rejectedViewerWork));
        assertEquals(EntityTransitionRetryQueue.MAX_PER_VIEWER, queue.pendingCount(crowdedViewer));

        int remainingGlobalCapacity = EntityTransitionRetryQueue.MAX_GLOBAL - queue.pendingCount();
        for (int id = 0; id < remainingGlobalCapacity; id++) {
            UUID viewer = UUID.randomUUID();
            EntityTransitionWork<String> work = work(
                    viewer,
                    view(false),
                    entity(10000 + id),
                    EntityViewTransition.Type.SHOW
            );
            assertTrue(work.recordFailure(0));
            assertEquals(ENQUEUED, queue.retry(work));
        }
        EntityTransitionWork<String> rejectedGlobalWork = work(
                UUID.randomUUID(),
                view(false),
                entity(20000),
                EntityViewTransition.Type.SHOW
        );
        assertTrue(rejectedGlobalWork.recordFailure(0));
        assertEquals(CAPACITY_REJECTED, queue.retry(rejectedGlobalWork));
        assertEquals(EntityTransitionRetryQueue.MAX_GLOBAL, queue.pendingCount());
    }

    private static EntityTransitionWork<String> work(
            UUID viewer,
            EntityView<PacketEventsEntity> view,
            PacketEventsEntity entity,
            EntityViewTransition.Type type
    ) {
        EntityTransitionPlan<String> plan = type == EntityViewTransition.Type.HIDE
                ? EntityTransitionPlan.hide("destroy")
                : EntityTransitionPlan.show(
                        "spawn", "position", "head", List.of(), null, null, List.of()
                );
        return new EntityTransitionWork<>(viewer, view, type, entity, 2, plan);
    }

    @SuppressWarnings("unchecked")
    private static EntityView<PacketEventsEntity> view(boolean playerView) {
        return (EntityView<PacketEventsEntity>) Proxy.newProxyInstance(
                EntityTransitionRetryQueueTest.class.getClassLoader(),
                new Class[]{EntityView.class},
                (proxy, method, args) -> method.getName().equals("isPlayerView")
                        ? playerView
                        : defaultValue(method.getReturnType())
        );
    }

    private static PacketEventsEntity entity(int entityID) {
        return new PacketEventsEntity(null, 0, 0, 0, entityID, UUID.randomUUID(), false, 0, true);
    }

    private static final class FailingVisibility {
        private boolean first = true;

        void set(boolean visible) {
            if (first) {
                first = false;
                throw new TestFailure();
            }
        }
    }

    private static final class TestFailure extends RuntimeException {
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        if (type == double.class) {
            return 0D;
        }
        if (type == char.class) {
            return (char) 0;
        }
        return null;
    }
}
