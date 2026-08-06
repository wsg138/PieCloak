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
}
