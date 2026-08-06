/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2026 Cubicake.
 * This file is part of RaycastedAntiESP.
 * RaycastedAntiESP is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License v3.0 only, which can be accessed at https://www.gnu.org/licenses/agpl-3.0.html.
 * See README.md for warranty disclaimer and further information.
 */

package games.cubi.raycastedantiesp.paper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventListenerTest {
    @Test
    void updateCheckWindowUsesElapsedMonotonicTicks() {
        int joinTick = 1_000;

        assertTrue(EventListener.isWithinJoinUpdateWindow(joinTick, joinTick));
        assertTrue(EventListener.isWithinJoinUpdateWindow(joinTick, joinTick + 9));
        assertFalse(EventListener.isWithinJoinUpdateWindow(joinTick, joinTick + 10));
        assertFalse(EventListener.isWithinJoinUpdateWindow(joinTick, joinTick - 1));
    }

    @Test
    void updateCheckWindowSurvivesTickCounterOverflow() {
        int joinTick = Integer.MAX_VALUE - 2;
        int currentTick = Integer.MIN_VALUE + 2;

        assertTrue(EventListener.isWithinJoinUpdateWindow(joinTick, currentTick));
    }
}
