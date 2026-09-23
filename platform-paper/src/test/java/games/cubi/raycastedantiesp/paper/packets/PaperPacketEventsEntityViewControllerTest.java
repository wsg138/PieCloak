package games.cubi.raycastedantiesp.paper.packets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperPacketEventsEntityViewControllerTest {
    @Test
    void hiddenManagedEntitySoundIsSuppressed() {
        assertTrue(PaperPacketEventsEntityViewController.shouldSuppressEntitySound(false, true));
    }

    @Test
    void visibleOrBypassedEntitySoundIsAllowed() {
        assertFalse(PaperPacketEventsEntityViewController.shouldSuppressEntitySound(false, false));
        assertFalse(PaperPacketEventsEntityViewController.shouldSuppressEntitySound(true, true));
    }
}
