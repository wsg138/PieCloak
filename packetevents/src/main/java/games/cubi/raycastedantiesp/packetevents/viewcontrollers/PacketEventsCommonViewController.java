/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2026 Cubicake.
 * This file is part of RaycastedAntiESP. RaycastedAntiESP is free software: you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License v3.0 only, which can be accessed at
 * https://www.gnu.org/licenses/agpl-3.0.html. See README.md for warranty disclaimer and further information.
 */

package games.cubi.raycastedantiesp.packetevents.viewcontrollers;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import games.cubi.locatables.api.Locatable;
import games.cubi.raycastedantiesp.core.players.PlayerData;

import java.util.Objects;
import java.util.UUID;
import java.util.function.IntSupplier;

public abstract class PacketEventsCommonViewController {
    private static volatile PacketEventsCommonViewController instance;
    private final IntSupplier currentTickSupplier;
    public final boolean v_1_21_5_orAbove = PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_21_5);

    protected PacketEventsCommonViewController(IntSupplier currentTick) {
        this.currentTickSupplier = currentTick;
    }

    public static void initialise(PacketEventsCommonViewController newInstance) {
        Objects.requireNonNull(newInstance, "newInstance");
        synchronized (PacketEventsCommonViewController.class) {
            if (instance != null) {
                throw new IllegalStateException("PacketEventsCommonViewController is already initialised");
            }
            instance = newInstance;
        }
    }

    @SuppressWarnings("PMD.NullAssignment") // Reset deliberately releases the singleton owned by the plugin lifecycle.
    public static void reset(PacketEventsCommonViewController expectedInstance) {
        synchronized (PacketEventsCommonViewController.class) {
            if (instance == expectedInstance) {
                instance = null;
            }
        }
    }

    public static PacketEventsCommonViewController get() {
        PacketEventsCommonViewController current = instance;
        if (current == null) {
            throw new IllegalStateException("PacketEventsCommonViewController has not been initialised.");
        }
        return current;
    }

    public static PacketEventsCommonViewController get(IntSupplier currentTick) {
        return get();
    }

    public abstract UUID resolveWorldUUID(User user);

    public abstract UUID resolveWorldUUID(String worldName);

    public UUID resolvePacketWorld(PlayerData playerData, User user) {
        String trackedWorldName = playerData.nettyData().getCurrentWorldName();
        if (trackedWorldName != null) {
            return resolveWorldUUID(trackedWorldName);
        }

        Locatable ownLocation = playerData.ownLocation();
        if (ownLocation != null && ownLocation.world() != null) {
            return ownLocation.world();
        }
        return resolveWorldUUID(user);
    }

    public void writeIfPresent(User viewer, PacketWrapper<?> packet) {
        if (viewer == null || packet == null) {
            return;
        }

        viewer.writePacketSilently(packet);
    }
}
