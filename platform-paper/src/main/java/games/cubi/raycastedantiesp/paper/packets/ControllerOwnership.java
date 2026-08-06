/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2026 Cubicake.
 * This file is part of RaycastedAntiESP.
 * RaycastedAntiESP is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License v3.0 only, which can be accessed at https://www.gnu.org/licenses/agpl-3.0.html.
 * See README.md for warranty disclaimer and further information.
 */

package games.cubi.raycastedantiesp.paper.packets;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Objects;

/**
 * Coordinates two static ownership slots as one lifecycle unit.
 *
 * <p>The lock must be the same lock used by the first ownership slot's constructor claim. Keeping
 * construction under that lock makes a constructor failure rollback deterministic: no replacement
 * owner can appear between the failed claim and restoration of the previous state.</p>
 */
final class ControllerOwnership {
    private static final Runnable NO_UNSAFE_CLEANUP = () -> { };

    @FunctionalInterface
    interface Factory<T> {
        T create() throws Throwable;
    }

    @FunctionalInterface
    interface OwnerAction<T> {
        void run(T owner) throws Throwable;
    }

    @FunctionalInterface
    interface Cleanup {
        void run() throws Throwable;
    }

    interface Slot {
        Object get();

        void set(Object value);
    }

    private final Object lock;
    private final Slot primary;
    private final Slot secondary;

    ControllerOwnership(Object lock, Slot primary, Slot secondary) {
        this.lock = Objects.requireNonNull(lock, "lock");
        this.primary = Objects.requireNonNull(primary, "primary");
        this.secondary = Objects.requireNonNull(secondary, "secondary");
    }

    <T> T construct(Factory<T> factory, OwnerAction<T> publishSecondary,
            OwnerAction<T> rollbackExternalResource) {
        return construct(factory, publishSecondary, rollbackExternalResource, NO_UNSAFE_CLEANUP);
    }

    @SuppressWarnings("PMD.CompareObjectsWithEquals")
    <T> T construct(Factory<T> factory, OwnerAction<T> publishSecondary,
            OwnerAction<T> rollbackExternalResource, Runnable markUnsafeCleanup) {
        Objects.requireNonNull(factory, "factory");
        Objects.requireNonNull(publishSecondary, "publishSecondary");
        Objects.requireNonNull(rollbackExternalResource, "rollbackExternalResource");
        Objects.requireNonNull(markUnsafeCleanup, "markUnsafeCleanup");

        synchronized (lock) {
            Object previousPrimary = primary.get();
            Object previousSecondary = secondary.get();
            if (previousPrimary != null || previousSecondary != null) {
                throw new IllegalStateException("Entity controller ownership is already active or partially claimed");
            }

            T owner = null;
            try {
                owner = Objects.requireNonNull(factory.create(), "factory returned null");
                publishSecondary.run(owner);
                if (primary.get() != owner || secondary.get() != owner) {
                    throw new IllegalStateException("Entity controller construction did not publish one consistent owner");
                }
                return owner;
            } catch (Throwable failure) {
                boolean unsafeCleanup = false;
                if (owner != null) {
                    T constructedOwner = owner;
                    int previousSuppressed = failure.getSuppressed().length;
                    failure = attempt(failure, () -> rollbackExternalResource.run(constructedOwner));
                    unsafeCleanup |= failure.getSuppressed().length > previousSuppressed;
                }

                int previousSuppressed = failure.getSuppressed().length;
                failure = attempt(failure, () -> primary.set(previousPrimary));
                unsafeCleanup |= failure.getSuppressed().length > previousSuppressed;

                previousSuppressed = failure.getSuppressed().length;
                failure = attempt(failure, () -> secondary.set(previousSecondary));
                unsafeCleanup |= failure.getSuppressed().length > previousSuppressed;

                if (unsafeCleanup) {
                    failure = attempt(failure, markUnsafeCleanup::run);
                }
                throwUnchecked(failure);
                throw new AssertionError("unreachable");
            }
        }
    }

    void closeOwned(Object expectedOwner, Cleanup externalCleanup) {
        Objects.requireNonNull(expectedOwner, "expectedOwner");
        Objects.requireNonNull(externalCleanup, "externalCleanup");

        Throwable failure = attempt(null, externalCleanup);
        failure = attempt(failure, () -> releasePrimaryOwned(expectedOwner));
        failure = attempt(failure, () -> releaseSecondaryOwned(expectedOwner));
        if (failure != null) {
            throwUnchecked(failure);
        }
    }

    @SuppressWarnings("PMD.CompareObjectsWithEquals")
    void releasePrimaryOwned(Object expectedOwner) {
        releaseSlotOwned(primary, expectedOwner);
    }

    @SuppressWarnings("PMD.CompareObjectsWithEquals")
    void releaseSecondaryOwned(Object expectedOwner) {
        releaseSlotOwned(secondary, expectedOwner);
    }

    @SuppressWarnings("PMD.CompareObjectsWithEquals")
    void verifyConsistentOrEmpty() {
        synchronized (lock) {
            Object currentPrimary = primary.get();
            Object currentSecondary = secondary.get();
            if (currentPrimary != currentSecondary) {
                throw new IllegalStateException("Entity controller ownership is partial or contains different owners");
            }
        }
    }

    @SuppressWarnings("PMD.CompareObjectsWithEquals")
    private void releaseSlotOwned(Slot slot, Object expectedOwner) {
        synchronized (lock) {
            if (slot.get() == expectedOwner) {
                slot.set(null);
            }
        }
    }

    static Slot reflectiveStaticSlot(Class<?> declaringClass, String fieldName) {
        Objects.requireNonNull(declaringClass, "declaringClass");
        Objects.requireNonNull(fieldName, "fieldName");
        try {
            Field field = declaringClass.getDeclaredField(fieldName);
            if (!Modifier.isStatic(field.getModifiers())) {
                throw new IllegalStateException(declaringClass.getName() + "." + fieldName + " is not static");
            }
            if (!field.trySetAccessible()) {
                throw new IllegalStateException("Could not access " + declaringClass.getName() + "." + fieldName);
            }
            return new ReflectiveSlot(field);
        } catch (NoSuchFieldException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private record ReflectiveSlot(Field field) implements Slot {
        @Override
        public Object get() {
            try {
                return field.get(null);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Could not read controller ownership field " + field, exception);
            }
        }

        @Override
        public void set(Object value) {
            try {
                field.set(null, value);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Could not update controller ownership field " + field, exception);
            }
        }
    }

    private static Throwable attempt(Throwable previous, Cleanup action) {
        try {
            action.run();
            return previous;
        } catch (Throwable failure) {
            if (previous == null) {
                return failure;
            }
            previous.addSuppressed(failure);
            return previous;
        }
    }

    static void throwUnchecked(Throwable failure) {
        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        throw new IllegalStateException("Unexpected checked controller lifecycle failure", failure);
    }
}
