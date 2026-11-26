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
import java.util.function.Function;

/**
 * Tracks optional-integration bypass ownership by object identity and native
 * entity ID. All state changes and scope updates share the same monitor.
 * Weak keys allow abandoned owners to release their bypasses on queue drain.
 */
final class WeakIdentityRetirementGuard<T> {
    private static final String OWNER_PARAM = "owner";
    private final EntityBypassRegistry.BypassScope bypassScope;
    private final ReferenceQueue<T> referenceQueue = new ReferenceQueue<>();
    private final Map<IdentityReference<T>, OwnerState> owners = new HashMap<>();
    private final Map<Integer, Integer> activeOwnerCounts = new HashMap<>();

    WeakIdentityRetirementGuard(EntityBypassRegistry.BypassScope bypassScope) {
        this.bypassScope = Objects.requireNonNull(bypassScope, "bypassScope");
    }

    /**
     * Resolve the native ID under the same monitor as retirement and registration.
     * Concurrent callbacks cannot publish a previously captured ID after a newer
     * callback has already registered its replacement.
     */
    boolean registerCurrent(T owner, Function<? super T, Integer> resolver) {
        Objects.requireNonNull(owner, OWNER_PARAM);
        Objects.requireNonNull(resolver, "resolver");
        synchronized (this) {
            discardCollected();
            OwnerState state = getOrCreate(owner);
            if (state.permanentlyRetired) {
                return false;
            }
            Integer entityId = resolver.apply(owner);
            return entityId != null && registerIfActive(owner, entityId);
        }
    }

    /**
     * Manager membership is authoritative for objects observed in a manager.
     * Never-managed API NPCs retain the existing callback-based behavior.
     */
    boolean registerManagedCurrent(T owner, Function<? super T, Integer> resolver,
                                   Function<? super T, ? extends T> managerLookup) {
        Objects.requireNonNull(owner, OWNER_PARAM);
        Objects.requireNonNull(managerLookup, "managerLookup");
        synchronized (this) {
            discardCollected();
            OwnerState state = getOrCreate(owner);
            return !state.permanentlyRetired && checkMembership(owner, state, managerLookup)
                    && registerCurrent(owner, resolver);
        }
    }

    /** Reconcile quiet removals even while external plugins retain NPC objects. */
    void sweepManagedOwners(Function<? super T, ? extends T> managerLookup) {
        Objects.requireNonNull(managerLookup, "managerLookup");
        synchronized (this) {
            discardCollected();
            for (var entry : owners.entrySet()) {
                T owner = entry.getKey().get();
                OwnerState state = entry.getValue();
                if (owner != null && !state.permanentlyRetired && !state.activeIds.isEmpty()) {
                    checkMembership(owner, state, managerLookup);
                }
            }
        }
    }

    // Manager ownership is object identity, not logical equality of NPC data.
    @SuppressWarnings("PMD.CompareObjectsWithEquals")
    private boolean checkMembership(T owner, OwnerState state,
                                    Function<? super T, ? extends T> managerLookup) {
        T current = managerLookup.apply(owner);
        if (current == owner) {
            state.managerObserved = true;
            return true;
        }
        if (current != null || state.managerObserved) {
            // A replacement at the same stable ID also proves this owner is stale.
            // Unlike explicit command deletion, direct removal permits legitimate
            // re-registration of the very same object in the manager.
            state.managerObserved = true;
            releaseActiveIds(state);
            return false;
        }
        return true;
    }

    private void releaseActiveIds(OwnerState state) {
        for (int activeId : state.activeIds) {
            releaseOwner(activeId);
        }
        state.activeIds.clear();
    }

    boolean registerIfActive(T owner, int entityId) {
        Objects.requireNonNull(owner, OWNER_PARAM);
        synchronized (this) {
            discardCollected();
            OwnerState state = getOrCreate(owner);
            if (state.permanentlyRetired || state.retiredIds.contains(entityId)) {
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
        Objects.requireNonNull(owner, OWNER_PARAM);
        synchronized (this) {
            discardCollected();
            OwnerState state = getOrCreate(owner);
            if (state.permanentlyRetired || !state.retiredIds.add(entityId)) {
                return;
            }
            if (state.activeIds.remove(entityId)) {
                releaseOwner(entityId);
            }
        }
    }

    /**
     * Permanent NPC removal may follow a native entity replacement before the
     * new incarnation has ever spawned for a viewer. Release all recorded
     * incarnations of this owner, not another owner's claims on the same IDs.
     * A permanent removal fences even previously unseen IDs from delayed
     * callbacks on the deleted object. This tombstone is identity-scoped and
     * weakly held, so replacement NPC objects and other integrations are unaffected.
     */
    void retireOwner(T owner) {
        Objects.requireNonNull(owner, OWNER_PARAM);
        synchronized (this) {
            discardCollected();
            OwnerState state = getOrCreate(owner);
            state.permanentlyRetired = true;
            releaseActiveIds(state);
            state.retiredIds.clear();
        }
    }

    // The queue must also be polled during quiet periods: without a scheduled
    // sweep, collected owners can leave stale numeric exemptions indefinitely.
    synchronized void sweepCollected() {
        discardCollected();
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
        boolean permanentlyRetired;
        boolean managerObserved;
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