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
        Objects.requireNonNull(factory, "factory");
        Objects.requireNonNull(publishSecondary, "publishSecondary");
        Objects.requireNonNull(rollbackExternalResource, "rollbackExternalResource");

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
                if (owner != null) {
                    try {
                        rollbackExternalResource.run(owner);
                    } catch (Throwable rollbackFailure) {
                        failure.addSuppressed(rollbackFailure);
                    }
                }
                try {
                    restore(previousPrimary, previousSecondary);
                } catch (Throwable restoreFailure) {
                    failure.addSuppressed(restoreFailure);
                }
                throwUnchecked(failure);
                throw new AssertionError("unreachable");
            }
        }
    }

    void closeOwned(Object expectedOwner, Cleanup externalCleanup) {
        Objects.requireNonNull(expectedOwner, "expectedOwner");
        Objects.requireNonNull(externalCleanup, "externalCleanup");

        Throwable failure = null;
        try {
            externalCleanup.run();
        } catch (Throwable cleanupFailure) {
            failure = cleanupFailure;
        }

        try {
            release(expectedOwner);
        } catch (Throwable releaseFailure) {
            if (failure == null) {
                failure = releaseFailure;
            } else {
                failure.addSuppressed(releaseFailure);
            }
        }

        if (failure != null) {
            throwUnchecked(failure);
        }
    }

    void release(Object expectedOwner) {
        Objects.requireNonNull(expectedOwner, "expectedOwner");
        synchronized (lock) {
            // Clear the primary slot first. The secondary getter lazily copies the primary value;
            // this order prevents a concurrent getter from permanently re-publishing the old owner.
            if (primary.get() == expectedOwner) {
                primary.set(null);
            }
            if (secondary.get() == expectedOwner) {
                secondary.set(null);
            }
        }
    }

    void verifyConsistentOrEmpty() {
        synchronized (lock) {
            Object currentPrimary = primary.get();
            Object currentSecondary = secondary.get();
            if (currentPrimary != null && currentSecondary != null && currentPrimary != currentSecondary) {
                throw new IllegalStateException("Entity controller ownership slots contain different owners");
            }
        }
    }

    private void restore(Object previousPrimary, Object previousSecondary) {
        // Restore in the same order as release so a lazy secondary lookup cannot capture a failed owner.
        primary.set(previousPrimary);
        secondary.set(previousSecondary);
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

    private static void throwUnchecked(Throwable failure) {
        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        throw new IllegalStateException("Unexpected checked controller lifecycle failure", failure);
    }
}
