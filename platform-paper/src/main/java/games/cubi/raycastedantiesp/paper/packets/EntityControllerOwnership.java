/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2026 Cubicake.
 * This file is part of RaycastedAntiESP.
 * RaycastedAntiESP is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License v3.0 only, which can be accessed at https://www.gnu.org/licenses/agpl-3.0.html.
 * See README.md for warranty disclaimer and further information.
 */

package games.cubi.raycastedantiesp.paper.packets;

import games.cubi.raycastedantiesp.core.view.controller.PacketEntityViewController;
import games.cubi.raycastedantiesp.packetevents.viewcontrollers.PacketEventsEntityViewController;

/**
 * Binds the current controller implementation's two singleton slots to one lifecycle.
 *
 * <p>The upstream fields do not expose release APIs, so the fixed-name reflective binding is kept
 * isolated at the Paper adapter boundary. Release remains explicit and ownership-aware for each
 * field, and a later upstream change fails here instead of silently retaining stale state.</p>
 */
final class EntityControllerOwnership {
    private static final ControllerOwnership OWNERSHIP = new ControllerOwnership(
            PacketEntityViewController.class,
            ControllerOwnership.reflectiveStaticSlot(PacketEntityViewController.class, "SELF"),
            ControllerOwnership.reflectiveStaticSlot(PacketEventsEntityViewController.class, "SELF")
    );

    private EntityControllerOwnership() {
    }

    static PaperPacketEventsEntityViewController construct(
            ControllerOwnership.Factory<PaperPacketEventsEntityViewController> factory,
            Runnable markUnsafeCleanup) {
        return OWNERSHIP.construct(
                factory,
                controller -> {
                    PacketEventsEntityViewController published = PacketEventsEntityViewController.get();
                    if (published != controller) {
                        throw new IllegalStateException("PacketEvents published a different entity controller owner");
                    }
                },
                PaperPacketEventsEntityViewController::rollbackRegistration,
                markUnsafeCleanup
        );
    }

    static void close(PaperPacketEventsEntityViewController owner, ControllerOwnership.Cleanup listenerCleanup) {
        OWNERSHIP.closeOwned(owner, listenerCleanup);
    }

    static void releaseCoreOwnership(PaperPacketEventsEntityViewController owner) {
        OWNERSHIP.releasePrimaryOwned(owner);
    }

    static void releasePacketEventsOwnership(PaperPacketEventsEntityViewController owner) {
        OWNERSHIP.releaseSecondaryOwned(owner);
    }

    static void verifyBindings() {
        OWNERSHIP.verifyConsistentOrEmpty();
    }
}
