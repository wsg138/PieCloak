/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2026 Cubicake.
 * This file is part of RaycastedAntiESP.
 * RaycastedAntiESP is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License v3.0 only, which can be accessed at https://www.gnu.org/licenses/agpl-3.0.html.
 * See README.md for warranty disclaimer and further information.
 */

package games.cubi.raycastedantiesp.paper.packets;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns one successful listener registration and unregisters it at most once. */
final class ListenerRegistration<T> implements AutoCloseable {
    private static final Runnable NO_UNSAFE_CLEANUP = () -> { };

    @FunctionalInterface
    interface Register<T> {
        T register(T candidate) throws Throwable;
    }

    @FunctionalInterface
    interface Unregister<T> {
        void unregister(T registration) throws Throwable;
    }

    private final T registration;
    private final Unregister<T> unregister;
    private final AtomicBoolean closed = new AtomicBoolean();

    private ListenerRegistration(T registration, Unregister<T> unregister) {
        this.registration = Objects.requireNonNull(registration, "registration");
        this.unregister = Objects.requireNonNull(unregister, "unregister");
    }

    static <T> ListenerRegistration<T> register(
            T candidate, Register<T> register, Unregister<T> unregister) {
        return register(candidate, register, unregister, NO_UNSAFE_CLEANUP);
    }

    static <T> ListenerRegistration<T> register(
            T candidate, Register<T> register, Unregister<T> unregister, Runnable markUnsafeCleanup) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(register, "register");
        Objects.requireNonNull(unregister, "unregister");
        Objects.requireNonNull(markUnsafeCleanup, "markUnsafeCleanup");

        try {
            T registration = Objects.requireNonNull(register.register(candidate), "register returned null");
            return new ListenerRegistration<>(registration, unregister);
        } catch (Throwable failure) {
            try {
                unregister.unregister(candidate);
            } catch (Throwable cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
                try {
                    markUnsafeCleanup.run();
                } catch (Throwable markerFailure) {
                    failure.addSuppressed(markerFailure);
                }
            }
            ControllerOwnership.throwUnchecked(failure);
            throw new AssertionError("unreachable");
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            unregister.unregister(registration);
        } catch (Throwable failure) {
            ControllerOwnership.throwUnchecked(failure);
        }
    }

    boolean isClosed() {
        return closed.get();
    }
}
