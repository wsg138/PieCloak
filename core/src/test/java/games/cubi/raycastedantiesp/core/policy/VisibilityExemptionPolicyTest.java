package games.cubi.raycastedantiesp.core.policy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VisibilityExemptionPolicyTest {
    private static final VisibilityExemptionPolicy ACTIVE = (world, x, y, z) -> false;

    @Test
    void activePolicyCapsSlowOrDisabledVisibleRechecks() {
        assertEquals(20, ACTIVE.effectiveVisibleRecheckTicks(-1));
        assertEquals(20, ACTIVE.effectiveVisibleRecheckTicks(200));
        assertEquals(5, ACTIVE.effectiveVisibleRecheckTicks(5));
        assertEquals(0, ACTIVE.effectiveVisibleRecheckTicks(0));
    }

    @Test
    void disabledPolicyPreservesConfiguredRecheck() {
        assertEquals(-1, VisibilityExemptionPolicy.DISABLED.effectiveVisibleRecheckTicks(-1));
        assertEquals(200, VisibilityExemptionPolicy.DISABLED.effectiveVisibleRecheckTicks(200));
    }
}
