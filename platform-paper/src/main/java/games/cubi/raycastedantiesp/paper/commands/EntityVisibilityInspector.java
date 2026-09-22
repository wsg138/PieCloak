/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2025-2026 Cubicake and Contributors.
 * This file is part of PieCloak, a modified fork of RaycastedAntiESP.
 */

package games.cubi.raycastedantiesp.paper.commands;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import games.cubi.locatables.api.Locatable;
import games.cubi.raycastedantiesp.core.config.ConfigManager;
import games.cubi.raycastedantiesp.core.config.raycast.RaycastConfig;
import games.cubi.raycastedantiesp.core.entity.EntityBypassRegistry;
import games.cubi.raycastedantiesp.core.players.PlayerData;
import games.cubi.raycastedantiesp.core.players.PlayerRegistry;
import games.cubi.raycastedantiesp.core.raycast.RaycastUtil;
import games.cubi.raycastedantiesp.core.tracked.NettyEntity;
import games.cubi.raycastedantiesp.core.utils.PrimitiveIntArrayList;
import games.cubi.raycastedantiesp.core.view.EntityView;
import games.cubi.raycastedantiesp.paper.RaycastedAntiESP;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

final class EntityVisibilityInspector {
    private EntityVisibilityInspector() {
    }

    static void inspect(CommandSender sender, String viewerName, int entityID) {
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

        EntityView<?> view = findManagedView(playerData, entityID);
        boolean bypassed = EntityBypassRegistry.isBypassed(entityID);
        boolean relationshipSupport = EntityBypassRegistry.isRelationshipSupportEntity(entityID);
        if (view == null) {
            sender.sendRichMessage("<gold>PieCloak inspect <gray>viewer=<white>" + viewer.getName()
                    + " <gray>entityId=<white>" + entityID);
            sender.sendRichMessage("<gray>managed=<red>false</red> bypassed=<white>" + bypassed
                    + " <gray>relationshipSupport=<white>" + relationshipSupport
                    + " <gray>viewerBypass=<white>" + playerData.hasBypassPermission());
            sender.sendRichMessage("<gray>No managed state exists for this viewer. If bypassed=true, the entity is intentionally forwarded without anti-ESP tracking.");
            return;
        }

        NettyEntity<?> entity = (NettyEntity<?>) view.getEntity(entityID);
        if (entity == null) {
            sender.sendRichMessage("<red>The entity disappeared from the managed view while it was being inspected.");
            return;
        }

        Locatable viewerLocation = playerData.ownLocation();
        RaycastConfig config = view.isPlayerView()
                ? ConfigManager.get().getPlayerConfig()
                : ConfigManager.get().getEntityConfig();
        double distance = distance(viewerLocation, entity);
        Boolean freshRaycast = freshRaycast(playerData, viewerLocation, entity, view, config, distance);
        int currentTick = RaycastedAntiESP.getCurrentTick();

        sender.sendRichMessage("<gold>PieCloak inspect <gray>viewer=<white>" + viewer.getName()
                + " <gray>entityId=<white>" + entityID
                + " <gray>type=<white>" + typeName(entity));
        sender.sendRichMessage("<gray>uuid=<white>" + entity.entityUUID()
                + " <gray>view=<white>" + (view.isPlayerView() ? "player" : "entity")
                + " <gray>worldEpoch=<white>" + playerData.acquireWorldEpoch());
        sender.sendRichMessage("<gray>position=<white>" + format(entity.x()) + "," + format(entity.y()) + "," + format(entity.z())
                + " <gray>distance=<white>" + format(distance)
                + " <gray>alwaysShow=<white>" + config.getAlwaysShowRadius()
                + " <gray>maxRadius=<white>" + config.getRaycastRadius());
        sender.sendRichMessage("<gray>engineVisible=<white>" + entity.visible()
                + " <gray>clientVisible=<white>" + entity.clientVisible()
                + " <gray>freshRaycast=<white>" + formatNullable(freshRaycast)
                + " <gray>glowing=<white>" + entity.glowing());
        sender.sendRichMessage("<gray>lastChecked=<white>" + entity.lastChecked()
                + " <gray>ticksSinceCheck=<white>" + ticksSince(currentTick, entity.lastChecked())
                + " <gray>viewerBypass=<white>" + playerData.hasBypassPermission()
                + " <gray>bypassed=<white>" + bypassed
                + " <gray>relationshipSupport=<white>" + relationshipSupport);
        sender.sendRichMessage("<gray>vehicle=<white>" + entity.vehicleID()
                + " <gray>leashHolder=<white>" + entity.leashingEntity()
                + " <gray>passengers=<white>" + PrimitiveIntArrayList.toString(entity.passengerIDs()));
    }

    private static EntityView<?> findManagedView(PlayerData playerData, int entityID) {
        if (playerData.entityView().exists(entityID)) {
            return playerData.entityView();
        }
        if (playerData.playerView().exists(entityID)) {
            return playerData.playerView();
        }
        return null;
    }

    private static Boolean freshRaycast(PlayerData playerData, Locatable viewerLocation, NettyEntity<?> entity,
            EntityView<?> view, RaycastConfig config, double distance) {
        if (!config.enabled() || viewerLocation == null || viewerLocation.world() == null || !Double.isFinite(distance)) {
            return null;
        }
        float yOffset = view.isPlayerView() ? 1.5f : entity.getYOffset();
        return RaycastUtil.raycast(
                viewerLocation,
                entity,
                config.getMaxOccludingCount(),
                config.getAlwaysShowRadius(),
                config.getRaycastRadius(),
                false,
                playerData.blockView(),
                yOffset,
                1,
                null
        );
    }

    private static double distance(Locatable start, NettyEntity<?> end) {
        if (start == null || start.world() == null) {
            return Double.NaN;
        }
        double x = end.x() - start.x();
        double y = end.y() - start.y();
        double z = end.z() - start.z();
        return Math.sqrt(x * x + y * y + z * z);
    }

    private static String typeName(NettyEntity<?> entity) {
        EntityType type = EntityTypes.getById(
                PacketEvents.getAPI().getServerManager().getVersion().toClientVersion(), entity.entityType());
        return type == null ? "unknown(" + entity.entityType() + ")" : type.getName().toString();
    }

    private static String ticksSince(int currentTick, int lastChecked) {
        if (lastChecked == NettyEntity.NEVER_CHECKED) {
            return "never";
        }
        return Integer.toString(currentTick - lastChecked);
    }

    private static String formatNullable(Boolean value) {
        return value == null ? "n/a" : value.toString();
    }

    private static String format(double value) {
        if (!Double.isFinite(value)) {
            return "n/a";
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
