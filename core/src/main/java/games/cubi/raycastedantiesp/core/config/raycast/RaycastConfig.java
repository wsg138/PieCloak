package games.cubi.raycastedantiesp.core.config.raycast;

import games.cubi.logs.Logger;
import games.cubi.raycastedantiesp.core.config.Config;
import games.cubi.raycastedantiesp.core.config.ConfigLoadException;
import games.cubi.raycastedantiesp.core.config.ConfigReader;
import org.spongepowered.configurate.ConfigurationNode;

public class RaycastConfig implements Config {
    private final boolean enabled;
    private final boolean hideSoundsWhenHidden;
    private final byte maxOccludingCount;
    private final short alwaysShowRadius;
    private final short raycastRadius;
    private final short hideOnSpawnDistance;
    private final short visibleRecheckIntervalTicks;

    public RaycastConfig(boolean enabled, boolean hideSoundsWhenHidden, int maxOccludingCount, int alwaysShowRadius,
                         int raycastRadius, int hideOnSpawnDistance, int visibleRecheckIntervalTicks) {
        this.enabled = enabled;
        this.hideSoundsWhenHidden = hideSoundsWhenHidden;
        this.maxOccludingCount = (byte) maxOccludingCount;
        this.alwaysShowRadius = (short) alwaysShowRadius;
        this.raycastRadius = (short) raycastRadius;
        this.hideOnSpawnDistance = (short) hideOnSpawnDistance;
        this.visibleRecheckIntervalTicks = (short) visibleRecheckIntervalTicks;
    }

    protected static RaycastConfig load(ConfigurationNode node, String path, boolean hasHideSoundsWhenHidden) {
        int maxOccludingCount = boundedInteger(node, path, "max-occluding-count", Byte.MAX_VALUE, 3);
        int alwaysShowRadius = boundedInteger(node, path, "always-show-radius", Short.MAX_VALUE, 8);
        int raycastRadius = boundedInteger(node, path, "raycast-radius", Short.MAX_VALUE, 48);
        int hideOnSpawnDistance = boundedInteger(node, path, "hide-on-spawn-distance", Short.MAX_VALUE, 32);
        int visibleRecheckIntervalTicks = boundedInteger(node, path, "visible-recheck-interval-ticks", Short.MAX_VALUE, 5);
        return new RaycastConfig(
                ConfigReader.bool(ConfigReader.node(node, "enabled"), path + ".enabled"),
                hasHideSoundsWhenHidden && ConfigReader.bool(ConfigReader.node(node, "hide-sounds-when-hidden"), path + ".hide-sounds-when-hidden"),
                maxOccludingCount,
                alwaysShowRadius,
                raycastRadius,
                hideOnSpawnDistance,
                visibleRecheckIntervalTicks
        );
    }

    private static int boundedInteger(ConfigurationNode node, String path, String key, int max, int fallback) {
        String fullPath = path + "." + key;
        int value = ConfigReader.integer(ConfigReader.node(node, key), fullPath);
        if (value >= -1 && value <= max) {
            return value;
        }
        Logger.warning(fullPath + " must be between -1 and " + max + " but was " + value + ". Defaulting to " + fallback + ".", 4, RaycastConfig.class);
        return fallback;
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean hideSoundsWhenHidden() {
        return hideSoundsWhenHidden;
    }

    public byte getMaxOccludingCount() {
        return maxOccludingCount;
    }

    public short getAlwaysShowRadius() {
        return alwaysShowRadius;
    }

    public short getRaycastRadius() {
        return raycastRadius;
    }

    public short hideOnSpawnDistance() {
        return hideOnSpawnDistance;
    }

    public short getVisibleRecheckIntervalTicks() {
        return visibleRecheckIntervalTicks;
    }
}
