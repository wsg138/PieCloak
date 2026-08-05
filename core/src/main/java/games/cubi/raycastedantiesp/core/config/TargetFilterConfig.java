/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2025-2026 Cubicake and Contributors.
 * This file is part of PieCloak, a modified fork of RaycastedAntiESP.
 */

package games.cubi.raycastedantiesp.core.config;

import org.spongepowered.configurate.ConfigurationNode;

import java.util.List;
import java.util.Locale;

/**
 * PieCloak's allowlist of packet-visible targets managed by the anti-ESP engine.
 */
public record TargetFilterConfig(
        boolean enabled,
        Mode mode,
        List<String> entities,
        List<String> blockEntities,
        List<String> blockEntityGroups
) implements Config {
    public static final ConfigExtension<TargetFilterConfig> EXTENSION = new ConfigExtension<>() {
        @Override
        public Class<TargetFilterConfig> type() {
            return TargetFilterConfig.class;
        }

        @Override
        public TargetFilterConfig load(ConfigurationNode config, BlockProcessorConfig blockProcessorConfig) {
            return TargetFilterConfig.load(config);
        }

        @Override
        public boolean requiresRestart(TargetFilterConfig startupConfig, TargetFilterConfig nextConfig) {
            return !startupConfig.equals(nextConfig);
        }
    };

    public TargetFilterConfig {
        entities = List.copyOf(entities);
        blockEntities = List.copyOf(blockEntities);
        blockEntityGroups = List.copyOf(blockEntityGroups);
    }

    public static TargetFilterConfig load(ConfigurationNode root) {
        ConfigurationNode filter = ConfigReader.node(root, "target-filter");
        Mode mode = Mode.fromConfigName(ConfigReader.string(ConfigReader.node(filter, "mode"), "target-filter.mode"));
        return new TargetFilterConfig(
                ConfigReader.bool(ConfigReader.node(filter, "enabled"), "target-filter.enabled"),
                mode,
                ConfigReader.stringList(ConfigReader.node(filter, "entities"), "target-filter.entities"),
                ConfigReader.stringList(ConfigReader.node(filter, "block-entities"), "target-filter.block-entities"),
                ConfigReader.stringList(ConfigReader.node(filter, "block-entity-groups"), "target-filter.block-entity-groups")
        );
    }

    public static String normalizeKey(String raw) {
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return value.startsWith("minecraft:") ? value.substring("minecraft:".length()) : value;
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
            return new String[]{configName};
        }
    }
}
