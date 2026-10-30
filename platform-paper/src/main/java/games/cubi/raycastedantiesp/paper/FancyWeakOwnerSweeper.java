/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright Ac 2026 Cubicake.
 * This file is part of RaycastedAntiESP.
 * RaycastedAntiESP is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License v3.0 only.
 */

package games.cubi.raycastedantiesp.paper;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;

import java.util.Objects;

/**
 * Polls weak owner references on Paper and Folia's global-region scheduler.
 * The owning Fancy integration must cancel the task before closing its scope.
 */
final class FancyWeakOwnerSweeper implements AutoCloseable {
    private static final long SWEEP_PERIOD_TICKS = 20L * 30L;
    private final ScheduledTask scheduledTask;

    FancyWeakOwnerSweeper(WeakIdentityRetirementGuard<?> guard) {
        Objects.requireNonNull(guard, "guard");
        scheduledTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                RaycastedAntiESP.get(), ignored -> guard.sweepCollected(),
                SWEEP_PERIOD_TICKS, SWEEP_PERIOD_TICKS);
    }

    @Override
    public void close() {
        scheduledTask.cancel();
    }
}