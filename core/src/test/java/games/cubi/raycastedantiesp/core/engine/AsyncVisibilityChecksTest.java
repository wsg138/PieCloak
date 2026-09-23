package games.cubi.raycastedantiesp.core.engine;

import games.cubi.raycastedantiesp.core.entity.EntityBypassRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static games.cubi.raycastedantiesp.core.tracked.NettyEntity.NO_LEASHER;
import static games.cubi.raycastedantiesp.core.tracked.NettyEntity.NO_VEHICLE;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncVisibilityChecksTest {
    @AfterEach
    void resetBypasses() {
        EntityBypassRegistry.reset();
    }

    @Test
    void bypassedVehicleDoesNotForceVisibility() {
        int viewerEntityID = 1;
        int bypassedVehicleID = 20;
        EntityBypassRegistry.addEntity(bypassedVehicleID);

        assertFalse(AsyncVisibilityChecks.attachmentRequiresVisibility(
                NO_LEASHER, bypassedVehicleID, null, viewerEntityID));
    }

    @Test
    void viewerRelationshipsStillForceVisibility() {
        int viewerEntityID = 1;

        assertTrue(AsyncVisibilityChecks.attachmentRequiresVisibility(
                viewerEntityID, NO_VEHICLE, null, viewerEntityID));
        assertTrue(AsyncVisibilityChecks.attachmentRequiresVisibility(
                NO_LEASHER, viewerEntityID, null, viewerEntityID));
        assertTrue(AsyncVisibilityChecks.attachmentRequiresVisibility(
                NO_LEASHER, NO_VEHICLE, new int[]{viewerEntityID}, viewerEntityID));
    }
}
