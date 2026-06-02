package games.cubi.raycastedantiesp.core.config;

import org.spongepowered.configurate.ConfigurationNode;

import java.util.List;

public record TargetFilterConfig(
        boolean enabled,
        Mode mode,
        List<String> entities,
        List<String> blockEntities,
        List<String> blockEntityGroups
) implements Config {
    public static TargetFilterConfig load(ConfigurationNode root) {
        ConfigurationNode filter = ConfigReader.node(root, "target-filter");
        Mode mode = Mode.fromConfigName(ConfigReader.string(ConfigReader.node(filter, "mode"), "target-filter.mode"));
        if (mode != Mode.ALLOWLIST) {
            throw new ConfigLoadException("target-filter.mode only supports ALLOWLIST");
        }
        return new TargetFilterConfig(
                ConfigReader.bool(ConfigReader.node(filter, "enabled"), "target-filter.enabled"),
                mode,
                ConfigReader.stringList(ConfigReader.node(filter, "entities"), "target-filter.entities"),
                ConfigReader.stringList(ConfigReader.node(filter, "block-entities"), "target-filter.block-entities"),
                ConfigReader.stringList(ConfigReader.node(filter, "block-entity-groups"), "target-filter.block-entity-groups")
        );
    }

    public static String normalizeKey(String raw) {
        String s = raw.trim().toLowerCase();
        return s.startsWith("minecraft:") ? s.substring("minecraft:".length()) : s;
    }

    public enum Mode implements ConfigEnum {
        ALLOWLIST("ALLOWLIST");

        private final String configName;

        Mode(String configName) {
            this.configName = configName;
        }

        public static Mode fromConfigName(String name) {
            for (Mode mode : values()) {
                if (mode.configName.equalsIgnoreCase(name)) {
                    return mode;
                }
            }
            throw new ConfigLoadException("Invalid target-filter.mode '" + name + "'. Valid modes: ALLOWLIST");
        }

        @Override
        public String[] getValues() {
            return new String[] {configName};
        }
    }
}
