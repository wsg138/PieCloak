/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2025-2026 Cubicake and Contributors.
 * This file is part of PieCloak, a modified fork of RaycastedAntiESP.
 */

package games.cubi.raycastedantiesp.paper.target;

import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.world.blockentity.BlockEntityType;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import games.cubi.logs.Logger;
import games.cubi.raycastedantiesp.core.config.ConfigManager;
import games.cubi.raycastedantiesp.core.config.TargetFilterConfig;
import games.cubi.raycastedantiesp.packetevents.target.PacketEventsTargetFilter;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class PaperTargetFilterService implements PacketEventsTargetFilter {
    private static final String MINECRAFT_NAMESPACE = "minecraft:";
    private static final Set<String> HEAD_OR_SKULL_PATHS = Set.of(
            "skeleton_skull",
            "skeleton_wall_skull",
            "wither_skeleton_skull",
            "wither_skeleton_wall_skull",
            "zombie_head",
            "zombie_wall_head",
            "player_head",
            "player_wall_head",
            "creeper_head",
            "creeper_wall_head",
            "dragon_head",
            "dragon_wall_head",
            "piglin_head",
            "piglin_wall_head"
    );
    private static final Set<String> DIRECT_BLOCK_ENTITY_TYPE_PATHS = Set.of(
            "campfire",
            "decorated_pot",
            "bell",
            "jukebox",
            "conduit",
            "beacon"
    );

    private final ConfigManager configManager;
    private volatile TargetFilterConfig loadedConfig;
    private volatile ResolvedTargetFilter resolved = ResolvedTargetFilter.disabled();

    public PaperTargetFilterService(ConfigManager configManager) {
        this.configManager = configManager;
        refresh(configManager.getExtensionConfig(TargetFilterConfig.class));
    }

    public void refresh() {
        refresh(configManager.getExtensionConfig(TargetFilterConfig.class));
    }

    @Override
    public boolean shouldCullEntity(EntityType entityType, boolean isPlayer) {
        ResolvedTargetFilter current = current();
        if (!current.enabled() || isPlayer || entityType == null) {
            return false;
        }
        return current.entityKeys().contains(normalizeKey(entityType.getName().toString()));
    }

    @Override
    public boolean shouldCullBlockState(int blockStateId) {
        ResolvedTargetFilter current = current();
        return current.enabled() && blockStateId >= 0 && current.blockStateIds().get(blockStateId);
    }

    @Override
    public boolean shouldCullBlockEntity(BlockEntityType blockEntityType) {
        ResolvedTargetFilter current = current();
        if (!current.enabled() || blockEntityType == null) {
            return false;
        }
        return current.safeBlockEntityTypeKeys().contains(normalizeKey(blockEntityType.getName().toString()));
    }

    @Override
    public boolean shouldCullTileEntity(int blockStateId) {
        return shouldCullBlockState(blockStateId);
    }

    public DebugSnapshot snapshot() {
        ResolvedTargetFilter current = current();
        return new DebugSnapshot(
                current.enabled(),
                current.entityKeys().size(),
                current.blockMaterialKeys().size(),
                current.blockStateIds().cardinality(),
                current.safeBlockEntityTypeKeys().size(),
                current.invalidEntries()
        );
    }

    private ResolvedTargetFilter current() {
        TargetFilterConfig config = configManager.getExtensionConfig(TargetFilterConfig.class);
        if (config != loadedConfig) {
            refresh(config);
        }
        return resolved;
    }

    private synchronized void refresh(TargetFilterConfig config) {
        if (config == loadedConfig) {
            return;
        }
        loadedConfig = config;
        if (config == null || !config.enabled()) {
            resolved = ResolvedTargetFilter.disabled();
            return;
        }

        List<String> invalidEntries = new ArrayList<>();
        Set<String> entityKeys = resolveEntities(config.entities(), invalidEntries);
        Set<Material> blockMaterials = resolveBlockMaterials(config, invalidEntries);
        BitSet blockStateIds = resolveBlockStateIds(blockMaterials);
        Set<String> safeBlockEntityTypeKeys = resolveSafeBlockEntityTypeKeys(blockMaterials);
        Set<String> blockMaterialKeys = new TreeSet<>();
        for (Material material : blockMaterials) {
            blockMaterialKeys.add(material.getKey().toString());
        }

        resolved = new ResolvedTargetFilter(
                true,
                Set.copyOf(entityKeys),
                Set.copyOf(blockMaterialKeys),
                blockStateIds,
                Set.copyOf(safeBlockEntityTypeKeys),
                List.copyOf(invalidEntries)
        );
        Logger.info("Target filter loaded: " + entityKeys.size() + " entity types, "
                + blockMaterials.size() + " block entity materials, "
                + blockStateIds.cardinality() + " block states.", 5, PaperTargetFilterService.class);
    }

    private Set<String> resolveEntities(List<String> configuredEntities, List<String> invalidEntries) {
        Set<String> entityKeys = new TreeSet<>();
        for (String configured : configuredEntities) {
            String key = normalizeConfiguredKey(configured, "target-filter.entities", invalidEntries);
            if (key == null) {
                continue;
            }
            NamespacedKey namespacedKey = NamespacedKey.fromString(key);
            org.bukkit.entity.EntityType bukkitType = namespacedKey == null ? null : Registry.ENTITY_TYPE.get(namespacedKey);
            if (bukkitType == null || bukkitType == org.bukkit.entity.EntityType.UNKNOWN) {
                warnSkipped("target-filter.entities", configured, "unknown entity type on this server", invalidEntries);
                continue;
            }
            if (bukkitType == org.bukkit.entity.EntityType.PLAYER) {
                warnSkipped("target-filter.entities", configured, "players are never culled", invalidEntries);
                continue;
            }
            entityKeys.add(key);
        }
        return entityKeys;
    }

    private Set<Material> resolveBlockMaterials(TargetFilterConfig config, List<String> invalidEntries) {
        Set<Material> blockMaterials = EnumSet.noneOf(Material.class);
        for (String configured : config.blockEntities()) {
            String key = normalizeConfiguredKey(configured, "target-filter.block-entities", invalidEntries);
            if (key == null) {
                continue;
            }
            Material material = materialByKey(key);
            if (material == null) {
                warnSkipped("target-filter.block-entities", configured, "unknown block material on this server", invalidEntries);
                continue;
            }
            if (!isTileStateMaterial(material)) {
                warnSkipped("target-filter.block-entities", configured, "material is not a block entity", invalidEntries);
                continue;
            }
            blockMaterials.add(material);
        }

        for (String configuredGroup : config.blockEntityGroups()) {
            String group = configuredGroup == null ? "" : configuredGroup.trim().toLowerCase(Locale.ROOT);
            if (group.startsWith(MINECRAFT_NAMESPACE)) {
                group = group.substring(MINECRAFT_NAMESPACE.length());
            }
            Set<Material> expanded = expandGroup(group);
            if (expanded == null) {
                warnSkipped("target-filter.block-entity-groups", configuredGroup, "unknown block entity group", invalidEntries);
                continue;
            }
            if (expanded.isEmpty()) {
                warnSkipped("target-filter.block-entity-groups", configuredGroup, "group matched no materials on this server", invalidEntries);
                continue;
            }
            blockMaterials.addAll(expanded);
        }
        return blockMaterials;
    }

    private BitSet resolveBlockStateIds(Set<Material> targetMaterials) {
        BitSet targetIds = new BitSet();
        if (targetMaterials.isEmpty()) {
            return targetIds;
        }

        int airRun = 0;
        int blockStateId = 0;
        while (true) {
            BlockData blockData = SpigotConversionUtil.toBukkitBlockData(WrappedBlockState.getByGlobalId(blockStateId));
            if (blockData == null) {
                Logger.warning("Stopping target-filter block state scan at invalid block state ID " + blockStateId, 5, PaperTargetFilterService.class);
                break;
            }
            if (blockData.getMaterial() == Material.AIR) {
                airRun++;
                if (airRun > 80000) {
                    break;
                }
            } else {
                airRun = 0;
                if (targetMaterials.contains(blockData.getMaterial())) {
                    targetIds.set(blockStateId);
                }
            }
            blockStateId++;
        }
        return targetIds;
    }

    private Set<String> resolveSafeBlockEntityTypeKeys(Set<Material> targetMaterials) {
        Map<String, Set<Material>> materialsByType = new HashMap<>();
        Registry.MATERIAL.stream()
                .filter(this::isTileStateMaterial)
                .forEach(material -> {
                    String typeKey = blockEntityTypeKeyFor(material);
                    if (typeKey != null) {
                        materialsByType.computeIfAbsent(typeKey, ignored -> EnumSet.noneOf(Material.class)).add(material);
                    }
                });

        Set<String> safeTypes = new TreeSet<>();
        for (Map.Entry<String, Set<Material>> entry : materialsByType.entrySet()) {
            if (targetMaterials.containsAll(entry.getValue())) {
                safeTypes.add(entry.getKey());
            }
        }
        return safeTypes;
    }

    private Set<Material> expandGroup(String group) {
        return switch (group) {
            case "shulker_boxes" -> materialsMatching(path -> path.equals("shulker_box") || path.endsWith("_shulker_box"));
            case "signs" -> materialsMatching(this::isSignPath);
            case "hanging_signs" -> materialsMatching(this::isHangingSignPath);
            case "banners" -> materialsMatching(path -> path.endsWith("_banner") && !path.endsWith("_wall_banner"));
            case "wall_banners" -> materialsMatching(path -> path.endsWith("_wall_banner"));
            case "beds" -> materialsMatching(path -> path.endsWith("_bed"));
            case "heads_and_skulls" -> materialsMatching(this::isHeadOrSkullPath);
            default -> null;
        };
    }

    private Set<Material> materialsMatching(PathMatcher matcher) {
        Set<Material> matches = EnumSet.noneOf(Material.class);
        Registry.MATERIAL.stream()
                .filter(this::isTileStateMaterial)
                .filter(material -> matcher.matches(material.getKey().getKey()))
                .forEach(matches::add);
        return matches;
    }

    private boolean isSignPath(String path) {
        return (path.endsWith("_sign") || path.endsWith("_wall_sign")) && !isHangingSignPath(path);
    }

    private boolean isHangingSignPath(String path) {
        return path.endsWith("_hanging_sign") || path.endsWith("_wall_hanging_sign");
    }

    private boolean isHeadOrSkullPath(String path) {
        return HEAD_OR_SKULL_PATHS.contains(path);
    }

    private boolean isTileStateMaterial(Material material) {
        if (material == null || material.isLegacy() || !material.isBlock() || material.isAir()) {
            return false;
        }
        if (material == Material.MOVING_PISTON) {
            return true;
        }
        try {
            return material.createBlockData().createBlockState() instanceof TileState;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private Material materialByKey(String key) {
        NamespacedKey namespacedKey = NamespacedKey.fromString(key);
        return namespacedKey == null ? null : Registry.MATERIAL.get(namespacedKey);
    }

    private String blockEntityTypeKeyFor(Material material) {
        String path = material.getKey().getKey();
        String typePath;
        if (DIRECT_BLOCK_ENTITY_TYPE_PATHS.contains(path)) {
            typePath = path;
        } else if (path.equals("soul_campfire")) {
            typePath = "campfire";
        } else if (path.equals("moving_piston")) {
            typePath = "piston";
        } else if (path.equals("shulker_box") || path.endsWith("_shulker_box")) {
            typePath = "shulker_box";
        } else if (isHangingSignPath(path)) {
            typePath = "hanging_sign";
        } else if (isSignPath(path)) {
            typePath = "sign";
        } else if (path.endsWith("_banner") || path.endsWith("_wall_banner")) {
            typePath = "banner";
        } else if (path.endsWith("_bed")) {
            typePath = "bed";
        } else if (isHeadOrSkullPath(path)) {
            typePath = "skull";
        } else {
            return null;
        }

        return MINECRAFT_NAMESPACE + typePath;
    }

    private String normalizeConfiguredKey(String configured, String path, List<String> invalidEntries) {
        if (configured == null || configured.trim().isEmpty()) {
            warnSkipped(path, String.valueOf(configured), "blank entry", invalidEntries);
            return null;
        }
        return normalizeKey(configured);
    }

    private static String normalizeKey(String key) {
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        if (!normalized.contains(":")) {
            normalized = MINECRAFT_NAMESPACE + normalized;
        }
        return normalized;
    }

    private void warnSkipped(String path, String value, String reason, List<String> invalidEntries) {
        String message = path + " skipped '" + value + "': " + reason;
        invalidEntries.add(message);
        Logger.warning(message, 3, PaperTargetFilterService.class);
    }

    private interface PathMatcher {
        boolean matches(String path);
    }

    private record ResolvedTargetFilter(
            boolean enabled,
            Set<String> entityKeys,
            Set<String> blockMaterialKeys,
            BitSet blockStateIds,
            Set<String> safeBlockEntityTypeKeys,
            List<String> invalidEntries
    ) {
        private static ResolvedTargetFilter disabled() {
            return new ResolvedTargetFilter(false, Set.of(), Set.of(), new BitSet(), Set.of(), List.of());
        }
    }

    public record DebugSnapshot(
            boolean enabled,
            int entityTypeCount,
            int blockMaterialCount,
            int blockStateCount,
            int safeBlockEntityTypeCount,
            List<String> invalidEntries
    ) {
    }
}
