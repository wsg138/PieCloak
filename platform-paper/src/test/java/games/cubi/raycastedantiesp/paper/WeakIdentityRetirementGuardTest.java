package games.cubi.raycastedantiesp.paper;

import games.cubi.raycastedantiesp.core.entity.EntityBypassRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class WeakIdentityRetirementGuardTest {
    @AfterEach
    void resetRegistry() {
        EntityBypassRegistry.reset();
    }

    @Test
    void removedNpcOwnerCannotRestoreItsBypassButNewOwnerCanUseId() {
        int entityId = 1401;
        var guard = new WeakIdentityRetirementGuard<Object>();
        var scope = EntityBypassRegistry.openFancyNpcScope();
        Object oldNpc = new Object();
        Object replacementNpc = new Object();

        assertTrue(guard.registerIfActive(oldNpc, entityId, () -> scope.addEntity(entityId)));
        assertTrue(EntityBypassRegistry.isBypassed(entityId));
        guard.retire(oldNpc, entityId, () -> scope.removeEntity(entityId));
        assertFalse(EntityBypassRegistry.isBypassed(entityId));

        assertFalse(guard.registerIfActive(oldNpc, entityId, () -> scope.addEntity(entityId)));
        assertFalse(EntityBypassRegistry.isBypassed(entityId));
        assertTrue(guard.registerIfActive(replacementNpc, entityId, () -> scope.addEntity(entityId)));
        assertTrue(EntityBypassRegistry.isBypassed(entityId));

        assertFalse(guard.registerIfActive(oldNpc, entityId, () -> scope.addEntity(entityId)));
        assertTrue(EntityBypassRegistry.isBypassed(entityId));
        guard.retire(replacementNpc, entityId, () -> scope.removeEntity(entityId));
        assertFalse(EntityBypassRegistry.isBypassed(entityId));
    }

    @Test
    void distinctEqualOwnersAreNotConflated() {
        record EqualOwner(int value) {
            @Override
            public boolean equals(Object other) {
                return other instanceof EqualOwner;
            }
            @Override
            public int hashCode() {
                return 1;
            }
        }
        var guard = new WeakIdentityRetirementGuard<EqualOwner>();
        EqualOwner old = new EqualOwner(1);
        EqualOwner newOwner = new EqualOwner(2);
        guard.retire(old, 9, () -> {});
        assertFalse(guard.registerIfActive(old, 9, () -> {}));
        assertTrue(guard.registerIfActive(newOwner, 9, () -> {}));
    }

    @Test
    void duplicateOldOwnerRemovalCannotClearReplacementId() {
        int entityId = 1401;
        var guard = new WeakIdentityRetirementGuard<Object>();
        var scope = EntityBypassRegistry.openFancyNpcScope();
        Object oldNpc = new Object();
        Object newNpc = new Object();

        assertTrue(guard.registerIfActive(oldNpc, entityId, () -> scope.addEntity(entityId)));
        guard.retire(oldNpc, entityId, () -> scope.removeEntity(entityId));
        assertTrue(guard.registerIfActive(newNpc, entityId, () -> scope.addEntity(entityId)));

        guard.retire(oldNpc, entityId, () -> scope.removeEntity(entityId));
        assertTrue(EntityBypassRegistry.isBypassed(entityId));
        guard.retire(newNpc, entityId, () -> scope.removeEntity(entityId));
        assertFalse(EntityBypassRegistry.isBypassed(entityId));
    }
    @Test
    void sameOwnerMayCreateNewEntityIncarnationWithDifferentId() {
        var guard = new WeakIdentityRetirementGuard<Object>();
        var scope = EntityBypassRegistry.openFancyNpcScope();
        Object npc = new Object();
        assertTrue(guard.registerIfActive(npc, 100, () -> scope.addEntity(100)));
        guard.retire(npc, 100, () -> scope.removeEntity(100));
        assertFalse(guard.registerIfActive(npc, 100, () -> scope.addEntity(100)));
        assertTrue(guard.registerIfActive(npc, 101, () -> scope.addEntity(101)));
        assertTrue(EntityBypassRegistry.isBypassed(101));
        assertFalse(EntityBypassRegistry.isBypassed(100));
    }
    @Test
    void asyncCallbackAndRemoveCannotRacePastRetirement() throws Exception {
        for (int attempt = 0; attempt < 80; attempt++) {
            var guard = new WeakIdentityRetirementGuard<Object>();
            var scope = EntityBypassRegistry.openFancyNpcScope();
            Object owner = new Object();
            int entityId = 5000 + attempt;
            var start = new CountDownLatch(1);
            Thread callback = new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException interruption) {
                    Thread.currentThread().interrupt();
                    return;
                }
                for (int i = 0; i < 50; i++) {
                    guard.registerIfActive(owner, entityId, () -> scope.addEntity(entityId));
                }
            });
            callback.start();
            start.countDown();
            guard.retire(owner, entityId, () -> scope.removeEntity(entityId));
            callback.join(2000);
            assertFalse(callback.isAlive());
            assertFalse(EntityBypassRegistry.isBypassed(entityId));
        }
    }
}