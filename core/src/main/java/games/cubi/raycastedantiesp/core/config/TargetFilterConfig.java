/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2025-2026 Cubicake and Contributors.
 * This file is part of PieCloak, a modified fork of RaycastedAntiESP.
 */

package games.cubi.raycastedantiesp.core.config;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

import java.util.List;
import java.util.Locale;

/**
 * PieCloak's allowlist of packet-visible targets managed by the anti-ESP engine.
 */
@ConfigSerializable
public record TargetFilterConfig(
        boolean enabled,
        Mode mode,
        List<String> entities,
        List<String> blockEntities,
        List<String> blockEntityGroups
) implements Config {
    public static final TargetFilterConfig DEFAULT = new TargetFilterConfig(true, Mode.ALLOWLIST,
            List.of("minecraft:villager", "minecraft:copper_golem", "minecraft:armadillo", "minecraft:wolf",
                    "minecraft:cat", "minecraft:ocelot", "minecraft:allay", "minecraft:bee", "minecraft:iron_golem",
                    "minecraft:snow_golem", "minecraft:item_frame", "minecraft:glow_item_frame", "minecraft:armor_stand", "minecraft:painting"),
            List.of("minecraft:campfire", "minecraft:soul_campfire", "minecraft:decorated_pot", "minecraft:bell",
                    "minecraft:jukebox", "minecraft:conduit", "minecraft:beacon", "minecraft:moving_piston"),
            List.of("shulker_boxes", "signs", "hanging_signs", "banners", "wall_banners", "beds", "heads_and_skulls"));

    public TargetFilterConfig {
        entities = List.copyOf(entities);
        blockEntities = List.copyOf(blockEntities);
        blockEntityGroups = List.copyOf(blockEntityGroups);
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
