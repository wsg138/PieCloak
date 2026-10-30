/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright Ac 2026 Cubicake.
 * This file is part of RaycastedAntiESP.
 * RaycastedAntiESP is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License v3.0 only.
 */

package games.cubi.raycastedantiesp.paper;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Objects;
import java.util.Map;

/**
 * Serializes callbacks for an entity owner and rejects callbacks from an
 * already removed owner incarnation, even if the integration remains enabled.
 * Weak identity keys permit garbage collection without reusing the old key
 * for a different owner, or the same owner after it creates a fresh entity ID.
 */
final class WeakIdentityRetirementGuard<T> {
    private final ReferenceQueue<T> referenceQueue = new ReferenceQueue<>();
    private final Map<IdentityReference<T>, Integer> retiredEntityIds = new HashMap<>();

    boolean registerIfActive(T owner, int entityId, Runnable register) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(register, "register");
        synchronized (this) {
            discardCollected();
            Integer retiredId = retiredEntityIds.get(new IdentityReference<>(owner, null));
            if (retiredId != null && retiredId == entityId) {
                return false;
            }
            register.run();
            return true;
        }
    }

    void retire(T owner, int entityId, Runnable unregister) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(unregister, "unregister");
        synchronized (this) {
            discardCollected();
            // A duplicate remove for a retired old owner must not clear the
            // replacement owner if it has legitimately claimed the same ID.
            Integer alreadyRetired = retiredEntityIds.get(new IdentityReference<>(owner, null));
            if (alreadyRetired != null && alreadyRetired == entityId) {
                return;
            }
            retiredEntityIds.put(new IdentityReference<>(owner, referenceQueue), entityId);
            unregister.run();
        }
    }

    @SuppressWarnings("unchecked") // ReferenceQueue holds only our own reference subtype.
    private void discardCollected() {
        while (true) {
            IdentityReference<T> expired = (IdentityReference<T>) referenceQueue.poll();
            if (expired == null) {
                return;
            }
            retiredEntityIds.remove(expired);
        }
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