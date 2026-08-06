/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2026 Cubicake.
 * This file is part of RaycastedAntiESP.
 * RaycastedAntiESP is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License v3.0 only, which can be accessed at https://www.gnu.org/licenses/agpl-3.0.html.
 * See README.md for warranty disclaimer and further information.
 */

package games.cubi.raycastedantiesp.paper.utils;

import com.destroystokyo.paper.event.server.ServerTickStartEvent;
import games.cubi.logs.Logger;
import games.cubi.raycastedantiesp.core.Ticker;
import games.cubi.raycastedantiesp.core.engine.AsyncEngine;
import games.cubi.raycastedantiesp.core.utils.VarHandler;
import games.cubi.raycastedantiesp.paper.EventListener;
import games.cubi.raycastedantiesp.paper.RaycastedAntiESP;
import games.cubi.raycastedantiesp.paper.internals.HackyEntityIDGuard;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

import java.lang.invoke.VarHandle;
import java.util.concurrent.atomic.AtomicBoolean;

public class PaperTicker extends PaperListener implements Ticker {
    private volatile int currentTick; private static final VarHandle CURRENT_TICK = VarHandler.get(PaperTicker.class, "currentTick", int.class);
    private final HackyEntityIDGuard entityIDGuard = new HackyEntityIDGuard();
    private final AtomicBoolean started = new AtomicBoolean();

    @Override
    public void start() {
        started.set(true);
    }

    @Override
    public int getAsInt() {
        return (int) CURRENT_TICK.getOpaque(this);
    }

    @Override
    public void close() {
        started.set(false);
        super.close();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void serverTickStartEvent(ServerTickStartEvent event) {
        if (!started.get()) {
            return;
        }
        int current = (int) CURRENT_TICK.getOpaque(this);
        int newTick = current + 1;
        CURRENT_TICK.setOpaque(this, newTick);

        entityIDGuard.tick(newTick);
        AsyncEngine engine = RaycastedAntiESP.getEngine();
        if (engine == null || !engine.markTickRunning()) {
            Logger.info("Skipped starting tick because previous tick is still running or shutdown has begun.", 6, EventListener.class);
            return;
        }
        long scheduledNanos = System.nanoTime();
        try {
            Bukkit.getAsyncScheduler().runNow(RaycastedAntiESP.get(), task -> engine.tick(newTick, scheduledNanos));
        } catch (RuntimeException exception) {
            engine.cancelPendingTickReservation();
            Logger.error("Failed to schedule engine tick after reserving it. Cleared the pending reservation so future ticks can continue.", exception, 2, EventListener.class);
        }
    }
}
