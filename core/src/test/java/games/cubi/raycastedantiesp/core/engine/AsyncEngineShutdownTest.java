package games.cubi.raycastedantiesp.core.engine;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncEngineShutdownTest {
    @Test
    void repeatedShutdownCancelsPendingReservationAndRejectsFutureTicks() {
        AsyncEngine engine = new AsyncEngine(null, null, () -> 0, Runnable::run) { };

        assertTrue(engine.markTickRunning());
        assertTrue(engine.shutdownAndAwait(1, TimeUnit.SECONDS));
        assertTrue(engine.shutdownAndAwait(1, TimeUnit.SECONDS));
        assertTrue(engine.isShutdownRequested());
        assertTrue(engine.isQuiescent());
        assertFalse(engine.markTickRunning());
    }
}
