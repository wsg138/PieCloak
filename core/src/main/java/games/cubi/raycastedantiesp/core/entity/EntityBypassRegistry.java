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
    private static volatile BypassScope fancyNpcScope = new BypassScope();
    private static volatile BypassScope fancyHologramScope = new BypassScope();

    private EntityBypassRegistry() {
    }

    /**
     * A lifetime-scoped bypass reason for one optional integration instance.
     * Closing the scope atomically prevents future asynchronous callbacks from
     * re-registering stale IDs after that integration has disabled.
     */
    public static final class BypassScope implements AutoCloseable {
        private final CopyOnWriteMTIntSet entityIds = CopyOnWriteMTIntSet.get();
        private volatile boolean closed;

        private BypassScope() {
        }

        public void addEntity(int entityID) {
            synchronized (this) {
                if (!closed) {
                    entityIds.add(entityID);
                }
            }
        }

        public boolean removeEntity(int entityID) {
            synchronized (this) {
                return !closed && entityIds.remove(entityID);
            }
        }

        private boolean isBypassed(int entityID) {
            return !closed && entityIds.contains(entityID);
        }

        @Override
        public void close() {
            synchronized (this) {
                closed = true;
            }
        }
    }

    public static BypassScope openFancyNpcScope() {
        synchronized (EntityBypassRegistry.class) {
            fancyNpcScope.close();
            fancyNpcScope = new BypassScope();
            return fancyNpcScope;
        }
    }

    public static BypassScope openFancyHologramScope() {
        synchronized (EntityBypassRegistry.class) {
            fancyHologramScope.close();
            fancyHologramScope = new BypassScope();
            return fancyHologramScope;
        }
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
        boolean removedNpc = fancyNpcScope.removeEntity(entityID);
        boolean removedHologram = fancyHologramScope.removeEntity(entityID);
        return removedBypass || removedSupport || removedNpc || removedHologram;
    }

    public static boolean isBypassed(int entityID) {
        return bypassedEntityIds.contains(entityID)
                || fancyNpcScope.isBypassed(entityID)
                || fancyHologramScope.isBypassed(entityID);
    }

    public static boolean isRelationshipSupportEntity(int entityID) {
        return relationshipSupportEntityIds.contains(entityID);
    }

    public static void reset() {
        synchronized (EntityBypassRegistry.class) {
            fancyNpcScope.close();
            fancyHologramScope.close();
            bypassedEntityIds = CopyOnWriteMTIntSet.get();
            relationshipSupportEntityIds = CopyOnWriteMTIntSet.get();
            fancyNpcScope = new BypassScope();
            fancyHologramScope = new BypassScope();
        }
    }
}