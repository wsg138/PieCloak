package games.cubi.raycastedantiesp.packetevents.viewcontrollers;

import games.cubi.raycastedantiesp.core.tracked.TrackedEntity;
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

class EntityTransitionRetryQueueTest {
    @Test
    void duplicateRetryForSameTransitionKeepsLatestAttempt() {
        EntityTransitionRetryQueue queue = new EntityTransitionRetryQueue();
        UUID viewer = UUID.randomUUID();
        EntityView<PacketEventsEntity> view = view(false);
        TrackedEntity<?> entity = entity();

        queue.enqueue(viewer, view, EntityViewTransition.Type.SHOW, entity, 2, 1);
        queue.enqueue(viewer, view, EntityViewTransition.Type.SHOW, entity, 2, 2);

        assertTrue(queue.hasPending(viewer));
        List<EntityTransitionRetryQueue.Retry> retries = queue.drain(viewer);
        assertEquals(1, retries.size());
        assertSame(entity, retries.getFirst().entity());
        assertEquals(2, retries.getFirst().attempts());
        assertFalse(queue.hasPending(viewer));
    }

    @Test
    void differentTransitionTypesRemainIndependent() {
        EntityTransitionRetryQueue queue = new EntityTransitionRetryQueue();
        UUID viewer = UUID.randomUUID();
        EntityView<PacketEventsEntity> view = view(false);
        TrackedEntity<?> entity = entity();

        queue.enqueue(viewer, view, EntityViewTransition.Type.SHOW, entity, 2, 1);
        queue.enqueue(viewer, view, EntityViewTransition.Type.HIDE, entity, 2, 1);

        assertEquals(2, queue.drain(viewer).size());
    }

    @SuppressWarnings("unchecked")
    private static EntityView<PacketEventsEntity> view(boolean playerView) {
        return (EntityView<PacketEventsEntity>) Proxy.newProxyInstance(
                EntityTransitionRetryQueueTest.class.getClassLoader(),
                new Class[]{EntityView.class},
                (proxy, method, args) -> method.getName().equals("isPlayerView") ? playerView : defaultValue(method.getReturnType())
        );
    }

    @SuppressWarnings("unchecked")
    private static TrackedEntity<?> entity() {
        UUID uuid = UUID.randomUUID();
        return (TrackedEntity<?>) Proxy.newProxyInstance(
                EntityTransitionRetryQueueTest.class.getClassLoader(),
                new Class[]{TrackedEntity.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "entityUUID" -> uuid;
                    case "entityID" -> 17;
                    default -> defaultValue(method.getReturnType());
                }
        );
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
