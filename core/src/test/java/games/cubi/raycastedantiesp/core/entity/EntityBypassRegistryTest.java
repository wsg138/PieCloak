package games.cubi.raycastedantiesp.core.entity;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityBypassRegistryTest {
    @BeforeEach
    @AfterEach
    void resetRegistry() {
        EntityBypassRegistry.reset();
    }

    @Test
    void despawnClearsEveryClassificationBeforeEntityIdReuse() {
        int entityId = 1_234_567_890;
        EntityBypassRegistry.addEntity(entityId);
        EntityBypassRegistry.addRelationshipSupportEntity(entityId);
        assertTrue(EntityBypassRegistry.isBypassed(entityId));
        assertTrue(EntityBypassRegistry.isRelationshipSupportEntity(entityId));
        assertTrue(EntityBypassRegistry.markEntityDespawned(entityId));
        assertFalse(EntityBypassRegistry.isBypassed(entityId));
        assertFalse(EntityBypassRegistry.isRelationshipSupportEntity(entityId));
        assertFalse(EntityBypassRegistry.markEntityDespawned(entityId));
    }

    @Test
    void resetClearsIdsRetainedAcrossPluginShutdown() {
        int entityId = 987_654_321;
        EntityBypassRegistry.addEntity(entityId);
        EntityBypassRegistry.addRelationshipSupportEntity(entityId);
        EntityBypassRegistry.reset();
        assertFalse(EntityBypassRegistry.isBypassed(entityId));
        assertFalse(EntityBypassRegistry.isRelationshipSupportEntity(entityId));
    }

    @Test
    void closingFancyNpcScopeKeepsOtherOwners() {
        int id=123;
        var npc=EntityBypassRegistry.openFancyNpcScope();
        var holo=EntityBypassRegistry.openFancyHologramScope();
        EntityBypassRegistry.addEntity(id);
        npc.addEntity(id);
        holo.addEntity(id);
        npc.close();
        assertTrue(EntityBypassRegistry.isBypassed(id));
        holo.close();
        assertTrue(EntityBypassRegistry.isBypassed(id));
        assertTrue(EntityBypassRegistry.markEntityDespawned(id));
        assertFalse(EntityBypassRegistry.isBypassed(id));
    }

    @Test
    void staleCallbacksAfterCloseDoNotReintroduceBypasses() {
        int id=456;
        var first=EntityBypassRegistry.openFancyNpcScope();
        first.addEntity(id);
        assertTrue(EntityBypassRegistry.isBypassed(id));
        first.close();
        first.addEntity(id);
        assertFalse(EntityBypassRegistry.isBypassed(id));
        var second=EntityBypassRegistry.openFancyNpcScope();
        second.addEntity(id);
        first.addEntity(id);
        first.close();
        assertTrue(EntityBypassRegistry.isBypassed(id));
        second.close();
        assertFalse(EntityBypassRegistry.isBypassed(id));
    }

    @Test
    void ownerSpecificRemovePreservesOtherOwner() {
        int id=789;
        var npc=EntityBypassRegistry.openFancyNpcScope();
        var holo=EntityBypassRegistry.openFancyHologramScope();
        npc.addEntity(id);
        holo.addEntity(id);
        assertTrue(npc.removeEntity(id));
        assertTrue(EntityBypassRegistry.isBypassed(id));
        assertTrue(holo.removeEntity(id));
        assertFalse(EntityBypassRegistry.isBypassed(id));
    }

    @Test
    void globalDespawnAndResetCloseCurrentScopes() {
        int id=1001;
        var npc=EntityBypassRegistry.openFancyNpcScope();
        var holo=EntityBypassRegistry.openFancyHologramScope();
        npc.addEntity(id);
        holo.addEntity(id);
        EntityBypassRegistry.addRelationshipSupportEntity(id);
        assertTrue(EntityBypassRegistry.markEntityDespawned(id));
        assertFalse(EntityBypassRegistry.isBypassed(id));
        assertFalse(EntityBypassRegistry.isRelationshipSupportEntity(id));
        npc.addEntity(id);
        EntityBypassRegistry.reset();
        npc.addEntity(id);
        holo.addEntity(id);
        assertFalse(EntityBypassRegistry.isBypassed(id));
    }

    @Test
    void concurrentCallbacksCannotReintroduceClosedScope() throws Exception {
        int id = 2026;
        for (int iteration = 0; iteration < 100; iteration++) {
            var scope = EntityBypassRegistry.openFancyNpcScope();
            var ready = new java.util.concurrent.CountDownLatch(1);
            var start = new java.util.concurrent.CountDownLatch(1);
            Thread callback = new Thread(() -> {
                ready.countDown();
                try {
                    start.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
                for (int j = 0; j < 100; j++) {
                    scope.addEntity(id);
                }
            });
            callback.start();
            assertTrue(ready.await(2, java.util.concurrent.TimeUnit.SECONDS));
            start.countDown();
            scope.close();
            callback.join(2000);
            assertFalse(callback.isAlive());
            assertFalse(EntityBypassRegistry.isBypassed(id));
        }
    }}