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
import games.cubi.raycastedantiesp.packetevents.target.PacketEventsTargetFilter;
import games.cubi.raycastedantiesp.packetevents.viewcontrollers.PacketEventsEntityViewController;

import java.util.Objects;
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
    public void close() {
        EntityControllerOwnership.close(this, registration::close);
    }

    void rollbackRegistration() {
        registration.close();
    }
}
