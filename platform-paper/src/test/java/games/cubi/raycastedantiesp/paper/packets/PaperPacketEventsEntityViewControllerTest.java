package games.cubi.raycastedantiesp.paper.packets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperPacketEventsEntityViewControllerTest {
    @Test
    void hiddenOrUnknownManagedEntitySoundIsSuppressed() {
        assertTrue(PaperPacketEventsEntityViewController.shouldSuppressEntitySound(false, true, true));
        assertTrue(PaperPacketEventsEntityViewController.shouldSuppressEntitySound(false, false, false));
    }

    @Test
    void visibleOrBypassedEntitySoundIsAllowed() {
        assertFalse(PaperPacketEventsEntityViewController.shouldSuppressEntitySound(false, true, false));
        assertFalse(PaperPacketEventsEntityViewController.shouldSuppressEntitySound(true, false, true));
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
