/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2026 Cubicake.
 * This file is part of RaycastedAntiESP. RaycastedAntiESP is free software: you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License v3.0 only, which can be accessed at
 * https://www.gnu.org/licenses/agpl-3.0.html. See README.md for warranty disclaimer and further information.
 */

package games.cubi.raycastedantiesp.core.players;

import games.cubi.raycastedantiesp.core.tracked.NettyEntity;

import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class PlayerRegistry {

    @FunctionalInterface
    public interface SelfEntityCreator {
        NettyEntity<?> createSelfEntity(PlayerData playerData, int selfEntityID, UUID playerUUID);
    }

    private static final PlayerRegistry instance = new PlayerRegistry();

    private PlayerRegistry() {}

    public static PlayerRegistry get() {
        return instance;
    }

    private final ConcurrentHashMap<UUID, PlayerData> playerDataMap = new ConcurrentHashMap<>();
    private final Collection<PlayerData> unmodifiablePlayerData = Collections.unmodifiableCollection(playerDataMap.values());
    private volatile PlayerData[] playerDataArray = new PlayerData[0];

    /** Forcefully registers a player and returns the new PlayerData, even if they were already registered.**/
    public PlayerData registerAndGetPlayer(UUID playerUUID, int joinTick, int selfEntityID, SelfEntityCreator selfEntityCreator) {
        synchronized (this) {
            PlayerData newData = new PlayerData(playerUUID, false, joinTick, selfEntityID, selfEntityCreator);
            PlayerData old = playerDataMap.put(playerUUID, newData);
            if (old != null) old.markDisconnected();
            updatePlayerDataArray();
            return newData;
        }
    }

    public void unregisterPlayer(UUID playerUUID) {
        synchronized (this) {
            PlayerData unregisteredPlayer = playerDataMap.remove(playerUUID);
            if (unregisteredPlayer == null) {
                return;
            }
            unregisteredPlayer.markDisconnected();
            updatePlayerDataArray();
        }
    }

    /**
     * Removes a player only when the registry still owns the expected generation.
     * This prevents cleanup for an obsolete PlayerData instance from unregistering a later login.
     */
    public boolean unregisterPlayer(UUID playerUUID, PlayerData expectedPlayerData) {
        synchronized (this) {
            if (expectedPlayerData == null || !playerDataMap.remove(playerUUID, expectedPlayerData)) {
                return false;
            }
            expectedPlayerData.markDisconnected();
            updatePlayerDataArray();
            return true;
        }
    }

    public void clear() {
        synchronized (this) {
            playerDataMap.values().forEach(PlayerData::markDisconnected);
            playerDataMap.clear();
            updatePlayerDataArray();
        }
    }

    public PlayerData getPlayerData(UUID playerUUID) {
        return playerDataMap.get(playerUUID);
    }

    public boolean isPlayerRegistered(UUID playerUUID) {
        return playerDataMap.containsKey(playerUUID);
    }

    /**
     * @return Live, unmodifiable view of all PlayerData instances.
     * **/
    public Collection<PlayerData> getAllPlayerData() {
        return unmodifiablePlayerData;
    }

    private void updatePlayerDataArray() {
        playerDataArray = playerDataMap.values().toArray(PlayerData[]::new);
    }

    /**
     * Do not mutate this array, all changes must go through {@link #registerAndGetPlayer(UUID, int, int, SelfEntityCreator)} /
     * {@link #unregisterPlayer(UUID)}.
     * <p></p>
     * Changes to this array will be lost on next player join/leave, and will not apply everywhere.
     */
    public PlayerData[] getPlayerDataArray() {
        return playerDataArray;
    }

    public void forEachPlayer(Consumer<PlayerData> consumer) {
        PlayerData[] data = getPlayerDataArray();
        for (PlayerData player : data) {
            consumer.accept(player);
        }
    }

    public int getPlayerCount() {
        return playerDataMap.size();
    }
}
