package games.cubi.raycastedantiesp.packetevents.viewcontrollers;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerRespawn;
import games.cubi.logs.Logger;
import games.cubi.raycastedantiesp.core.players.ClientStateResetter;
import games.cubi.raycastedantiesp.core.players.PlayerData;
import games.cubi.raycastedantiesp.core.players.PlayerRegistry;

import java.util.UUID;

/** Shared managed/bypass respawn invalidation performed before ordinary packet handling. */
public final class PacketEventsRespawnStateInvalidator {
    private static final int DEFAULT_MIN_WORLD_HEIGHT = -64;

    private PacketEventsRespawnStateInvalidator() {}

    public static boolean isRespawnPacket(Object packetType) {
        return packetType == PacketType.Play.Server.RESPAWN;
    }

    public static boolean invalidateIfRespawn(PacketSendEvent event) {
        if (!isRespawnPacket(event.getPacketType())) {
            return false;
        }

        UUID playerUUID = event.getUser().getUUID();
        if (playerUUID == null) {
            return false;
        }
        PlayerData playerData = PlayerRegistry.getInstance().getPlayerData(playerUUID);
        if (playerData == null) {
            return false;
        }

        String worldName = null;
        int minWorldHeight = currentMinWorldHeight(playerData);
        try {
            WrapperPlayServerRespawn packet = new WrapperPlayServerRespawn(event);
            worldName = packet.getWorldName().orElse(null);
            minWorldHeight = packet.getDimensionType().getMinY();
        } catch (RuntimeException exception) {
            Logger.error("Could not parse respawn world metadata; invalidating with unresolved world state. uuid="
                    + playerUUID, exception, 1, PacketEventsRespawnStateInvalidator.class);
        }

        UUID worldUUID = null;
        if (worldName != null) {
            try {
                worldUUID = PacketEventsCommonViewController.get().resolveWorldUUID(worldName);
            } catch (RuntimeException exception) {
                Logger.error("Could not resolve respawn world name; falling back to the viewer world. uuid="
                        + playerUUID + " worldName=" + worldName,
                        exception, 1, PacketEventsRespawnStateInvalidator.class);
            }
            if (worldUUID == null) {
                Logger.error("Respawn world name did not resolve; falling back to the viewer world. uuid="
                        + playerUUID + " worldName=" + worldName,
                        1, PacketEventsRespawnStateInvalidator.class);
                worldName = null;
            }
        } else {
            Logger.error("Received respawn packet without a world name; falling back to the viewer world. uuid="
                    + playerUUID, 1, PacketEventsRespawnStateInvalidator.class);
        }

        if (worldUUID == null) {
            try {
                worldUUID = PacketEventsCommonViewController.get().resolveWorldUUID(event.getUser());
            } catch (RuntimeException exception) {
                Logger.error("Could not resolve the viewer world for respawn invalidation. uuid="
                        + playerUUID, exception, 1, PacketEventsRespawnStateInvalidator.class);
            }
        }

        return ClientStateResetter.resetForRespawn(
                playerData,
                worldName,
                worldUUID,
                minWorldHeight
        );
    }

    private static int currentMinWorldHeight(PlayerData playerData) {
        return playerData.nettyData().getCurrentWorldName() == null
                ? DEFAULT_MIN_WORLD_HEIGHT
                : playerData.nettyData().getCurrentWorldMinHeight();
    }
}
