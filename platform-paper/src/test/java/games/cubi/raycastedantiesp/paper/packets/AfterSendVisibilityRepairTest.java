package games.cubi.raycastedantiesp.paper.packets;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AfterSendVisibilityRepairTest {
    @Test
    void successfulRepairFlushesExactlyOnce() {
        AtomicInteger repairs = new AtomicInteger();
        AtomicInteger flushes = new AtomicInteger();

        AfterSendVisibilityRepair.runAndFlush(repairs::incrementAndGet, flushes::incrementAndGet);

        assertEquals(1, repairs.get());
        assertEquals(1, flushes.get());
    }

    @Test
    void partialRepairStillFlushesBeforeFailureEscapes() {
        AtomicInteger flushes = new AtomicInteger();

        assertThrows(IllegalStateException.class, () -> AfterSendVisibilityRepair.runAndFlush(
                () -> { throw new IllegalStateException("repair failed after one or more writes"); },
                flushes::incrementAndGet));

        assertEquals(1, flushes.get());
    }
}
