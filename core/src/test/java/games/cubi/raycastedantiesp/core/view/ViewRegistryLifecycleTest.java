package games.cubi.raycastedantiesp.core.view;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewRegistryLifecycleTest {
    @AfterEach
    void reset() {
        ViewRegistry.reset();
    }

    @Test
    void resetAllowsFreshFactoriesAfterDisableEnableCycle() {
        ViewRegistry.initialise(ignored -> null, ignored -> null, ignored -> null);
        assertTrue(ViewRegistry.isInitialised());
        assertThrows(IllegalStateException.class,
                () -> ViewRegistry.initialise(ignored -> null, ignored -> null, ignored -> null));

        ViewRegistry.reset();
        assertFalse(ViewRegistry.isInitialised());

        ViewRegistry.initialise(ignored -> null, ignored -> null, ignored -> null);
        assertTrue(ViewRegistry.isInitialised());
    }
}
