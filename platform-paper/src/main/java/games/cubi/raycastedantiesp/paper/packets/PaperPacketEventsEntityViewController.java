/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2026 Cubicake.
 * This file is part of RaycastedAntiESP.
 * RaycastedAntiESP is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License v3.0 only, which can be accessed at https://www.gnu.org/licenses/agpl-3.0.html.
 * See README.md for warranty disclaimer and further information.
 */

package games.cubi.raycastedantiesp.paper.packets;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import games.cubi.logs.Logger;
import games.cubi.raycastedantiesp.core.entity.EntityBypassRegistry;
import games.cubi.raycastedantiesp.core.players.PlayerData;
import games.cubi.raycastedantiesp.core.players.PlayerRegistry;
import games.cubi.raycastedantiesp.core.players.WorldEpochGuard;
import games.cubi.raycastedantiesp.packetevents.target.PacketEventsTargetFilter;
import games.cubi.raycastedantiesp.packetevents.viewcontrollers.PacketEventsEntityViewController;
import games.cubi.raycastedantiesp.packetevents.viewcontrollers.PacketEventsRespawnStateInvalidator;
import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.IntSupplier;

public final class PaperPacketEventsEntityViewController extends PacketEventsEntityViewController implements AutoCloseable {
    private final ListenerRegistration<PacketListenerCommon> registration;

    public static PaperPacketEventsEntityViewController create(
            IntSupplier currentTickSupplier, PacketEventsTargetFilter targetFilter,
            Runnable markUnsafeCleanup) {
        Objects.requireNonNull(markUnsafeCleanup, "markUnsafeCleanup");
        return EntityControllerOwnership.construct(
                () -> new PaperPacketEventsEntityViewController(
                        currentTickSupplier, targetFilter, markUnsafeCleanup),
                markUnsafeCleanup
        );
    }

    private PaperPacketEventsEntityViewController(
            IntSupplier currentTickSupplier, PacketEventsTargetFilter targetFilter,
            Runnable markUnsafeCleanup) {
        super(currentTickSupplier, targetFilter);
        PacketListenerCommon listener = asAbstract(PacketListenerPriority.HIGHEST);
        registration = ListenerRegistration.register(
                listener,
                candidate -> PacketEvents.getAPI().getEventManager().registerListener(candidate),
                registered -> PacketEvents.getAPI().getEventManager().unregisterListener(registered),
                markUnsafeCleanup
        );
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        List<Runnable> afterSendTasks = event.getTasksAfterSend();
        int firstControllerTask = afterSendTasks.size();

        PacketEventsRespawnStateInvalidator.invalidateIfRespawn(event);
        super.onPacketSend(event);

        UUID playerUUID = event.getUser().getUUID();
        PlayerData playerData = playerUUID == null
                ? null
                : PlayerRegistry.getInstance().getPlayerData(playerUUID);
        if (playerData == null) {
            return;
        }
        int worldEpoch = playerData.acquireWorldEpoch();
        for (int index = firstControllerTask; index < afterSendTasks.size(); index++) {
            afterSendTasks.set(index, WorldEpochGuard.fence(
                    playerData,
                    worldEpoch,
                    afterSendTasks.get(index)
            ));
        }
    }

    @Override
    protected void sendEntityPassengerPacket(
            int vehicleID, IntArrayList passengers, PlayerData playerData) {
        if (!EntityBypassRegistry.isBypassed(vehicleID)) {
            super.sendEntityPassengerPacket(vehicleID, passengers, playerData);
            return;
        }
        writeBypassedVehiclePassengerState(vehicleID, passengers, playerData);
    }

    @SuppressWarnings("PMD.GuardLogStatement") // CubiLogging performs its own level filtering.
    private static void writeBypassedVehiclePassengerState(
            int vehicleID, IntArrayList passengers, PlayerData playerData) {
        try {
            Object channel = PacketEvents.getAPI().getProtocolManager().getChannel(playerData.getPlayerUUID());
            if (channel == null) {
                return;
            }
            var user = PacketEvents.getAPI().getProtocolManager().getUser(channel);
            if (user == null) {
                return;
            }
            user.writePacketSilently(new WrapperPlayServerSetPassengers(vehicleID, passengers.toIntArray()));
        } catch (RuntimeException exception) {
            Logger.error("Failed to send filtered passenger state for bypassed vehicle id=" + vehicleID
                    + " viewer=" + playerData.getPlayerUUID()
                    + ". The original relationship packet will remain suppressed.",
                    exception, 2, PaperPacketEventsEntityViewController.class);
        }
    }

    @Override
    public void close() {
        EntityControllerOwnership.close(this, registration::close);
    }

    void rollbackRegistration() {
        registration.close();
    }
}
