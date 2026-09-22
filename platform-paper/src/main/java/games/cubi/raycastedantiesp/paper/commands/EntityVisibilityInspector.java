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

        EntityView<?> view = managedView(playerData, entityID);
        if (view == null) {
            renderUnmanaged(sender, viewer, playerData, entityID);
            return;
        }

        NettyEntity<?> entity = (NettyEntity<?>) view.getEntity(entityID);
        if (entity == null) {
            sender.sendRichMessage("<red>The entity disappeared from the managed view while it was being inspected.");
            return;
        }
        renderManaged(sender, viewer, playerData, view, entity);
    }

    private static EntityView<?> managedView(PlayerData playerData, int entityID) {
        EntityView<?> entityView = playerData.entityView();
        if (entityView.exists(entityID)) {
            return entityView;
        }
        EntityView<?> playerView = playerData.playerView();
        return playerView.exists(entityID) ? playerView : null;
    }

    private static void renderUnmanaged(CommandSender sender, Player viewer, PlayerData playerData, int entityID) {
        sender.sendRichMessage("<gold>PieCloak inspect <gray>viewer=<white>" + viewer.getName()
                + " <gray>entityId=<white>" + entityID);
        sender.sendRichMessage("<gray>managed=<red>false</red> bypassed=<white>"
                + EntityBypassRegistry.isBypassed(entityID)
                + " <gray>relationshipSupport=<white>"
                + EntityBypassRegistry.isRelationshipSupportEntity(entityID)
                + " <gray>viewerBypass=<white>" + playerData.hasBypassPermission());
        sender.sendRichMessage("<gray>No managed state exists for this viewer. A bypassed entity is intentionally forwarded without anti-ESP tracking.");
    }

    private static void renderManaged(CommandSender sender, Player viewer, PlayerData playerData,
            EntityView<?> view, NettyEntity<?> entity) {
        RaycastConfig config = view.isPlayerView()
                ? ConfigManager.get().getPlayerConfig()
                : ConfigManager.get().getEntityConfig();
        Locatable viewerLocation = playerData.ownLocation();

        sender.sendRichMessage("<gold>PieCloak inspect <gray>viewer=<white>" + viewer.getName()
                + " <gray>entityId=<white>" + entity.entityID()
                + " <gray>type=<white>" + typeName(entity));
        sender.sendRichMessage("<gray>uuid=<white>" + entity.entityUUID()
                + " <gray>view=<white>" + viewName(view)
                + " <gray>worldEpoch=<white>" + playerData.acquireWorldEpoch());
        sender.sendRichMessage("<gray>position=<white>" + format(entity.x()) + "," + format(entity.y()) + "," + format(entity.z())
                + " <gray>distance=<white>" + distance(viewerLocation, entity)
                + " <gray>alwaysShow=<white>" + config.getAlwaysShowRadius()
                + " <gray>maxRadius=<white>" + config.getRaycastRadius());
        sender.sendRichMessage("<gray>engineVisible=<white>" + entity.visible()
                + " <gray>clientVisible=<white>" + entity.clientVisible()
                + " <gray>freshRaycast=<white>" + freshRaycast(playerData, viewerLocation, entity, view, config)
                + " <gray>glowing=<white>" + entity.glowing());
        sender.sendRichMessage("<gray>lastChecked=<white>" + entity.lastChecked()
                + " <gray>viewerBypass=<white>" + playerData.hasBypassPermission()
                + " <gray>bypassed=<white>" + EntityBypassRegistry.isBypassed(entity.entityID())
                + " <gray>relationshipSupport=<white>" + EntityBypassRegistry.isRelationshipSupportEntity(entity.entityID()));
        sender.sendRichMessage("<gray>vehicle=<white>" + entity.vehicleID()
                + " <gray>leashHolder=<white>" + entity.leashingEntity()
                + " <gray>passengers=<white>" + PrimitiveIntArrayList.toString(entity.passengerIDs()));
    }

    private static String viewName(EntityView<?> view) {
        return view.isPlayerView() ? "player" : "entity";
    }

    private static String freshRaycast(PlayerData playerData, Locatable viewerLocation, NettyEntity<?> entity,
            EntityView<?> view, RaycastConfig config) {
        if (!config.enabled()) {
            return "disabled";
        }
        if (viewerLocation == null || viewerLocation.world() == null) {
            return "n/a";
        }
        float yOffset = view.isPlayerView() ? 1.5f : entity.getYOffset();
        return Boolean.toString(RaycastUtil.raycast(
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
        ));
    }

    private static String distance(Locatable start, NettyEntity<?> end) {
        if (start == null || start.world() == null) {
            return "n/a";
        }
        double x = end.x() - start.x();
        double y = end.y() - start.y();
        double z = end.z() - start.z();
        return format(Math.sqrt(x * x + y * y + z * z));
    }

    private static String typeName(NettyEntity<?> entity) {
        EntityType type = EntityTypes.getById(
                PacketEvents.getAPI().getServerManager().getVersion().toClientVersion(), entity.entityType());
        return type == null ? "unknown(" + entity.entityType() + ")" : String.valueOf(type.getName());
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
