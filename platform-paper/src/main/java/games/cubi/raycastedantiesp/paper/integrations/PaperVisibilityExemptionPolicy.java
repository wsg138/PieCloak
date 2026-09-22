package games.cubi.raycastedantiesp.paper.integrations;

import games.cubi.raycastedantiesp.core.policy.VisibilityExemptionPolicy;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/** Platform lifecycle for optional visibility-exemption integrations. */
public interface PaperVisibilityExemptionPolicy extends VisibilityExemptionPolicy, AutoCloseable {
    PaperVisibilityExemptionPolicy DISABLED = new PaperVisibilityExemptionPolicy() {
        @Override
        public boolean isExempt(UUID world, double x, double y, double z) {
            return false;
        }

        @Override
        public boolean isActive() {
            return false;
        }

        @Override
        public void enable(JavaPlugin plugin) {
        }

        @Override
        public void close() {
        }
    };

    void enable(JavaPlugin plugin);

    @Override
    default void close() {
    }
}
