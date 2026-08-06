/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2026 Cubicake.
 * This file is part of RaycastedAntiESP.
 * RaycastedAntiESP is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License v3.0 only, which can be accessed at https://www.gnu.org/licenses/agpl-3.0.html.
 * See README.md for warranty disclaimer and further information.
 */

package games.cubi.raycastedantiesp.core.engine;

import games.cubi.locatables.api.ImmutableSpatial;
import games.cubi.locatables.api.Locatable;
import games.cubi.logs.Logger;
import games.cubi.raycastedantiesp.core.config.ConfigManager;
import games.cubi.raycastedantiesp.core.config.DebugConfig;
import games.cubi.raycastedantiesp.core.config.raycast.EntityConfig;
import games.cubi.raycastedantiesp.core.config.raycast.PlayerConfig;
import games.cubi.raycastedantiesp.core.config.raycast.TileEntityConfig;
import games.cubi.raycastedantiesp.core.logging.CubiLog;
import games.cubi.raycastedantiesp.core.players.PlayerData;
import games.cubi.raycastedantiesp.core.players.PlayerRegistry;
import games.cubi.raycastedantiesp.core.raycast.ParticleSpawner;
import games.cubi.raycastedantiesp.core.raycast.RaycastUtil;
import games.cubi.raycastedantiesp.core.view.BlockView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntSupplier;

public abstract class AsyncEngine implements Engine {
    private static final long SLOW_TICK_NANOS = 40 * 1_000_000L;
    private static final int SINGLE_THREAD = 1;
    //Literally just magic numbers I made by keyboard mashing
    private static final int TICK_IDLE = 1872;
    private static final int TICK_PENDING = -129;
    private static final int TICK_RUNNING = 34892;

    private final ConfigManager config;
    private final ParticleSpawner particleSpawner;
    private final AsyncVisibilityChecks visibilityChecks;
    private final IntSupplier currentTickSupplier;
    private final AtomicInteger runningTick = new AtomicInteger(-1);
    private final AtomicInteger tickState = new AtomicInteger(TICK_IDLE);
    private final AtomicLong tickNanos = new AtomicLong(0);
    private final AtomicBoolean tickWorkActive = new AtomicBoolean();
    private final AtomicBoolean shutdownRequested = new AtomicBoolean();
    private final Object shutdownMonitor = new Object();
    private final AsyncRunner asyncRunner;
    private final TimingStatsSelector timingStatsSelector = new TimingStatsSelector();

    public AsyncEngine(ConfigManager config, ParticleSpawner particleSpawner, IntSupplier currentTickSupplier, AsyncRunner asyncRunner) {
        this.config = config;
        this.particleSpawner = particleSpawner;
        this.visibilityChecks = new AsyncVisibilityChecks(particleSpawner);
        this.currentTickSupplier = currentTickSupplier;
        this.asyncRunner = asyncRunner;
    }

    /**
     * Reserves the next engine tick before it is handed to the async scheduler.
     *
     * @return true if the caller now owns the only pending tick slot; false if an existing pending
     * or running tick should cover this attempt, or shutdown has begun.
     */
    public boolean markTickRunning() {
        if (shutdownRequested.get()) {
            return false;
        }
        if (tickState.compareAndSet(TICK_IDLE, TICK_PENDING)) {
            if (!shutdownRequested.get()) {
                return true;
            }
            tickState.compareAndSet(TICK_PENDING, TICK_IDLE);
            signalShutdownWaiters();
            return false;
        }
        recordSkippedTick(-1, System.nanoTime());
        return false;
    }

    /**
     * Releases a pending tick reservation when async scheduling fails before {@link #tick(int, long)}
     * can claim it. The operation is idempotent during shutdown.
     */
    public void cancelPendingTickReservation() {
        if (tickState.compareAndSet(TICK_PENDING, TICK_IDLE)) {
            signalShutdownWaiters();
            return;
        }
        if (!shutdownRequested.get() && tickState.get() != TICK_IDLE) {
            Logger.warning("Attempted to cancel a pending tick reservation, but the tick was no longer pending.", 5, AsyncEngine.class);
        }
    }

    /**
     * Stops accepting new ticks and waits a bounded amount of time for accepted worker batches to
     * stop touching shared visibility state.
     *
     * @return true when the engine is quiescent; false when the timeout elapsed and callers must
     * keep shared registries/controllers alive until a later wait succeeds.
     */
    public boolean shutdownAndAwait(long timeout, TimeUnit unit) {
        shutdownRequested.set(true);
        cancelPendingTickReservation();

        long remainingNanos = unit.toNanos(timeout);
        long deadline = System.nanoTime() + remainingNanos;
        synchronized (shutdownMonitor) {
            while (!isQuiescent()) {
                if (remainingNanos <= 0) {
                    return false;
                }
                try {
                    TimeUnit.NANOSECONDS.timedWait(shutdownMonitor, remainingNanos);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return false;
                }
                remainingNanos = deadline - System.nanoTime();
            }
            return true;
        }
    }

    public boolean isShutdownRequested() {
        return shutdownRequested.get();
    }

    public boolean isQuiescent() {
        return !tickWorkActive.get() && tickState.get() != TICK_RUNNING;
    }

    /**
     * Runs the tick which was reserved using {@link #markTickRunning} and, if the engine falls behind the server clock, keeps the
     * same worker on the latest tick instead of yielding back to the scheduler.
     *
     * @param scheduledTick the server tick captured before async handoff.
     * @param scheduledNanos the time captured before async handoff, used to separate queue delay from engine work.
     */
    @Override
    public void tick(int scheduledTick, long scheduledNanos) {
        boolean runTick = true;
        boolean expectPending = true;
        int startTick = currentTickSupplier.getAsInt();
        while (runTick && !shutdownRequested.get()) {
            if (!startTickState(scheduledTick, expectPending)) {
                return;
            }
            if (!dispatchTickSafely(scheduledTick, startTick, scheduledNanos)) {
                return;
            }

            int latestTick = currentTickSupplier.getAsInt();
            if (latestTick > startTick && !shutdownRequested.get()) {
                // If running behind, don't yield the thread, just run the next tick immediately
                Logger.warning("Tick thread completed tick #" + startTick + " after tick #" + latestTick + " had already begun. Starting next tick immediately instead of yielding thread to scheduler. This is probably safe to ignore but may suggest that your server is overloaded.", 5);
                startTick = latestTick;
                scheduledNanos = System.nanoTime();
                scheduledTick = latestTick;

                if (tickState.get() == TICK_RUNNING) {
                    // This means that there is already a tick running, so we should exit
                    Logger.info("Tick finished behind but another thread had already begun ticking the next tick.", 5, AsyncEngine.class);
                    return;
                }
                expectPending = false; //Next call to startTickState should not expect to see status set to pending
            }
            else runTick = false;
        }
    }

    private boolean dispatchTickSafely(int scheduledTick, int startTick, long scheduledNanos) {
        try {
            return dispatchTick(scheduledTick, startTick, scheduledNanos);
        } catch (RuntimeException | Error throwable) {
            finishTickState();
            Logger.error("Engine tick setup failed before worker ownership was established. Released the tick reservation.",
                    throwable, 2, AsyncEngine.class);
            return false;
        }
    }

    /**
     * Claims worker capacity and either runs work immediately or schedules worker batches.
     *
     * @return true if at least one worker submission completed successfully; false if setup was
     * skipped or no worker was accepted.
     */
    @SuppressWarnings("PMD.GuardLogStatement") // CubiLogging performs level filtering inside Logger.
    private boolean dispatchTick(int scheduledTick, int startTick, long scheduledNanos) {
        int threads = Math.max(SINGLE_THREAD,
                config.getEngineConfig().asyncConfig().asyncProcessingThreads());
        DebugConfig debugConfig = config.getDebugConfig();
        TimingStats tickTimingStats = timingStats(debugConfig);
        long startNanos = System.nanoTime();

        if (skipDuplicateTick(scheduledTick, startTick, threads, startNanos, tickTimingStats)) {
            return false;
        }
        if (!claimTickWork(scheduledTick, threads, scheduledNanos, startNanos, tickTimingStats)) {
            return false;
        }
        return dispatchClaimedTick(
                scheduledTick, startTick, scheduledNanos, startNanos, threads, debugConfig, tickTimingStats);
    }

    @SuppressWarnings("PMD.GuardLogStatement") // CubiLogging performs level filtering inside Logger.
    private boolean skipDuplicateTick(
            int scheduledTick,
            int startTick,
            int threads,
            long startNanos,
            TimingStats tickTimingStats) {
        int tickAlreadyRunning = runningTick.get();
        if (scheduledTick != tickAlreadyRunning) {
            return false;
        }
        logAggregateReport(tickTimingStats.recordSkipped(threads, startNanos));
        Logger.info("RaycastedAntiESP is already processing this tick; skipping duplicate same-tick attempt."
                + " scheduledTick=" + scheduledTick
                + " currentServerTick=" + startTick
                + " currentRunningTick=" + tickAlreadyRunning, 6, AsyncEngine.class);
        finishTickState();
        return true;
    }

    @SuppressWarnings("PMD.GuardLogStatement") // CubiLogging performs level filtering inside Logger.
    private boolean claimTickWork(
            int scheduledTick,
            int threads,
            long scheduledNanos,
            long startNanos,
            TimingStats tickTimingStats) {
        if (tickWorkActive.compareAndSet(false, true)) {
            return true;
        }
        long queueNanos = Math.max(0, startNanos - scheduledNanos);
        Logger.warning("RaycastedAntiESP is still ticking from the last tick! Skipping this tick to avoid concurrent modification issues."
                + " scheduledTick=" + scheduledTick
                + " currentServerTick=" + currentTickSupplier.getAsInt()
                + " currentRunningTick=" + runningTick.get()
                + " timeSpentInQueue=" + TickTimingFormatter.formatMillis(queueNanos) + "ms", 5, AsyncEngine.class);
        logAggregateReport(tickTimingStats.recordSkipped(threads, startNanos));
        finishTickState();
        return false;
    }

    private boolean dispatchClaimedTick(
            int scheduledTick,
            int startTick,
            long scheduledNanos,
            long startNanos,
            int threads,
            DebugConfig debugConfig,
            TimingStats tickTimingStats) {
        boolean claimedRunningTick = false;
        try {
            tickNanos.set(startNanos);
            runningTick.set(startTick);
            claimedRunningTick = true;

            Collection<PlayerData> allPlayers = PlayerRegistry.getInstance().getAllPlayerData();
            TickTimings timings = createTickTimings(
                    scheduledTick, scheduledNanos, startTick, startNanos, threads, allPlayers.size(), tickTimingStats);
            List<List<PlayerData>> batches = createPlayerBatches(allPlayers, threads);
            AsyncRunner runner = threads == SINGLE_THREAD ? Runnable::run : asyncRunner;
            return dispatchBatches(batches, runner, debugConfig, startTick, timings, tickTimingStats);
        } catch (RuntimeException | Error throwable) {
            recoverFailedTickSetup(startTick, claimedRunningTick, tickTimingStats);
            Logger.error("An error occurred during tick setup before worker submission. Released engine ownership to avoid deadlock.",
                    throwable, 2, AsyncEngine.class);
            return false;
        }
    }

    private TickTimings createTickTimings(
            int scheduledTick,
            long scheduledNanos,
            int currentTick,
            long startNanos,
            int threads,
            int playerCount,
            TimingStats tickTimingStats) {
        if (tickTimingStats == TimingStatsNoOp.INSTANCE) {
            return null;
        }
        return new TickTimings(
                scheduledTick, scheduledNanos, currentTick, startNanos, threads, playerCount);
    }

    private static List<List<PlayerData>> createPlayerBatches(
            Collection<PlayerData> allPlayers, int threads) {
        if (threads == SINGLE_THREAD) {
            return List.of(new ArrayList<>(allPlayers));
        }
        List<List<PlayerData>> batches = new ArrayList<>(threads);
        for (int index = 0; index < threads; index++) {
            batches.add(new ArrayList<>());
        }
        int index = 0;
        for (PlayerData playerData : allPlayers) {
            batches.get(index++ % threads).add(playerData);
        }
        return batches;
    }

    private boolean dispatchBatches(
            List<List<PlayerData>> batches,
            AsyncRunner runner,
            DebugConfig debugConfig,
            int currentTick,
            TickTimings timings,
            TimingStats tickTimingStats) {
        EntityConfig entityConfig = config.getEntityConfig();
        PlayerConfig playerConfig = config.getPlayerConfig();
        TileEntityConfig tileEntityConfig = config.getTileEntityConfig();
        List<Runnable> tasks = new ArrayList<>(batches.size());
        for (List<PlayerData> batch : batches) {
            tasks.add(() -> {
                if (!shutdownRequested.get()) {
                    subTick(batch, entityConfig, playerConfig, tileEntityConfig,
                            debugConfig, currentTick, timings);
                }
            });
        }
        int submitted = AsyncTickWork.dispatch(
                tasks,
                runner,
                shutdownRequested::get,
                exception -> Logger.error("Failed to submit every worker batch for tick " + currentTick
                        + ". Accepted workers will drain before the tick is finalised; later batches were not submitted.",
                        exception, 2, AsyncEngine.class),
                throwable -> Logger.error("An async engine worker failed while processing tick " + currentTick
                        + ". The worker permit was released so the engine can continue after remaining workers drain.",
                        throwable, 2, AsyncEngine.class),
                () -> completeTick(currentTick, timings, tickTimingStats)
        );
        return submitted > 0;
    }

    private void recoverFailedTickSetup(
            int currentTick, boolean claimedRunningTick, TimingStats tickTimingStats) {
        if (claimedRunningTick) {
            completeTick(currentTick, null, tickTimingStats);
            return;
        }
        releaseTickWork();
        finishTickState();
    }

    /**
     * Processes one worker batch. Tick-wide finalisation is owned by {@link AsyncTickWork} after
     * submissions have closed and every accepted worker permit has been released.
     */
    private void subTick(List<PlayerData> batch, EntityConfig entityConfig, PlayerConfig playerConfig,
                         TileEntityConfig tileEntityConfig, DebugConfig debugConfig, int currentTick,
                         TickTimings timings) {
        TickTimingBatch batchTimings = timings == null ? TickTimingBatchNoOp.INSTANCE : new TickTimingBatch();
        long batchStartNanos = batchTimings.startBatch();
        try {
            processTickForPlayers(batch, entityConfig, playerConfig, tileEntityConfig,
                    debugConfig.showDebugParticles(), currentTick, batchTimings);
        } finally {
            if (timings != null) {
                timings.recordBatch(batchTimings, batchTimings.elapsedSince(batchStartNanos));
            }
        }
    }

    private void completeTick(int currentTick, TickTimings timings, TimingStats timingStats) {
        try {
            long completionNanos = System.nanoTime();
            if (timings != null) {
                TickTimingSnapshot snapshot = timings.snapshot(currentTickSupplier.getAsInt(), completionNanos);
                String aggregateReport = timingStats.recordCompleted(snapshot, completionNanos);
                if (snapshot.wallNanos() > SLOW_TICK_NANOS) {
                    CubiLog.recordWarning(snapshot.toSlowTickMessage(), 5, AsyncEngine.class);
                }
                logAggregateReport(aggregateReport);
            } else {
                long elapsedNanos = completionNanos - tickNanos.get();
                if (elapsedNanos > SLOW_TICK_NANOS) {
                    CubiLog.recordWarning("Tick completed in " + (elapsedNanos / 1_000_000.0) + " ms. If you see this warning frequently, consider reducing the raycasting load by adjusting the configuration.", 5, AsyncEngine.class);
                }
            }
        } finally {
            finaliseTick(currentTick);
            releaseTickWork();
        }
    }

    private void releaseTickWork() {
        if (!tickWorkActive.compareAndSet(true, false)) {
            Logger.error("Async engine tick ownership was released more than once.", 2, AsyncEngine.class);
        }
        signalShutdownWaiters();
    }

    private void signalShutdownWaiters() {
        synchronized (shutdownMonitor) {
            shutdownMonitor.notifyAll();
        }
    }

    /**
     * Moves the reserved tick into the running state. Catch-up iterations may start from idle
     * because they are created by the current worker instead of by the server tick event.
     *
     * @return true if this thread now owns the running state; false if another pending or running
     * tick should take precedence.
     */
    private boolean startTickState(int scheduledTick, boolean expectPending) {
        if (shutdownRequested.get()) {
            cancelPendingTickReservation();
            return false;
        }
        if (expectPending) {
            if (tickState.compareAndSet(TICK_PENDING, TICK_RUNNING)) {
                return true;
            }
            if (tickState.compareAndSet(TICK_IDLE, TICK_RUNNING)) {
                // This means that this tick was dispatched but not marked as such, or was unmarked at some point.
                Logger.warning("Tick " + scheduledTick + " was dispatched but not marked as pending. This suggests a race condition. Please report this on our GitHub or Discord.", 5, AsyncEngine.class);
                return true;
            }
        } else if (tickState.compareAndSet(TICK_IDLE, TICK_RUNNING)) {
            return true;
        }

        // This means there is already a tick pending or running, so we should exit.
        recordSkippedTick(-1, System.nanoTime());
        Logger.info("Tick " + scheduledTick + " is pending but another tick is already pending or running. Exiting now.", 6, AsyncEngine.class);
        return false;
    }

    /**
     * Releases ownership for the tick that completed, without clearing a newer running tick that
     * may have already taken over.
     */
    private void finaliseTick(int currentTick) {
        runningTick.compareAndSet(currentTick, -1);
        finishTickState();
    }

    /**
     * Returns the reservation state to idle after the active tick has stopped touching shared player visibility data.
     */
    private void finishTickState() {
        if (!tickState.compareAndSet(TICK_RUNNING, TICK_IDLE)) {
            // This should never happen as it means the tick was marked as completed before processing was completed
            Logger.warning("tickState was not running when completing tick! This should never happen. Please report this on our GitHub or Discord.", 5, AsyncEngine.class);
        }
        signalShutdownWaiters();
    }

    private void processTickForPlayers(
            List<PlayerData> playerDataList,
            EntityConfig entityConfig,
            PlayerConfig playerConfig,
            TileEntityConfig tileEntityConfig,
            boolean debugParticles,
            int currentTick,
            TickTimingBatch timings) {
        for (PlayerData playerData : playerDataList) {
            if (shutdownRequested.get()) {
                return;
            }
            processTickForPlayer(
                    playerData, entityConfig, playerConfig, tileEntityConfig,
                    debugParticles, currentTick, timings);
        }
    }

    private void processTickForPlayer(
            PlayerData playerData,
            EntityConfig entityConfig,
            PlayerConfig playerConfig,
            TileEntityConfig tileEntityConfig,
            boolean debugParticles,
            int currentTick,
            TickTimingBatch timings) {
        if (!playerData.isConnected()) {
            return;
        }
        playerData.nettyData().markPendingPostSpawnTasksForEviction();
        if (playerData.hasBypassPermission()) {
            timings.incrementBypassSkippedPlayers();
            return;
        }

        Locatable playerLocation = playerData.ownLocation();
        if (playerLocation == null || playerLocation.world() == null) {
            timings.incrementNullLocationSkippedPlayers();
            return;
        }
        int worldEpoch = playerData.tryAcquireWorldEpochFor(playerLocation.world());
        if (worldEpoch == PlayerData.INVALID_WORLD_EPOCH) {
            if (tileEntityConfig.enabled()) {
                timings.incrementTileWorldSkipped();
            }
            return;
        }

        timings.incrementProcessedPlayers();
        BlockView blockView = playerData.blockView();
        try {
            visibilityChecks.processEntitySection(
                    playerData, playerLocation, blockView, entityConfig,
                    debugParticles, currentTick, worldEpoch, timings);
            visibilityChecks.processPlayerSection(
                    playerData, playerLocation, blockView, playerConfig,
                    debugParticles, currentTick, worldEpoch, timings);
            processTileSection(
                    playerData, playerLocation, blockView, tileEntityConfig,
                    debugParticles, currentTick, worldEpoch, timings);
        } finally {
            flushPlayerTransitions(playerData, blockView);
        }
    }

    private void processTileSection(
            PlayerData playerData,
            Locatable playerLocation,
            BlockView blockView,
            TileEntityConfig tileEntityConfig,
            boolean debugParticles,
            int currentTick,
            int worldEpoch,
            TickTimingBatch timings) {
        if (!tileEntityConfig.enabled()) {
            return;
        }
        long sectionStartNanos = timings.startTileSection();
        checkTileEntities(playerData, playerLocation, tileEntityConfig, debugParticles,
                blockView, currentTick, worldEpoch, timings);
        timings.finishTileSection(sectionStartNanos);
    }

    private static void flushPlayerTransitions(PlayerData playerData, BlockView blockView) {
        playerData.entityView().flushPendingTransitions();
        playerData.playerView().flushPendingTransitions();
        blockView.flushPendingTransitions();
    }

    private void checkTileEntities(PlayerData player, Locatable playerLocation, TileEntityConfig tileEntityConfig, boolean debugParticles, BlockView blockView, int currentTick, int worldEpoch, TickTimingBatch timings) {
        long modeToken = blockView.tileEntityCheckModeToken();
        int checked = blockView.updateVisibilityForEachNeedingRecheck(tileEntityConfig.getVisibleRecheckIntervalTicks(), currentTick, modeToken, worldEpoch, tileEntityLocation -> {

            if (playerLocation.distanceSquared(tileEntityLocation) > (double) tileEntityConfig.getRaycastRadius() * tileEntityConfig.getRaycastRadius()) {
                timings.incrementTileRadiusSkipped();
                return BlockView.VisibilityResolver.HIDE;
            }
            timings.incrementTileRaycasts();
            boolean canSee = RaycastUtil.raycast(playerLocation, tileEntityLocation, tileEntityConfig.getMaxOccludingCount() + 1, tileEntityConfig.getAlwaysShowRadius(), tileEntityConfig.getRaycastRadius(), debugParticles, blockView, 1, particleSpawner);
            return canSee ? BlockView.VisibilityResolver.SHOW : BlockView.VisibilityResolver.HIDE;
        });
        timings.addTileChecked(checked);
    }

    private static void logAggregateReport(String aggregateReport) {
        if (aggregateReport != null) {
            Logger.info(aggregateReport, 4, AsyncEngine.class);
        }
    }

    /**
     * Counts a skipped tick against the currently configured timing sink.
     */
    private void recordSkippedTick(int threads, long nowNanos) {
        logAggregateReport(timingStats().recordSkipped(threads, nowNanos));
    }

    /**
     * Selects the timing sink from the current debug config.
     *
     * @return a collecting {@link TimingStats} while timing diagnostics are enabled, otherwise the
     * no-op singleton.
     */
    private TimingStats timingStats() {
        return timingStats(config.getDebugConfig());
    }

    /**
     * Keeps disabled timing diagnostics allocation-free and starts a fresh collection window when
     * diagnostics are re-enabled.
     *
     * @return the timing sink that should be used for a newly starting tick or skip record.
     */
    private TimingStats timingStats(DebugConfig debugConfig) {
        return timingStatsSelector.select(debugConfig.recordTimings());
    }
}
