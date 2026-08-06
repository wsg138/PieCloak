/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2026 Cubicake.
 * This file is part of RaycastedAntiESP.
 * RaycastedAntiESP is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License v3.0 only, which can be accessed at https://www.gnu.org/licenses/agpl-3.0.html.
 * See README.md for warranty disclaimer and further information.
 */

package games.cubi.raycastedantiesp.paper.packets;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ListenerRegistrationTest {
    @Test
    void eachSuccessfulLifecycleRegistersAndUnregistersExactlyOnce() {
        AtomicInteger registrations = new AtomicInteger();
        AtomicInteger unregistrations = new AtomicInteger();

        for (int lifecycle = 0; lifecycle < 2; lifecycle++) {
            Object listener = new Object();
            ListenerRegistration<Object> registration = ListenerRegistration.register(
                    listener,
                    candidate -> {
                        registrations.incrementAndGet();
                        return candidate;
                    },
                    registered -> {
                        assertSame(listener, registered);
                        unregistrations.incrementAndGet();
                    }
            );

            registration.close();
            assertDoesNotThrow(registration::close);
            assertTrue(registration.isClosed());
        }

        assertEquals(2, registrations.get());
        assertEquals(2, unregistrations.get());
    }

    @Test
    void registrationFailureAttemptsCandidateCleanupExactlyOnce() {
        AtomicInteger registrations = new AtomicInteger();
        AtomicInteger unregistrations = new AtomicInteger();
        RuntimeException expected = new RuntimeException("registration failed after partial publication");
        Object listener = new Object();

        RuntimeException actual = assertThrows(RuntimeException.class, () -> ListenerRegistration.register(
                listener,
                candidate -> {
                    registrations.incrementAndGet();
                    throw expected;
                },
                registered -> {
                    assertSame(listener, registered);
                    unregistrations.incrementAndGet();
                }
        ));

        assertSame(expected, actual);
        assertEquals(1, registrations.get());
        assertEquals(1, unregistrations.get());
    }

    @Test
    void unregisterFailureIsSurfacedButNeverRetriedByRepeatedClose() {
        AtomicInteger registrations = new AtomicInteger();
        AtomicInteger unregistrations = new AtomicInteger();
        RuntimeException expected = new RuntimeException("unregister failed");
        Object listener = new Object();
        ListenerRegistration<Object> registration = ListenerRegistration.register(
                listener,
                candidate -> {
                    registrations.incrementAndGet();
                    return candidate;
                },
                registered -> {
                    unregistrations.incrementAndGet();
                    throw expected;
                }
        );

        RuntimeException actual = assertThrows(RuntimeException.class, registration::close);
        assertSame(expected, actual);
        assertDoesNotThrow(registration::close);
        assertTrue(registration.isClosed());
        assertEquals(1, registrations.get());
        assertEquals(1, unregistrations.get());
    }
}
