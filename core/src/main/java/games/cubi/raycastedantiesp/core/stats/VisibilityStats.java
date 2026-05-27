package games.cubi.raycastedantiesp.core.stats;

import java.util.concurrent.atomic.AtomicLong;

public final class VisibilityStats {
    private static final VisibilityStats INSTANCE = new VisibilityStats();

    private final AtomicLong passes = new AtomicLong();
    private final AtomicLong totalNanos = new AtomicLong();
    private final AtomicLong lastNanos = new AtomicLong();
    private final AtomicLong maxNanos = new AtomicLong();
    private final AtomicLong lastRaycastChecks = new AtomicLong();
    private final AtomicLong totalRaycastChecks = new AtomicLong();

    private VisibilityStats() {
    }

    public static VisibilityStats get() {
        return INSTANCE;
    }

    public void recordVisibilityPass(long nanos, long raycastChecks) {
        passes.incrementAndGet();
        totalNanos.addAndGet(nanos);
        lastNanos.set(nanos);
        maxNanos.accumulateAndGet(nanos, Math::max);
        lastRaycastChecks.set(raycastChecks);
        totalRaycastChecks.addAndGet(raycastChecks);
    }

    public Snapshot snapshot() {
        long passCount = passes.get();
        long total = totalNanos.get();
        return new Snapshot(
                passCount,
                lastNanos.get(),
                passCount == 0 ? 0 : total / passCount,
                maxNanos.get(),
                lastRaycastChecks.get(),
                totalRaycastChecks.get()
        );
    }

    public record Snapshot(
            long passes,
            long lastNanos,
            long averageNanos,
            long maxNanos,
            long lastRaycastChecks,
            long totalRaycastChecks
    ) {
    }
}
