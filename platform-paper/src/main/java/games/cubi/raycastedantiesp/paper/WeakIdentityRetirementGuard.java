/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright Ac 2026 Cubicake.
 * This file is part of RaycastedAntiESP.
 * RaycastedAntiESP is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License v3.0 only.
 */

package games.cubi.raycastedantiesp.paper;

import games.cubi.raycastedantiesp.core.entity.EntityBypassRegistry;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Tracks optional-integration bypass ownership by object identity and native
 * entity ID. All state changes and scope updates share the same monitor.
 * Weak keys allow abandoned owners to release their bypasses on queue drain.
 */
final class WeakIdentityRetirementGuard<T> {
    private final EntityBypassRegistry.BypassScope bypassScope;
    private final ReferenceQueue<T> referenceQueue = new ReferenceQueue<>();
    private final Map<IdentityReference<T>, OwnerState> owners = new HashMap<>();
    private final Map<Integer, Integer> activeOwnerCounts = new HashMap<>();

    WeakIdentityRetirementGuard(EntityBypassRegistry.BypassScope bypassScope) {
        this.bypassScope = Objects.requireNonNull(bypassScope, "bypassScope");
    }

    boolean registerIfActive(T owner, int entityId) {
        Objects.requireNonNull(owner, "owner");
        synchronized (this) {
            discardCollected();
            OwnerState state = getOrCreate(owner);
            if (state.retiredIds.contains(entityId)) {
                return false;
            }
            if (!state.activeIds.contains(entityId)) {
                // An NPC/hologram owner can have only one live native entity.
                // A changed ID means its previous incarnation is retired,
                // even if the optional plugin omitted the remove event.
                for (int previousId : state.activeIds) {
                    state.retiredIds.add(previousId);
                    releaseOwner(previousId);
                }
                state.activeIds.clear();
                state.activeIds.add(entityId);
                activeOwnerCounts.merge(entityId, 1, Integer::sum);
            }
            // The underlying scope add is idempotent. Keep classification
            // current even if a separate world-removal event cleared the ID.
            bypassScope.addEntity(entityId);
            return true;
        }
    }

    void retire(T owner, int entityId) {
        Objects.requireNonNull(owner, "owner");
        synchronized (this) {
            discardCollected();
            OwnerState state = getOrCreate(owner);
            if (!state.retiredIds.add(entityId)) {
                return;
            }
            if (state.activeIds.remove(entityId)) {
                releaseOwner(entityId);
            }
        }
    }

    private OwnerState getOrCreate(T owner) {
        IdentityReference<T> probe = new IdentityReference<>(owner, null);
        OwnerState state = owners.get(probe);
        if (state == null) {
            state = new OwnerState();
            owners.put(new IdentityReference<>(owner, referenceQueue), state);
        }
        return state;
    }

    private void releaseOwner(int entityId) {
        int remaining = activeOwnerCounts.getOrDefault(entityId, 0);
        if (remaining <= 1) {
            activeOwnerCounts.remove(entityId);
            bypassScope.removeEntity(entityId);
        } else {
            activeOwnerCounts.put(entityId, remaining - 1);
        }
    }

    @SuppressWarnings("unchecked") // Only our own IdentityReference values enter this queue.
    private void discardCollected() {
        IdentityReference<T> expired;
        while ((expired = (IdentityReference<T>) referenceQueue.poll()) != null) {
            OwnerState state = owners.remove(expired);
            if (state != null) {
                for (int entityId : state.activeIds) {
                    releaseOwner(entityId);
                }
            }
        }
    }

    private static final class OwnerState {
        final Set<Integer> activeIds = new HashSet<>();
        final Set<Integer> retiredIds = new HashSet<>();
    }

    private static final class IdentityReference<T> extends WeakReference<T> {
        private final int identityHash;

        private IdentityReference(T owner, ReferenceQueue<T> queue) {
            super(owner, queue);
            identityHash = System.identityHashCode(owner);
        }

        @Override
        public int hashCode() {
            return identityHash;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof IdentityReference<?> reference)) {
                return false;
            }
            Object mine = get();
            return mine != null && mine == reference.get();
        }
    }
}