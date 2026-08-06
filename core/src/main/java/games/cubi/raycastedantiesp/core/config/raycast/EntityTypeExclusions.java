/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2026 Cubicake.
 * This file is part of RaycastedAntiESP.
 * RaycastedAntiESP is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License v3.0 only, which can be accessed at https://www.gnu.org/licenses/agpl-3.0.html.
 * See README.md for warranty disclaimer and further information.
 */

package games.cubi.raycastedantiesp.core.config.raycast;

import games.cubi.logs.Logger;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;

/** Immutable entity-type exclusion policy resolved during plugin startup. */
public final class EntityTypeExclusions {
    private static volatile IntSet exclusions;

    private EntityTypeExclusions() {
    }

    public static synchronized void initialise(IntSet resolved) {
        if (exclusions != null) {
            throw new IllegalStateException("Entity type exclusions are already initialised");
        }
        exclusions = IntSets.unmodifiable(new IntOpenHashSet(resolved));
    }

    public static synchronized void reset() {
        exclusions = null;
    }

    public static boolean excludes(int entityType) {
        IntSet current = exclusions;
        if (current == null) {
            Logger.error(new IllegalStateException("Exclusion set was not initialised before being read"), 4,
                    EntityTypeExclusions.class);
            return false;
        }
        return current.contains(entityType);
    }

    public static int size() {
        IntSet current = exclusions;
        return current == null ? 0 : current.size();
    }
}
