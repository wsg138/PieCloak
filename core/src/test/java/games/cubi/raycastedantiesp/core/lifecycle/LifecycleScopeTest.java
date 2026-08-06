package games.cubi.raycastedantiesp.core.lifecycle;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LifecycleScopeTest {
    @Test
    void startupFailureUnwindsEveryRegisteredResourceInReverseOrder() {
        List<Integer> closeOrder = new ArrayList<>();
        LifecycleScope scope = new LifecycleScope();
        scope.onClose(() -> closeOrder.add(1));
        scope.onClose(() -> { closeOrder.add(2); throw new IllegalStateException("cleanup failure"); });
        scope.onClose(() -> closeOrder.add(3));

        RuntimeException failure = assertThrows(RuntimeException.class, scope::close);

        assertEquals(List.of(3, 2, 1), closeOrder);
        assertEquals(1, failure.getSuppressed().length);
        assertTrue(scope.isClosed());
    }

    @Test
    void repeatedShutdownIsIdempotent() {
        AtomicInteger closes = new AtomicInteger();
        LifecycleScope scope = new LifecycleScope();
        scope.onClose(closes::incrementAndGet);

        scope.close();
        scope.close();

        assertEquals(1, closes.get());
    }

    @Test
    void lateRegistrationIsClosedImmediatelyAndCannotLeakIntoFreshLifecycle() {
        AtomicInteger oldCloses = new AtomicInteger();
        LifecycleScope oldScope = new LifecycleScope();
        oldScope.close();

        assertThrows(IllegalStateException.class,
                () -> oldScope.own((AutoCloseable) oldCloses::incrementAndGet));
        assertEquals(1, oldCloses.get());

        AtomicInteger freshCloses = new AtomicInteger();
        LifecycleScope freshScope = new LifecycleScope();
        freshScope.own((AutoCloseable) freshCloses::incrementAndGet);
        freshScope.close();

        assertEquals(1, freshCloses.get());
        assertEquals(1, oldCloses.get());
    }

    @Test
    void closedInstanceCannotReceiveWorkWhileFreshInstanceCan() {
        FakeListener oldListener = new FakeListener();
        LifecycleScope oldScope = new LifecycleScope();
        oldScope.own(oldListener);

        oldListener.receive();
        oldScope.close();
        oldListener.receive();

        FakeListener freshListener = new FakeListener();
        LifecycleScope freshScope = new LifecycleScope();
        freshScope.own(freshListener);
        freshListener.receive();

        assertEquals(1, oldListener.received());
        assertEquals(1, freshListener.received());
        freshScope.close();
    }

    private static final class FakeListener implements AutoCloseable {
        private int receivedCount;
        private boolean active = true;

        void receive() {
            if (active) {
                receivedCount++;
            }
        }

        int received() {
            return receivedCount;
        }

        @Override
        public void close() {
            active = false;
        }
    }
}
