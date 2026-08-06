/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2026 Cubicake.
 * This file is part of RaycastedAntiESP.
 * RaycastedAntiESP is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License v3.0 only, which can be accessed at https://www.gnu.org/licenses/agpl-3.0.html.
 * See README.md for warranty disclaimer and further information.
 */

package games.cubi.raycastedantiesp.paper.packets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControllerOwnershipTest {
    private static final Object LOCK = new Object();

    private static final class Holder {
        private static Object primary;
        private static Object secondary;
    }

    private static final class Owner {
    }

    private static final class OwnershipSlot implements ControllerOwnership.Slot {
        private Object value;
        private RuntimeException setFailure;
        private final AtomicInteger setAttempts = new AtomicInteger();

        OwnershipSlot(Object value) {
            this.value = value;
        }

        @Override
        public Object get() {
            return value;
        }

        @Override
        public void set(Object value) {
            setAttempts.incrementAndGet();
            if (setFailure != null) {
                throw setFailure;
            }
            this.value = value;
        }
    }

    @AfterEach
    @SuppressWarnings("PMD.NullAssignment") // Static test slots model the production ownership release contract.
    void clearSlots() {
        Holder.primary = null;
        Holder.secondary = null;
    }

    @Test
    void constructCloseAndReconstructUsesOneOwnerForBothSlots() {
        ControllerOwnership ownership = ownership();
        Owner first = construct(ownership);

        assertSame(first, Holder.primary);
        assertSame(first, Holder.secondary);

        ownership.closeOwned(first, () -> { });
        assertNull(Holder.primary);
        assertNull(Holder.secondary);

        Owner second = construct(ownership);
        assertSame(second, Holder.primary);
        assertSame(second, Holder.secondary);
        ownership.closeOwned(second, () -> { });
    }

    @Test
    void failedConstructionRollsBackThePrimaryClaim() {
        ControllerOwnership ownership = ownership();
        RuntimeException expected = new RuntimeException("constructor failed");

        RuntimeException actual = assertThrows(RuntimeException.class, () -> ownership.construct(
                () -> {
                    Holder.primary = new Owner();
                    throw expected;
                },
                owner -> Holder.secondary = owner,
                owner -> { }
        ));

        assertSame(expected, actual);
        assertNull(Holder.primary);
        assertNull(Holder.secondary);
        assertDoesNotThrow(() -> ownership.closeOwned(construct(ownership), () -> { }));
    }

    @Test
    void failedSecondaryPublicationRollsBackBothClaimsAndExternalResource() {
        ControllerOwnership ownership = ownership();
        AtomicBoolean rolledBack = new AtomicBoolean();
        RuntimeException expected = new RuntimeException("publication failed");

        RuntimeException actual = assertThrows(RuntimeException.class, () -> ownership.construct(
                () -> {
                    Owner owner = new Owner();
                    Holder.primary = owner;
                    return owner;
                },
                owner -> {
                    Holder.secondary = owner;
                    throw expected;
                },
                owner -> rolledBack.set(true)
        ));

        assertSame(expected, actual);
        assertTrue(rolledBack.get());
        assertNull(Holder.primary);
        assertNull(Holder.secondary);
        assertDoesNotThrow(() -> ownership.closeOwned(construct(ownership), () -> { }));
    }

    @Test
    void failedExternalRollbackMarksLifecycleUnsafeAndStillRestoresSlots() {
        ControllerOwnership ownership = ownership();
        AtomicBoolean unsafeCleanup = new AtomicBoolean();
        RuntimeException constructionFailure = new RuntimeException("publication failed");
        RuntimeException cleanupFailure = new RuntimeException("listener rollback failed");

        RuntimeException actual = assertThrows(RuntimeException.class, () -> ownership.construct(
                () -> {
                    Owner owner = new Owner();
                    Holder.primary = owner;
                    return owner;
                },
                owner -> {
                    Holder.secondary = owner;
                    throw constructionFailure;
                },
                owner -> { throw cleanupFailure; },
                () -> unsafeCleanup.set(true)
        ));

        assertSame(constructionFailure, actual);
        assertEquals(1, actual.getSuppressed().length);
        assertSame(cleanupFailure, actual.getSuppressed()[0]);
        assertTrue(unsafeCleanup.get());
        assertNull(Holder.primary);
        assertNull(Holder.secondary);
    }

    @Test
    void successfulRollbackDoesNotMarkLifecycleUnsafe() {
        ControllerOwnership ownership = ownership();
        AtomicBoolean unsafeCleanup = new AtomicBoolean();
        RuntimeException constructionFailure = new RuntimeException("publication failed");

        assertThrows(RuntimeException.class, () -> ownership.construct(
                () -> {
                    Owner owner = new Owner();
                    Holder.primary = owner;
                    return owner;
                },
                owner -> { throw constructionFailure; },
                owner -> { },
                () -> unsafeCleanup.set(true)
        ));

        assertFalse(unsafeCleanup.get());
        assertNull(Holder.primary);
        assertNull(Holder.secondary);
    }

    @Test
    void repeatedOwnershipReleaseIsHarmless() {
        ControllerOwnership ownership = ownership();
        Owner owner = construct(ownership);

        ownership.closeOwned(owner, () -> { });
        assertDoesNotThrow(() -> ownership.closeOwned(owner, () -> { }));
        assertNull(Holder.primary);
        assertNull(Holder.secondary);
    }

    @Test
    void staleOwnerCannotReleaseReplacementOwner() {
        ControllerOwnership ownership = ownership();
        Owner stale = construct(ownership);
        ownership.closeOwned(stale, () -> { });
        Owner replacement = construct(ownership);

        ownership.closeOwned(stale, () -> { });

        assertSame(replacement, Holder.primary);
        assertSame(replacement, Holder.secondary);
        ownership.closeOwned(replacement, () -> { });
    }

    @Test
    void cleanupFailureStillReleasesBothOwnershipSlotsAndPropagates() {
        ControllerOwnership ownership = ownership();
        Owner owner = construct(ownership);
        RuntimeException expected = new RuntimeException("listener unregister failed");

        RuntimeException actual = assertThrows(RuntimeException.class,
                () -> ownership.closeOwned(owner, () -> { throw expected; }));

        assertSame(expected, actual);
        assertNull(Holder.primary);
        assertNull(Holder.secondary);
    }

    @Test
    void secondSingletonReleaseIsAttemptedWhenFirstReleaseFails() {
        Owner owner = new Owner();
        OwnershipSlot primary = new OwnershipSlot(owner);
        OwnershipSlot secondary = new OwnershipSlot(owner);
        RuntimeException expected = new RuntimeException("primary release failed");
        primary.setFailure = expected;
        ControllerOwnership ownership = new ControllerOwnership(LOCK, primary, secondary);

        RuntimeException actual = assertThrows(RuntimeException.class,
                () -> ownership.closeOwned(owner, () -> { }));

        assertSame(expected, actual);
        assertSame(owner, primary.get());
        assertNull(secondary.get());
        assertEquals(1, primary.setAttempts.get());
        assertEquals(1, secondary.setAttempts.get());
    }

    @Test
    void rollbackAttemptsBothSingletonRestoresWhenFirstRestoreFails() {
        OwnershipSlot primary = new OwnershipSlot(null);
        OwnershipSlot secondary = new OwnershipSlot(null);
        RuntimeException restoreFailure = new RuntimeException("primary restore failed");
        ControllerOwnership ownership = new ControllerOwnership(LOCK, primary, secondary);
        RuntimeException constructionFailure = new RuntimeException("construction failed");
        AtomicBoolean unsafeCleanup = new AtomicBoolean();

        RuntimeException actual = assertThrows(RuntimeException.class, () -> ownership.construct(
                () -> {
                    primary.value = new Owner();
                    primary.setFailure = restoreFailure;
                    throw constructionFailure;
                },
                owner -> secondary.value = owner,
                owner -> { },
                () -> unsafeCleanup.set(true)
        ));

        assertSame(constructionFailure, actual);
        assertEquals(1, actual.getSuppressed().length);
        assertSame(restoreFailure, actual.getSuppressed()[0]);
        assertTrue(unsafeCleanup.get());
        assertEquals(1, primary.setAttempts.get());
        assertEquals(1, secondary.setAttempts.get());
        assertNull(secondary.get());
    }

    @Test
    void consistencyCheckRejectsOneSidedOwnership() {
        ControllerOwnership ownership = ownership();
        Holder.primary = new Owner();

        assertThrows(IllegalStateException.class, ownership::verifyConsistentOrEmpty);
    }

    @Test
    void productionBindingsResolveAndPaperConstructionIsFactoryOnly() {
        assertDoesNotThrow(EntityControllerOwnership::verifyBindings);
        Constructor<?>[] constructors = PaperPacketEventsEntityViewController.class.getDeclaredConstructors();
        assertTrue(constructors.length > 0);
        for (Constructor<?> constructor : constructors) {
            assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        }
    }

    private static ControllerOwnership ownership() {
        return new ControllerOwnership(
                LOCK,
                ControllerOwnership.reflectiveStaticSlot(Holder.class, "primary"),
                ControllerOwnership.reflectiveStaticSlot(Holder.class, "secondary")
        );
    }

    private static Owner construct(ControllerOwnership ownership) {
        return ownership.construct(
                () -> {
                    Owner owner = new Owner();
                    Holder.primary = owner;
                    return owner;
                },
                owner -> Holder.secondary = owner,
                owner -> { }
        );
    }
}
