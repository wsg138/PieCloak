/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2026 Cubicake.
 * This file is part of RaycastedAntiESP.
 * RaycastedAntiESP is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License v3.0 only, which can be accessed at https://www.gnu.org/licenses/agpl-3.0.html.
 * See README.md for warranty disclaimer and further information.
 */

package games.cubi.raycastedantiesp.paper.utils;

import games.cubi.logs.Logger;
import games.cubi.raycastedantiesp.core.Ticker;
import games.cubi.raycastedantiesp.core.engine.AsyncEngine;
import games.cubi.raycastedantiesp.core.utils.VarHandler;
import games.cubi.raycastedantiesp.paper.EventListener;
import games.cubi.raycastedantiesp.paper.RaycastedAntiESP;
import games.cubi.raycastedantiesp.paper.internals.HackyEntityIDGuard;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;

import java.lang.invoke.VarHandle;
import java.util.concurrent.atomic.AtomicReference;

public class FoliaTicker implements Ticker {
    private volatile int currentTick; private static final VarHandle CURRENT_TICK = VarHandler.get(FoliaTicker.class, "currentTick", int.class);
    private final HackyEntityIDGuard entityIDGuard = new HackyEntityIDGuard();
    private final AtomicReference<ScheduledTask> task = new AtomicReference<>();

    @Override
    public void start() {
        ScheduledTask scheduled = Bukkit.getGlobalRegionScheduler().runAtFixedRate(RaycastedAntiESP.get(), this::increment, 1L, 1L);
        if (!task.compareAndSet(null, scheduled)) {
            scheduled.cancel();
            throw new IllegalStateException("Folia ticker was already started");
        }
    }

    private void increment(ScheduledTask scheduledTask) {
        if (task.get() != scheduledTask) {
            return;
        }
        int previous = (int) CURRENT_TICK.getAndAdd(this, 1);
        int newTick = previous + 1;

        entityIDGuard.tick(newTick);
        AsyncEngine engine = RaycastedAntiESP.getEngine();
        if (engine == null || !engine.markTickRunning()) {
            Logger.info("Skipped starting tick because previous tick is still running or shutdown has begun.", 6, EventListener.class);
            return;
        }
        long scheduledNanos = System.nanoTime();
        try {
            Bukkit.getAsyncScheduler().runNow(RaycastedAntiESP.get(), ignored -> engine.tick(newTick, scheduledNanos));
        } catch (RuntimeException exception) {
            engine.cancelPendingTickReservation();
            Logger.error("Failed to schedule engine tick after reserving it. Cleared the pending reservation so future ticks can continue.", exception, 2, EventListener.class);
        }
    }

    @Override
    public int getAsInt() {
        return (int) CURRENT_TICK.getOpaque(this);
    }

    @Override
    public void close() {
        ScheduledTask scheduled = task.getAndSet(null);
        if (scheduled != null) {
            scheduled.cancel();
        }
    }
}
