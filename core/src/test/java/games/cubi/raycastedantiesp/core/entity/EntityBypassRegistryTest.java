package games.cubi.raycastedantiesp.core.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityBypassRegistryTest {
    @Test
    void despawnClearsRelationshipSupportClassification() {
        int entityId = 1_234_567_890;
        EntityBypassRegistry.markEntityDespawned(entityId);
        EntityBypassRegistry.addRelationshipSupportEntity(entityId);

        assertTrue(EntityBypassRegistry.isRelationshipSupportEntity(entityId));
        assertTrue(EntityBypassRegistry.markEntityDespawned(entityId));
        assertFalse(EntityBypassRegistry.isRelationshipSupportEntity(entityId));
    }
}
