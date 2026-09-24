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
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDamageEvent;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntitySoundEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import games.cubi.logs.Logger;
import games.cubi.raycastedantiesp.core.entity.EntityBypassRegistry;
import games.cubi.raycastedantiesp.core.players.PlayerData;
import games.cubi.raycastedantiesp.core.players.PlayerRegistry;
import games.cubi.raycastedantiesp.core.policy.VisibilityExemptionPolicy;
import games.cubi.raycastedantiesp.core.tracked.NettyEntity;
import games.cubi.raycastedantiesp.packetevents.target.PacketEventsTargetFilter;
import games.cubi.raycastedantiesp.packetevents.viewcontrollers.PacketEventsEntityViewController;
import games.cubi.raycastedantiesp.packetevents.viewcontrollers.PacketEventsRespawnStateInvalidator;
import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.IntSupplier;

import static games.cubi.raycastedantiesp.core.tracked.NettyEntity.NO_LEASHER;

public final class PaperPacketEventsEntityViewController extends PacketEventsEntityViewController implements AutoCloseable {
    private final ListenerRegistration<PacketListenerCommon> registration;

    public static PaperPacketEventsEntityViewController create(
            IntSupplier currentTickSupplier, PacketEventsTargetFilter targetFilter,
            VisibilityExemptionPolicy visibilityExemptionPolicy, Runnable markUnsafeCleanup) {
        Objects.requireNonNull(markUnsafeCleanup, "markUnsafeCleanup");
        return EntityControllerOwnership.construct(
                () -> new PaperPacketEventsEntityViewController(
                        currentTickSupplier, targetFilter, visibilityExemptionPolicy, markUnsafeCleanup),
                markUnsafeCleanup
        );
    }

    private PaperPacketEventsEntityViewController(
            IntSupplier currentTickSupplier, PacketEventsTargetFilter targetFilter,
            VisibilityExemptionPolicy visibilityExemptionPolicy, Runnable markUnsafeCleanup) {
        super(currentTickSupplier, targetFilter, visibilityExemptionPolicy);
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
        suppressHiddenEntitySound(event, playerData);
        suppressHiddenDamageEvent(event, playerData);
        AfterSendVisibilityRepair.wrapNewTasks(
                afterSendTasks,
                firstControllerTask,
                playerData,
                playerData.acquireWorldEpoch(),
                event.getUser()::flushPackets);
    }

    private void suppressHiddenEntitySound(PacketSendEvent event, PlayerData playerData) {
        if (event.getPacketType() != PacketType.Play.Server.ENTITY_SOUND_EFFECT) {
            return;
        }
        int entityID = new WrapperPlayServerEntitySoundEffect(event).getEntityId();
        if (shouldSuppressClientEntityReference(entityID, playerData)) {
            event.setCancelled(true);
        }
    }

    private void suppressHiddenDamageEvent(PacketSendEvent event, PlayerData playerData) {
        if (event.getPacketType() != PacketType.Play.Server.DAMAGE_EVENT) {
            return;
        }
        WrapperPlayServerDamageEvent packet = new WrapperPlayServerDamageEvent(event);
        if (shouldSuppressClientEntityReference(packet.getEntityId(), playerData)) {
            event.setCancelled(true);
            return;
        }
        boolean sourcePositionPresent = packet.getSourcePosition() != null;
        if (!EntityVisibilityPacketPolicy.damageSourceUsesEntityReferences(sourcePositionPresent)) {
            return;
        }
        int causeEntityID = EntityVisibilityPacketPolicy.decodeDamageSourceEntityID(packet.getSourceCauseId());
        int directEntityID = EntityVisibilityPacketPolicy.decodeDamageSourceEntityID(packet.getSourceDirectId());
        if (shouldSuppressClientEntityReference(causeEntityID, playerData)
                || shouldSuppressClientEntityReference(directEntityID, playerData)) {
            event.setCancelled(true);
        }
    }

    private static boolean shouldSuppressClientEntityReference(int entityID, PlayerData playerData) {
        if (entityID < 0) {
            return false;
        }
        boolean targetBypassed = EntityBypassRegistry.isBypassed(entityID);
        boolean self = playerData.nettyData().isSelfEntityID(entityID);
        NettyEntity<?> entity = self ? null : playerData.entityFromID(entityID);
        boolean tracked = self || entity != null;
        boolean visible = self || entity != null && entity.visible();
        boolean clientVisible = self || entity != null && entity.clientVisible();
        return EntityVisibilityPacketPolicy.shouldSuppressClientEntityReference(
                playerData.hasBypassPermission(), targetBypassed, self, tracked, visible, clientVisible);
    }

    @Override
    protected boolean handleEntityPassengers(
            int vehicleID, int[] passengers, PlayerData playerData, int currentTick) {
        boolean unresolvedVehicle = hasUnresolvedClientReference(vehicleID, playerData);
        boolean unresolvedPassenger = hasUnresolvedPassengerReference(passengers, playerData);
        boolean cancelled = super.handleEntityPassengers(vehicleID, passengers, playerData, currentTick);
        if (unresolvedVehicle) {
            return true;
        }
        if (unresolvedPassenger && !cancelled) {
            sendEntityPassengerPacket(
                    vehicleID,
                    collectClientVisiblePassengers(passengers, playerData),
                    playerData
            );
            return true;
        }
        return cancelled;
    }

    @Override
    protected boolean handleLeashEntity(
            int leashedEntityID, int holderEntityID, PlayerData playerData, int currentTick) {
        boolean unresolvedReference = hasUnresolvedClientReference(leashedEntityID, playerData)
                || holderEntityID != NO_LEASHER && hasUnresolvedClientReference(holderEntityID, playerData);
        boolean cancelled = super.handleLeashEntity(leashedEntityID, holderEntityID, playerData, currentTick);
        return cancelled || unresolvedReference;
    }

    private static boolean hasUnresolvedPassengerReference(int[] passengers, PlayerData playerData) {
        if (passengers == null) {
            return false;
        }
        for (int passengerID : passengers) {
            if (hasUnresolvedClientReference(passengerID, playerData)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasUnresolvedClientReference(int entityID, PlayerData playerData) {
        boolean bypassed = EntityBypassRegistry.isBypassed(entityID);
        boolean tracked = playerData.nettyData().isSelfEntityID(entityID) || playerData.entityFromID(entityID) != null;
        return EntityVisibilityPacketPolicy.shouldSuppressUnresolvedReference(bypassed, tracked);
    }

    @Override
    protected void sendEntityPassengerPacket(
            int vehicleID, IntArrayList passengers, PlayerData playerData) {
        IntArrayList clientVisiblePassengers = collectClientVisiblePassengers(passengers.toIntArray(), playerData);
        if (!EntityBypassRegistry.isBypassed(vehicleID)) {
            super.sendEntityPassengerPacket(vehicleID, clientVisiblePassengers, playerData);
            return;
        }
        writeBypassedVehiclePassengerState(vehicleID, clientVisiblePassengers, playerData);
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
