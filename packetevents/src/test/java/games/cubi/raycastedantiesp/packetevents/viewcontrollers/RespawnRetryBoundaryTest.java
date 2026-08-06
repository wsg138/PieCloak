package games.cubi.raycastedantiesp.packetevents.viewcontrollers;

import games.cubi.raycastedantiesp.packetevents.testsupport.TestProxySupport;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import games.cubi.raycastedantiesp.core.tracked.TrackedTileEntity;
import games.cubi.raycastedantiesp.core.view.EntityView;
import games.cubi.raycastedantiesp.core.view.EntityViewTransition;
import games.cubi.raycastedantiesp.packetevents.tracked.PacketEventsEntity;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static games.cubi.raycastedantiesp.packetevents.viewcontrollers.BlockTransitionRetryQueue.Operation.SHOW;
import static games.cubi.raycastedantiesp.packetevents.viewcontrollers.BlockTransitionRetryQueue.Stage.BLOCK;
import static games.cubi.raycastedantiesp.packetevents.viewcontrollers.EntityTransitionRetryQueue.RetryResult.ENQUEUED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RespawnRetryBoundaryTest {
    @Test
    void queuedShowCannotSpawnEntityReusingPreRespawnIdAfterRespawnCleanup() {
        EntityTransitionRetryQueue<String> queue = new EntityTransitionRetryQueue<>();
        UUID viewer = UUID.randomUUID();
        EntityView<PacketEventsEntity> view = entityView();
        PacketEventsEntity oldEntity = entity(71);
        oldEntity.setClientVisible(false);
        EntityTransitionWork<String> queuedShow = new EntityTransitionWork<>(
                viewer,
                view,
                EntityViewTransition.Type.SHOW,
                oldEntity,
                2,
                EntityTransitionPlan.show(
                        "spawn", "position", "head", List.of(), null, null, List.of())
        );
        assertTrue(queuedShow.recordFailure(10));
        assertEquals(ENQUEUED, queue.retry(queuedShow));

        queue.clear(viewer);
        PacketEventsEntity replacement = entity(71);
        replacement.setClientVisible(false);
        AtomicInteger packetWrites = new AtomicInteger();
        for (EntityTransitionWork<String> retry : queue.drainDue(viewer, 100)) {
            retry.execute(ignored -> packetWrites.incrementAndGet(), replacement::setClientVisible);
        }

        assertFalse(queue.hasPending(viewer));
        assertEquals(0, packetWrites.get());
        assertFalse(replacement.clientVisible());
        assertEquals(oldEntity.entityID(), replacement.entityID());
        assertNotEquals(oldEntity.entityUUID(), replacement.entityUUID());
    }

    @Test
    void respawnCleanupDropsPendingBlockRepairBeforeItCanWrite() {
        BlockTransitionRetryQueue queue = new BlockTransitionRetryQueue();
        UUID viewer = UUID.randomUUID();
        queue.enqueue(new BlockTransitionRetryQueue.RetryRequest(viewer, SHOW, BLOCK, tileEntity(), 5, 2, 7L, 1, 10));

        queue.clear(viewer);

        assertFalse(queue.hasPending(viewer));
        assertTrue(queue.drainDue(viewer, 4, 100).isEmpty());
    }

    @Test
    void ordinaryNonRespawnPacketsDoNotSelectDestructiveInvalidation() {
        assertFalse(PacketEventsRespawnStateInvalidator.isRespawnPacket(
                PacketType.Play.Server.JOIN_GAME));
        assertFalse(PacketEventsRespawnStateInvalidator.isRespawnPacket(
                PacketType.Play.Server.SPAWN_ENTITY));
        assertTrue(PacketEventsRespawnStateInvalidator.isRespawnPacket(
                PacketType.Play.Server.RESPAWN));
    }

    @SuppressWarnings("unchecked")
    private static EntityView<PacketEventsEntity> entityView() {
        return (EntityView<PacketEventsEntity>) Proxy.newProxyInstance(
                TestProxySupport.contextClassLoader(),
                new Class[]{EntityView.class},
                (proxy, method, args) -> method.getName().equals("isPlayerView")
                        ? false
                        : TestProxySupport.defaultValue(method.getReturnType())
        );
    }

    private static PacketEventsEntity entity(int entityID) {
        return new PacketEventsEntity(
                null, 0, 0, 0, entityID, UUID.randomUUID(), false, 0, false);
    }

    @SuppressWarnings("unchecked")
    private static TrackedTileEntity<?> tileEntity() {
        return (TrackedTileEntity<?>) Proxy.newProxyInstance(
                TestProxySupport.contextClassLoader(),
                new Class<?>[]{TrackedTileEntity.class},
                (object, method, args) -> TestProxySupport.defaultValue(method.getReturnType())
        );
    }

}
