package games.cubi.raycastedantiesp.core.config.engine;

import games.cubi.raycastedantiesp.core.config.ConfigEnum;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public enum EngineMode implements ConfigEnum {
    ASYNC("async", "simple"),
    NETTY("netty");

    private final String configName;
    private final @Nullable String aliasName;

    EngineMode(String configName) {
        this.configName = configName;
        aliasName = null;
    }

    EngineMode(String configName, @NotNull String aliasName) {
        this.configName = configName;
        this.aliasName = aliasName;
    }

    public String getName() {
        return configName;
    }

    public static @Nullable EngineMode fromString(String name) {
        for (EngineMode mode : values()) {
            if (mode.configName.equalsIgnoreCase(name) || name.equalsIgnoreCase(mode.aliasName)) {
                return mode;
            }
        }
        return null;
    }

    @Override
    public String[] getValues() {
        return new String[] {NETTY.configName, ASYNC.configName};
    }
}
