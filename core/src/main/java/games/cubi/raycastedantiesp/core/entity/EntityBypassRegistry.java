/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2026 Cubicake.
 * This file is part of RaycastedAntiESP.
 * RaycastedAntiESP is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License v3.0 only, which can be accessed at https://www.gnu.org/licenses/agpl-3.0.html.
 * See README.md for warranty disclaimer and further information.
 */

package games.cubi.raycastedantiesp.core.entity;

import games.cubi.utils.sets.CopyOnWriteMTIntSet;

/** Global registry of entity IDs which RaycastedAntiESP must ignore. */
public final class EntityBypassRegistry {
    private static volatile CopyOnWriteMTIntSet bypassedEntityIds = CopyOnWriteMTIntSet.get();
    private static volatile CopyOnWriteMTIntSet relationshipSupportEntityIds = CopyOnWriteMTIntSet.get();

    private EntityBypassRegistry() {
    }

    public static void addEntity(int entityID) {
        bypassedEntityIds.add(entityID);
    }

    public static void addRelationshipSupportEntity(int entityID) {
        relationshipSupportEntityIds.add(entityID);
    }

    public static boolean markEntityDespawned(int entityID) {
        boolean removedBypass = bypassedEntityIds.remove(entityID);
        boolean removedSupport = relationshipSupportEntityIds.remove(entityID);
        return removedBypass || removedSupport;
    }

    public static boolean isBypassed(int entityID) {
        return bypassedEntityIds.contains(entityID);
    }

    public static boolean isRelationshipSupportEntity(int entityID) {
        return relationshipSupportEntityIds.contains(entityID);
    }

    public static void reset() {
        synchronized (EntityBypassRegistry.class) {
            bypassedEntityIds = CopyOnWriteMTIntSet.get();
            relationshipSupportEntityIds = CopyOnWriteMTIntSet.get();
        }
    }
}
