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

        RespawnMetadata parsed = parseMetadata(event, playerData, playerUUID);
        RespawnMetadata resolved = resolveWorld(event, parsed, playerUUID);
        return ClientStateResetter.resetForRespawn(
                playerData,
                resolved.worldName(),
                resolved.worldUUID(),
                resolved.minWorldHeight()
        );
    }

    @SuppressWarnings("PMD.GuardLogStatement") // CubiLogging applies its configured level filter internally.
    private static RespawnMetadata parseMetadata(
            PacketSendEvent event, PlayerData playerData, UUID playerUUID) {
        try {
            WrapperPlayServerRespawn packet = new WrapperPlayServerRespawn(event);
            return new RespawnMetadata(
                    packet.getWorldName().orElse(null),
                    null,
                    packet.getDimensionType().getMinY()
            );
        } catch (RuntimeException exception) {
            Logger.error("Could not parse respawn world metadata; invalidating with unresolved world state. uuid="
                    + playerUUID, exception, 1, PacketEventsRespawnStateInvalidator.class);
            return new RespawnMetadata(null, null, currentMinWorldHeight(playerData));
        }
    }

    @SuppressWarnings("PMD.GuardLogStatement") // CubiLogging applies its configured level filter internally.
    private static RespawnMetadata resolveWorld(
            PacketSendEvent event, RespawnMetadata parsed, UUID playerUUID) {
        String worldName = parsed.worldName();
        if (worldName == null) {
            Logger.error("Received respawn packet without a world name; falling back to the viewer world. uuid="
                    + playerUUID, 1, PacketEventsRespawnStateInvalidator.class);
            return parsed.withResolvedWorld(null, resolveViewerWorld(event, playerUUID));
        }

        UUID namedWorld = resolveNamedWorld(worldName, playerUUID);
        if (namedWorld != null) {
            return parsed.withResolvedWorld(worldName, namedWorld);
        }

        Logger.error("Respawn world name did not resolve; falling back to the viewer world. uuid="
                + playerUUID + " worldName=" + worldName,
                1, PacketEventsRespawnStateInvalidator.class);
        return parsed.withResolvedWorld(null, resolveViewerWorld(event, playerUUID));
    }

    @SuppressWarnings("PMD.GuardLogStatement") // CubiLogging applies its configured level filter internally.
    private static UUID resolveNamedWorld(String worldName, UUID playerUUID) {
        try {
            return PacketEventsCommonViewController.get().resolveWorldUUID(worldName);
        } catch (RuntimeException exception) {
            Logger.error("Could not resolve respawn world name; falling back to the viewer world. uuid="
                    + playerUUID + " worldName=" + worldName,
                    exception, 1, PacketEventsRespawnStateInvalidator.class);
            return null;
        }
    }

    @SuppressWarnings("PMD.GuardLogStatement") // CubiLogging applies its configured level filter internally.
    private static UUID resolveViewerWorld(PacketSendEvent event, UUID playerUUID) {
        try {
            return PacketEventsCommonViewController.get().resolveWorldUUID(event.getUser());
        } catch (RuntimeException exception) {
            Logger.error("Could not resolve the viewer world for respawn invalidation. uuid="
                    + playerUUID, exception, 1, PacketEventsRespawnStateInvalidator.class);
            return null;
        }
    }

    private static int currentMinWorldHeight(PlayerData playerData) {
        return playerData.nettyData().getCurrentWorldName() == null
                ? DEFAULT_MIN_WORLD_HEIGHT
                : playerData.nettyData().getCurrentWorldMinHeight();
    }

    private record RespawnMetadata(String worldName, UUID worldUUID, int minWorldHeight) {
        private RespawnMetadata withResolvedWorld(String resolvedWorldName, UUID resolvedWorldUUID) {
            return new RespawnMetadata(resolvedWorldName, resolvedWorldUUID, minWorldHeight);
        }
    }
}
