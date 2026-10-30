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
        var scope = EntityBypassRegistry.openFancyNpcScope();
        var guard = new WeakIdentityRetirementGuard<Object>(scope);
        Object oldNpc = new Object();
        Object replacementNpc = new Object();

        assertTrue(guard.registerIfActive(oldNpc, entityId));
        assertTrue(EntityBypassRegistry.isBypassed(entityId));
        guard.retire(oldNpc, entityId);
        assertFalse(EntityBypassRegistry.isBypassed(entityId));

        assertFalse(guard.registerIfActive(oldNpc, entityId));
        assertFalse(EntityBypassRegistry.isBypassed(entityId));
        assertTrue(guard.registerIfActive(replacementNpc, entityId));
        assertTrue(EntityBypassRegistry.isBypassed(entityId));

        assertFalse(guard.registerIfActive(oldNpc, entityId));
        assertTrue(EntityBypassRegistry.isBypassed(entityId));
        guard.retire(replacementNpc, entityId);
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
        var guard = new WeakIdentityRetirementGuard<EqualOwner>(EntityBypassRegistry.openFancyNpcScope());
        EqualOwner old = new EqualOwner(1);
        EqualOwner newOwner = new EqualOwner(2);
        guard.retire(old, 9);
        assertFalse(guard.registerIfActive(old, 9));
        assertTrue(guard.registerIfActive(newOwner, 9));
    }

    @Test
    void duplicateOldOwnerRemovalCannotClearReplacementId() {
        int entityId = 1401;
        var scope = EntityBypassRegistry.openFancyNpcScope();
        var guard = new WeakIdentityRetirementGuard<Object>(scope);
        Object oldNpc = new Object();
        Object newNpc = new Object();

        assertTrue(guard.registerIfActive(oldNpc, entityId));
        guard.retire(oldNpc, entityId);
        assertTrue(guard.registerIfActive(newNpc, entityId));

        guard.retire(oldNpc, entityId);
        assertTrue(EntityBypassRegistry.isBypassed(entityId));
        guard.retire(newNpc, entityId);
        assertFalse(EntityBypassRegistry.isBypassed(entityId));
    }
    @Test
    void sameOwnerMayCreateNewEntityIncarnationWithDifferentId() {
        var scope = EntityBypassRegistry.openFancyNpcScope();
        var guard = new WeakIdentityRetirementGuard<Object>(scope);
        Object npc = new Object();
        assertTrue(guard.registerIfActive(npc, 100));
        guard.retire(npc, 100);
        assertFalse(guard.registerIfActive(npc, 100));
        assertTrue(guard.registerIfActive(npc, 101));
        assertTrue(EntityBypassRegistry.isBypassed(101));
        assertFalse(EntityBypassRegistry.isBypassed(100));
    }
    @Test
    void olderRetiredIdCannotReturnAfterNewIncarnationOfSameObject() {
        var scope = EntityBypassRegistry.openFancyNpcScope();
        var guard = new WeakIdentityRetirementGuard<Object>(scope);
        Object owner = new Object();
        assertTrue(guard.registerIfActive(owner, 100));
        guard.retire(owner, 100);
        assertTrue(guard.registerIfActive(owner, 101));
        guard.retire(owner, 101);
        assertFalse(guard.registerIfActive(owner, 100));
        assertFalse(EntityBypassRegistry.isBypassed(100));
    }

    @Test
    void lateRemovalOfOldOwnerCannotClearReusedIdClaimedByReplacement() {
        var scope = EntityBypassRegistry.openFancyNpcScope();
        var guard = new WeakIdentityRetirementGuard<Object>(scope);
        Object original = new Object();
        Object replacement = new Object();
        int id = 500;
        assertTrue(guard.registerIfActive(original, id));
        assertTrue(guard.registerIfActive(replacement, id));
        guard.retire(original, id);
        assertTrue(EntityBypassRegistry.isBypassed(id), "replacement must remain active");
        guard.retire(replacement, id);
        assertFalse(EntityBypassRegistry.isBypassed(id));
    }
    @Test
    void retiredUnregisteredOwnerDoesNotClearAnotherOwnersExemption() {
        var scope = EntityBypassRegistry.openFancyNpcScope();
        var guard = new WeakIdentityRetirementGuard<Object>(scope);
        Object absentOld = new Object();
        Object newOwner = new Object();
        assertTrue(guard.registerIfActive(newOwner, 600));
        guard.retire(absentOld, 600);
        assertTrue(EntityBypassRegistry.isBypassed(600));
        guard.retire(newOwner, 600);
        assertFalse(EntityBypassRegistry.isBypassed(600));
    }

    @Test
    void weakOwnerQueueDrainReleasesBypassAndPreservesOtherActiveOwner() throws Exception {
        var scope = EntityBypassRegistry.openFancyNpcScope();
        var guard = new WeakIdentityRetirementGuard<Object>(scope);
        Object orphan = new Object();
        Object live = new Object();
        assertTrue(guard.registerIfActive(orphan, 650));
        assertTrue(guard.registerIfActive(live, 650));

        // Deterministically emulate reference collection instead of relying on System.gc().
        var ownersField = WeakIdentityRetirementGuard.class.getDeclaredField("owners");
        ownersField.setAccessible(true);
        java.util.Map<?, ?> owners = (java.util.Map<?, ?>) ownersField.get(guard);
        java.lang.ref.Reference<?> orphanKey = owners.keySet().stream()
                .map(key -> (java.lang.ref.Reference<?>) key)
                .filter(key -> key.get() == orphan)
                .findFirst().orElseThrow();
        orphanKey.clear();
        assertTrue(orphanKey.enqueue());

        assertTrue(guard.registerIfActive(live, 650)); // drains queued orphan
        assertTrue(EntityBypassRegistry.isBypassed(650));
        guard.retire(live, 650);
        assertFalse(EntityBypassRegistry.isBypassed(650));
    }
    @Test
    void changingNativeIdWithoutRemoveRetiresPriorIncarnation() {
        var scope = EntityBypassRegistry.openFancyNpcScope();
        var guard = new WeakIdentityRetirementGuard<Object>(scope);
        Object owner = new Object();
        assertTrue(guard.registerIfActive(owner, 700));
        assertTrue(guard.registerIfActive(owner, 701));
        assertFalse(EntityBypassRegistry.isBypassed(700),
                "one NPC owner cannot represent both old and new native entities");
        assertFalse(guard.registerIfActive(owner, 700),
                "delayed callback for previous native ID must stay retired");
        assertTrue(EntityBypassRegistry.isBypassed(701));
    }
    @Test
    void asyncCallbackAndRemoveCannotRacePastRetirement() throws Exception {
        for (int attempt = 0; attempt < 80; attempt++) {
            var scope = EntityBypassRegistry.openFancyNpcScope();
            var guard = new WeakIdentityRetirementGuard<Object>(scope);
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
                    guard.registerIfActive(owner, entityId);
                }
            });
            callback.start();
            start.countDown();
            guard.retire(owner, entityId);
            callback.join(2000);
            assertFalse(callback.isAlive());
            assertFalse(EntityBypassRegistry.isBypassed(entityId));
        }
    }
}