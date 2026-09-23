/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2026 Cubicake.
 * This file is part of RaycastedAntiESP.
 * RaycastedAntiESP is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License v3.0 only, which can be accessed at https://www.gnu.org/licenses/agpl-3.0.html.
 * See README.md for warranty disclaimer and further information.
 */

package games.cubi.raycastedantiesp.paper.packets;

final class EntityVisibilityPacketPolicy {
    private EntityVisibilityPacketPolicy() {
    }

    static boolean shouldSuppressClientEntityReference(
            boolean bypassed, boolean self, boolean tracked, boolean visible, boolean clientVisible) {
        return !bypassed && !self && (!tracked || !visible || !clientVisible);
    }

    static boolean shouldSuppressUnresolvedReference(boolean bypassed, boolean tracked) {
        return !bypassed && !tracked;
    }

    static int decodeDamageSourceEntityID(int packetValue) {
        return packetValue - 1;
    }

    static boolean damageSourceUsesEntityReferences(boolean sourcePositionPresent) {
        return !sourcePositionPresent;
    }
}
