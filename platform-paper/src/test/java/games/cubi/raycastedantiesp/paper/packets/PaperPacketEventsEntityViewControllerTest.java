package games.cubi.raycastedantiesp.paper.packets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperPacketEventsEntityViewControllerTest {
    @Test
    void hiddenOrUnknownManagedEntityReferenceIsSuppressed() {
        assertTrue(PaperPacketEventsEntityViewController.shouldSuppressClientEntityReference(
                false, false, true, false, false));
        assertTrue(PaperPacketEventsEntityViewController.shouldSuppressClientEntityReference(
                false, false, false, false, false));
        assertTrue(PaperPacketEventsEntityViewController.shouldSuppressClientEntityReference(
                false, false, true, true, false));
    }

    @Test
    void visibleSelfOrBypassedEntityReferenceIsAllowed() {
        assertFalse(PaperPacketEventsEntityViewController.shouldSuppressClientEntityReference(
                false, false, true, true, true));
        assertFalse(PaperPacketEventsEntityViewController.shouldSuppressClientEntityReference(
                false, true, true, true, true));
        assertFalse(PaperPacketEventsEntityViewController.shouldSuppressClientEntityReference(
                true, false, false, false, false));
    }

    @Test
    void damageSourcePacketIdsUseVanillaOffsetEncoding() {
        assertEquals(-1, PaperPacketEventsEntityViewController.decodeDamageSourceEntityID(0));
        assertEquals(5, PaperPacketEventsEntityViewController.decodeDamageSourceEntityID(6));
    }

    @Test
    void unresolvedRelationshipReferenceIsSuppressed() {
        assertTrue(PaperPacketEventsEntityViewController.shouldSuppressUnresolvedReference(false, false));
    }

    @Test
    void trackedOrBypassedRelationshipReferenceIsAllowed() {
        assertFalse(PaperPacketEventsEntityViewController.shouldSuppressUnresolvedReference(false, true));
        assertFalse(PaperPacketEventsEntityViewController.shouldSuppressUnresolvedReference(true, false));
    }
}
