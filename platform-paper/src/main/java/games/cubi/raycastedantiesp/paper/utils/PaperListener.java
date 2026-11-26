/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2026 Cubicake.
 * This file is part of RaycastedAntiESP. RaycastedAntiESP is free software: you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License v3.0 only, which can be accessed at
 * https://www.gnu.org/licenses/agpl-3.0.html. See README.md for warranty disclaimer and further information.
 */

package games.cubi.raycastedantiesp.paper.utils;

import games.cubi.raycastedantiesp.paper.RaycastedAntiESP;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.util.concurrent.atomic.AtomicBoolean;

public abstract class PaperListener implements Listener, AutoCloseable {
    private final AtomicBoolean registered = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();

    @SuppressWarnings("unchecked")
    public final <T extends PaperListener> T register() {
        if (closed.get()) {
            throw new IllegalStateException("Cannot register a closed listener");
        }
        if (!registered.compareAndSet(false, true)) {
            throw new IllegalStateException("Listener is already registered");
        }
        try {
            Bukkit.getPluginManager().registerEvents(this, RaycastedAntiESP.get());
            return (T) this;
        } catch (RuntimeException | Error throwable) {
            registered.set(false);
            closed.set(true);
            try {
                HandlerList.unregisterAll(this);
            } catch (RuntimeException | Error cleanupFailure) {
                throwable.addSuppressed(cleanupFailure);
            }
            throw throwable;
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true) && registered.getAndSet(false)) {
            HandlerList.unregisterAll(this);
        }
    }
}
