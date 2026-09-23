package games.cubi.raycastedantiesp.paper.packets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityVisibilityPacketPolicyTest {
    @Test
    void hiddenOrUnknownManagedEntityReferenceIsSuppressed() {
        assertTrue(EntityVisibilityPacketPolicy.shouldSuppressClientEntityReference(
                false, false, true, false, false));
        assertTrue(EntityVisibilityPacketPolicy.shouldSuppressClientEntityReference(
                false, false, false, false, false));
        assertTrue(EntityVisibilityPacketPolicy.shouldSuppressClientEntityReference(
                false, false, true, true, false));
    }

    @Test
    void visibleSelfOrBypassedEntityReferenceIsAllowed() {
        assertFalse(EntityVisibilityPacketPolicy.shouldSuppressClientEntityReference(
                false, false, true, true, true));
        assertFalse(EntityVisibilityPacketPolicy.shouldSuppressClientEntityReference(
                false, true, true, true, true));
        assertFalse(EntityVisibilityPacketPolicy.shouldSuppressClientEntityReference(
                true, false, false, false, false));
    }

    @Test
    void damageSourcePacketIdsUseVanillaOffsetEncoding() {
        assertEquals(-1, EntityVisibilityPacketPolicy.decodeDamageSourceEntityID(0));
        assertEquals(5, EntityVisibilityPacketPolicy.decodeDamageSourceEntityID(6));
    }

    @Test
    void unresolvedRelationshipReferenceIsSuppressed() {
        assertTrue(EntityVisibilityPacketPolicy.shouldSuppressUnresolvedReference(false, false));
    }

    @Test
    void trackedOrBypassedRelationshipReferenceIsAllowed() {
        assertFalse(EntityVisibilityPacketPolicy.shouldSuppressUnresolvedReference(false, true));
        assertFalse(EntityVisibilityPacketPolicy.shouldSuppressUnresolvedReference(true, false));
    }
}
