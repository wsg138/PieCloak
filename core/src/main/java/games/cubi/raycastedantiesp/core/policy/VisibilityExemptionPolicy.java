package games.cubi.raycastedantiesp.core.policy;

import games.cubi.locatables.api.Locatable;

import java.util.UUID;

/**
 * Target-location policy which can opt a position out of normal visibility hiding.
 * Implementations must be safe for concurrent packet-thread and engine-thread queries.
 */
@FunctionalInterface
public interface VisibilityExemptionPolicy {
    int MAX_EXEMPTION_RECHECK_TICKS = 20;

    VisibilityExemptionPolicy DISABLED = new VisibilityExemptionPolicy() {
        @Override
        public boolean isExempt(UUID world, double x, double y, double z) {
            return false;
        }

        @Override
        public boolean isActive() {
            return false;
        }
    };

    boolean isExempt(UUID world, double x, double y, double z);

    default boolean isExempt(Locatable location) {
        return location != null
                && location.world() != null
                && isExempt(location.world(), location.x(), location.y(), location.z());
    }

    default boolean isActive() {
        return true;
    }

    /**
     * Exemption changes must be noticed even when ordinary visible-target rechecks are disabled or slow.
     */
    default int effectiveVisibleRecheckTicks(int configuredRecheckTicks) {
        if (!isActive()) {
            return configuredRecheckTicks;
        }
        return configuredRecheckTicks < 0
                ? MAX_EXEMPTION_RECHECK_TICKS
                : Math.min(configuredRecheckTicks, MAX_EXEMPTION_RECHECK_TICKS);
    }
}
