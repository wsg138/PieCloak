package games.cubi.raycastedantiesp.core.engine;

import java.util.concurrent.atomic.AtomicReference;

/** Lock-free selection of the timing sink used by a newly starting tick or skip record. */
final class TimingStatsSelector {
    private final AtomicReference<TimingStats> current = new AtomicReference<>(TimingStatsNoOp.INSTANCE);

    TimingStats select(boolean enabled) {
        if (!enabled) {
            current.set(TimingStatsNoOp.INSTANCE);
            return TimingStatsNoOp.INSTANCE;
        }

        TimingStats existing = current.get();
        if (existing != TimingStatsNoOp.INSTANCE) {
            return existing;
        }

        TimingStats created = new TimingStats();
        TimingStats witness = current.compareAndExchange(TimingStatsNoOp.INSTANCE, created);
        return witness == TimingStatsNoOp.INSTANCE ? created : witness;
    }
}
