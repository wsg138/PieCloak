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
import games.cubi.raycastedantiesp.core.entity.EntityBypassRegistry;
import games.cubi.raycastedantiesp.core.players.PlayerData;
import games.cubi.raycastedantiesp.core.players.PlayerRegistry;
import games.cubi.raycastedantiesp.core.players.WorldEpochGuard;
import games.cubi.raycastedantiesp.core.tracked.NettyEntity;
import games.cubi.raycastedantiesp.core.utils.PrimitiveIntArrayList;
import games.cubi.raycastedantiesp.packetevents.target.PacketEventsTargetFilter;
import games.cubi.raycastedantiesp.packetevents.viewcontrollers.PacketEventsEntityViewController;
import games.cubi.raycastedantiesp.packetevents.viewcontrollers.PacketEventsRespawnStateInvalidator;
import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.IntSupplier;

import static games.cubi.raycastedantiesp.core.tracked.NettyEntity.NO_LEASHER;
import static games.cubi.raycastedantiesp.core.tracked.NettyEntity.NO_VEHICLE;

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

    /**
     * A bypassed vehicle is already known to the client, but that must not make a managed passenger
     * visible. Keep the authoritative relationship in NettyData and rewrite the client relationship
     * to contain only passengers which are independently client-visible.
     */
    @Override
    protected boolean handleEntityPassengers(
            int entityID, int[] passengers, PlayerData playerData, int currentTick) {
        if (!EntityBypassRegistry.isBypassed(entityID)) {
            return super.handleEntityPassengers(entityID, passengers, playerData, currentTick);
        }

        int[] previousPassengers = playerData.nettyData().getUnresolvedPassengers(entityID);
        playerData.nettyData().setUnresolvedPassengers(entityID, passengers);
        clearStaleBypassedPassengerReferences(entityID, previousPassengers, passengers, playerData);
        updateKnownBypassedVehiclePassengers(entityID, passengers, playerData);

        IntArrayList visiblePassengers = collectClientVisiblePassengers(passengers, playerData);
        int passengerCount = passengers == null ? 0 : passengers.length;
        if (visiblePassengers.size() == passengerCount) {
            return false;
        }

        writeBypassedVehiclePassengerState(entityID, visiblePassengers, playerData);
        return true;
    }

    private static void updateKnownBypassedVehiclePassengers(
            int vehicleID, int[] passengers, PlayerData playerData) {
        if (passengers == null) {
            return;
        }
        for (int passengerID : passengers) {
            NettyEntity<?> passenger = playerData.entityFromID(passengerID);
            if (passenger != null) {
                passenger.setVehicleID(vehicleID);
            }
        }
    }

    /**
     * Preserve the core reconciliation behavior except for the one unsafe rule which force-shows a
     * passenger merely because its already-known vehicle is bypassed.
     */
    @Override
    protected void reconcileUnresolvedPassengers(NettyEntity<?> insertedEntity, PlayerData playerData) {
        int unresolvedVehicleID = playerData.nettyData().getUnresolvedVehicleForPassenger(insertedEntity.entityID());
        if (unresolvedVehicleID == NO_VEHICLE || !EntityBypassRegistry.isBypassed(unresolvedVehicleID)) {
            super.reconcileUnresolvedPassengers(insertedEntity, playerData);
            return;
        }

        int[] pendingPassengers = playerData.nettyData().getUnresolvedPassengers(insertedEntity.entityID());
        if (!PrimitiveIntArrayList.isEmpty(pendingPassengers)) {
            // The inserted entity itself is tracked, so the normal tracked-vehicle handler is safe.
            super.handleEntityPassengers(
                    insertedEntity.entityID(), pendingPassengers, playerData, insertedEntity.lastChecked());
        }
        insertedEntity.setVehicleID(unresolvedVehicleID);
    }

    /**
     * A bypassed entity can spawn after its passenger relationship. Resolve that relationship without
     * turning the bypass into a visibility override for managed passengers.
     */
    @Override
    protected void handleBypassedEntitySpawn(int entityID, PlayerData playerData, int currentTick) {
        playerData.nettyData().clearPendingPostSpawnTasksForEntity(entityID);
        reconcilePendingBypassedPassengers(entityID, playerData);
        reconcileBypassedLeashHolder(entityID, playerData);
        reconcileBypassedLeashedEntities(entityID, playerData);
    }

    private static void reconcilePendingBypassedPassengers(int entityID, PlayerData playerData) {
        int[] pendingPassengers = playerData.nettyData().getUnresolvedPassengers(entityID);
        if (!PrimitiveIntArrayList.isEmpty(pendingPassengers)) {
            updateKnownBypassedVehiclePassengers(entityID, pendingPassengers, playerData);
        }
    }

    private static void reconcileBypassedLeashHolder(int entityID, PlayerData playerData) {
        int holderEntityID = playerData.nettyData().getUnresolvedHolderForLeashedEntity(entityID);
        if (holderEntityID == NO_LEASHER) {
            return;
        }
        NettyEntity<?> holder = playerData.entityFromID(holderEntityID);
        if (holder != null) {
            holder.addLeashedEntity(entityID);
        }
    }

    private static void reconcileBypassedLeashedEntities(int entityID, PlayerData playerData) {
        int[] pendingLeashedEntityIDs = playerData.nettyData().getUnresolvedLeashes(entityID);
        if (PrimitiveIntArrayList.isEmpty(pendingLeashedEntityIDs)) {
            return;
        }
        for (int leashedEntityID : pendingLeashedEntityIDs) {
            NettyEntity<?> leashedEntity = playerData.entityFromID(leashedEntityID);
            if (leashedEntity != null) {
                leashedEntity.setLeashingEntity(entityID);
            }
        }
    }

    private static void clearStaleBypassedPassengerReferences(
            int vehicleID, int[] previousPassengers, int[] newPassengers, PlayerData playerData) {
        if (PrimitiveIntArrayList.isEmpty(previousPassengers)) {
            return;
        }
        for (int previousPassengerID : previousPassengers) {
            if (PrimitiveIntArrayList.contains(newPassengers, previousPassengerID)) {
                continue;
            }
            NettyEntity<?> previousPassenger = playerData.entityFromID(previousPassengerID);
            if (previousPassenger != null && previousPassenger.vehicleID() == vehicleID) {
                previousPassenger.setVehicleEntity(null);
            }
        }
    }

    private static void writeBypassedVehiclePassengerState(
            int vehicleID, IntArrayList passengers, PlayerData playerData) {
        Object channel = PacketEvents.getAPI().getProtocolManager().getChannel(playerData.getPlayerUUID());
        if (channel == null) {
            return;
        }
        var user = PacketEvents.getAPI().getProtocolManager().getUser(channel);
        if (user == null) {
            return;
        }
        user.writePacketSilently(new WrapperPlayServerSetPassengers(vehicleID, passengers.toIntArray()));
    }

    @Override
    public void close() {
        EntityControllerOwnership.close(this, registration::close);
    }

    void rollbackRegistration() {
        registration.close();
    }
}
