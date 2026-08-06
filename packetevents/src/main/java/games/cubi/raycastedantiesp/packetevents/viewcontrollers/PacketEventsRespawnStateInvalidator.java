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

        WrapperPlayServerRespawn packet = new WrapperPlayServerRespawn(event);
        String worldName = packet.getWorldName().orElse(null);
        if (worldName == null) {
            Logger.error("Received respawn packet without a world name, uuid=" + playerUUID,
                    1, PacketEventsRespawnStateInvalidator.class);
            return false;
        }
        UUID worldUUID = PacketEventsCommonViewController.get().resolveWorldUUID(worldName);
        return ClientStateResetter.resetForRespawn(
                playerData,
                worldName,
                worldUUID,
                packet.getDimensionType().getMinY()
        );
    }
}
