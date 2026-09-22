/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2025-2026 Cubicake and Contributors.
 * This file is part of PieCloak, a modified fork of RaycastedAntiESP.
 */

package games.cubi.raycastedantiesp.paper.commands;

import games.cubi.locatables.api.Locatable;
import games.cubi.locatables.implementations.ImmutableBlockSpatialImpl;
import games.cubi.raycastedantiesp.core.config.ConfigManager;
import games.cubi.raycastedantiesp.core.config.raycast.TileEntityConfig;
import games.cubi.raycastedantiesp.core.players.PlayerData;
import games.cubi.raycastedantiesp.core.players.PlayerRegistry;
import games.cubi.raycastedantiesp.core.raycast.RaycastUtil;
import games.cubi.raycastedantiesp.core.tracked.TrackedTileEntity;
import games.cubi.raycastedantiesp.core.view.BlockView;
import games.cubi.raycastedantiesp.paper.RaycastedAntiESP;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.UUID;

final class BlockVisibilityInspector {
    private BlockVisibilityInspector() {
    }

    static void inspect(CommandSender sender, String viewerName, int x, int y, int z) {
        Player viewer = Bukkit.getPlayerExact(viewerName);
        if (viewer == null) {
            sender.sendRichMessage("<red>Viewer is not online: <white>" + viewerName);
            return;
        }

        PlayerData playerData = PlayerRegistry.getInstance().getPlayerData(viewer.getUniqueId());
        if (playerData == null) {
            sender.sendRichMessage("<red>PieCloak has no packet state for <white>" + viewer.getName());
            return;
        }

        Locatable viewerLocation = playerData.ownLocation();
        UUID world = viewerLocation == null ? null : viewerLocation.world();
        if (world == null) {
            sender.sendRichMessage("<red>PieCloak does not have a stable world/location for this viewer yet.");
            return;
        }

        ImmutableBlockSpatialImpl position = new ImmutableBlockSpatialImpl(x, y, z);
        BlockView blockView = playerData.blockView();
        BlockView.BlockEntityStatus status = blockView.getBlockEntityStatus(world, position);
        TrackedTileEntity<?> tile = blockView.getTrackedTileEntity(world, position);
        TileEntityConfig config = ConfigManager.get().getTileEntityConfig();
        double distance = distance(viewerLocation, position);
        Boolean freshRaycast = freshRaycast(playerData, viewerLocation, position, config);

        sender.sendRichMessage("<gold>PieCloak inspectblock <gray>viewer=<white>" + viewer.getName()
                + " <gray>position=<white>" + x + "," + y + "," + z
                + " <gray>world=<white>" + world);
        sender.sendRichMessage("<gray>classification=<white>" + status
                + " <gray>tracked=<white>" + (tile != null)
                + " <gray>viewerBypass=<white>" + playerData.hasBypassPermission()
                + " <gray>worldEpoch=<white>" + playerData.acquireWorldEpoch());
        sender.sendRichMessage("<gray>distance=<white>" + format(distance)
                + " <gray>alwaysShow=<white>" + config.getAlwaysShowRadius()
                + " <gray>maxRadius=<white>" + config.getRaycastRadius()
                + " <gray>freshRaycast=<white>" + formatNullable(freshRaycast));

        if (tile == null) {
            sender.sendRichMessage("<gray>No tracked tile state exists at this position for this viewer.");
            return;
        }

        int currentTick = RaycastedAntiESP.getCurrentTick();
        sender.sendRichMessage("<gray>blockStateId=<white>" + tile.blockID()
                + " <gray>engineVisible=<white>" + tile.visible()
                + " <gray>lastChecked=<white>" + tile.lastChecked()
                + " <gray>ticksSinceCheck=<white>" + ticksSince(currentTick, tile.lastChecked()));
    }

    private static Boolean freshRaycast(PlayerData playerData, Locatable viewerLocation,
            ImmutableBlockSpatialImpl position, TileEntityConfig config) {
        if (!config.enabled()) {
            return null;
        }
        return RaycastUtil.raycast(
                viewerLocation,
                position,
                config.getMaxOccludingCount() + 1,
                config.getAlwaysShowRadius(),
                config.getRaycastRadius(),
                false,
                playerData.blockView(),
                1,
                null
        );
    }

    private static double distance(Locatable start, ImmutableBlockSpatialImpl end) {
        double dx = end.blockX() + 0.5 - start.x();
        double dy = end.blockY() + 0.5 - start.y();
        double dz = end.blockZ() + 0.5 - start.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static String ticksSince(int currentTick, int lastChecked) {
        if (lastChecked == TrackedTileEntity.NEVER_CHECKED) {
            return "never";
        }
        return Integer.toString(currentTick - lastChecked);
    }

    private static String formatNullable(Boolean value) {
        return value == null ? "n/a" : value.toString();
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
