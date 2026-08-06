/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2026 Cubicake.
 * This file is part of RaycastedAntiESP.
 * RaycastedAntiESP is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License v3.0 only, which can be accessed at https://www.gnu.org/licenses/agpl-3.0.html.
 * See README.md for warranty disclaimer and further information.
 */

package games.cubi.raycastedantiesp.paper;

import games.cubi.logs.Logger;
import games.cubi.raycastedantiesp.core.Core;
import games.cubi.raycastedantiesp.core.Ticker;
import games.cubi.raycastedantiesp.core.config.ConfigManager;
import games.cubi.raycastedantiesp.core.config.TargetFilterConfig;
import games.cubi.raycastedantiesp.core.config.raycast.EntityTypeExclusions;
import games.cubi.raycastedantiesp.core.entity.EntityBypassRegistry;
import games.cubi.raycastedantiesp.core.lifecycle.LifecycleScope;
import games.cubi.raycastedantiesp.core.players.PlayerRegistry;
import games.cubi.raycastedantiesp.core.view.ViewRegistry;
import games.cubi.raycastedantiesp.packetevents.config.PacketEventsBlockProcessorConfig;
import games.cubi.raycastedantiesp.packetevents.view.PacketEventsBlockView;
import games.cubi.raycastedantiesp.packetevents.view.PacketEventsEntityView;
import games.cubi.raycastedantiesp.packetevents.viewcontrollers.PacketEventsCommonViewController;
import games.cubi.raycastedantiesp.paper.bStats.MetricsCollector;
import games.cubi.raycastedantiesp.paper.commands.Attribution;
import games.cubi.raycastedantiesp.paper.commands.AttributionBrigadier;
import games.cubi.raycastedantiesp.paper.commands.RaycastedAntiESPCommandBrigadier;
import games.cubi.raycastedantiesp.paper.commands.SourceCommandBrigadier;
import games.cubi.raycastedantiesp.paper.config.PaperEntityTypeExclusionResolver;
import games.cubi.raycastedantiesp.paper.engine.PaperAsyncEngine;
import games.cubi.raycastedantiesp.paper.packets.PacketEventsPaperBlockInfoResolver;
import games.cubi.raycastedantiesp.paper.packets.PaperPacketEventsBlockViewController;
import games.cubi.raycastedantiesp.paper.packets.PaperPacketEventsCommonViewController;
import games.cubi.raycastedantiesp.paper.packets.PaperPacketEventsEntityViewController;
import games.cubi.raycastedantiesp.paper.target.PaperTargetFilterService;
import games.cubi.raycastedantiesp.paper.target.TargetFilteringBlockInfoResolver;
import games.cubi.raycastedantiesp.paper.utils.FoliaTicker;
import games.cubi.raycastedantiesp.paper.utils.PaperTicker;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntSupplier;

public final class RaycastedAntiESP extends JavaPlugin implements CommandExecutor {
    private static final long ENGINE_SHUTDOWN_TIMEOUT_SECONDS = 5;

    private static ConfigManager config;
    private static PaperPacketEventsCommonViewController commonController;
    private static PaperPacketEventsEntityViewController packetEventsController;
    private static PaperAsyncEngine engine;
    private static MetricsCollector metricsCollector;
    private static RaycastedAntiESP instance;
    private static PaperLoggerAdapter loggerAdapter;
    private static PaperTargetFilterService targetFilter;
    private static IntSupplier currentTickSupplier;
    private static LifecycleScope activeLifecycle;
    private static volatile boolean reenableBlocked;

    private boolean commandsRegistered;

    public static final boolean isFolia = getClass("io.papermc.paper.threadedregions.RegionizedServer") != null;

    {
        instance = this;
        loggerAdapter = new PaperLoggerAdapter(getLogger(), getDataPath().resolve("logs/" + System.currentTimeMillis() + ".log"));
        Core.initialize(loggerAdapter);
    }

    public static @Nullable Class<?> getClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException exception) {
            return null;
        }
    }

    @Override
    public void onLoad() {
        instance = this;
        Core.initialize(loggerAdapter);
        initialiseConfigIfNeeded();
        Plugin packetEvents = Bukkit.getPluginManager().getPlugin("packetevents");
        if (packetEvents == null) {
            throw new IllegalStateException("PacketEvents is required but was not found.");
        }
        Logger.info("PacketEvents detected.", 5);
    }

    @Override
    @SuppressWarnings("PMD.NullAssignment") // Startup rollback deliberately clears the lifecycle ownership sentinel.
    public void onEnable() {
        instance = this;
        Core.initialize(loggerAdapter);
        finishPriorShutdownOrThrow();
        initialiseConfigIfNeeded();

        LifecycleScope startup = new LifecycleScope();
        AtomicBoolean engineDrained = new AtomicBoolean(true);
        AtomicBoolean teardownSafe = new AtomicBoolean(true);
        startup.onClose(() -> {
            if (engineDrained.get() && teardownSafe.get()) {
                resetSharedState();
            } else {
                reenableBlocked = true;
                Logger.error("Shutdown could not prove that workers and listener registrations were fully drained. Shared state remains fenced and same-JVM re-enable is blocked.", 1, RaycastedAntiESP.class);
            }
        });

        try {
            PaperEntityTypeExclusionResolver.resolveAndInitialise(config.getEntityConfig().excludedTypes());
            targetFilter = new PaperTargetFilterService(config);
            PacketEventsPaperBlockInfoResolver upstreamBlockInfoResolver = new PacketEventsPaperBlockInfoResolver();
            TargetFilteringBlockInfoResolver blockInfoResolver = new TargetFilteringBlockInfoResolver(upstreamBlockInfoResolver, targetFilter);
            boolean trackAllBlocks = config.getBlockProcessorConfig().trackAllBlocks();
            ViewRegistry.initialise(
                    worldEpoch -> new PacketEventsBlockView(blockInfoResolver, trackAllBlocks, worldEpoch),
                    PacketEventsEntityView::createEntityView,
                    PacketEventsEntityView::createPlayerView
            );

            Ticker ticker = createTicker();
            try {
                currentTickSupplier = ticker;
                engine = new PaperAsyncEngine(this, config, currentTickSupplier);
                PaperAsyncEngine engineForShutdown = engine;
                startup.onClose(() -> {
                    engineDrained.set(false);
                    if (engineForShutdown.shutdownAndAwait(ENGINE_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                        engineDrained.set(true);
                    }
                });
                ownCritical(startup, teardownSafe, ticker);
            } catch (RuntimeException | Error throwable) {
                try {
                    ticker.close();
                } catch (Exception | Error cleanupFailure) {
                    throwable.addSuppressed(cleanupFailure);
                }
                throw throwable;
            }

            commonController = ownCritical(startup, teardownSafe,
                    new PaperPacketEventsCommonViewController(currentTickSupplier));
            PacketEventsCommonViewController.initialise(commonController);

            packetEventsController = ownCritical(startup, teardownSafe,
                    PaperPacketEventsEntityViewController.create(
                            currentTickSupplier, targetFilter, () -> teardownSafe.set(false)));
            ownCritical(startup, teardownSafe,
                    new PaperPacketEventsBlockViewController(blockInfoResolver, trackAllBlocks, currentTickSupplier));
            ownCritical(startup, teardownSafe, EventListener.initialise(this, currentTickSupplier));

            UpdateChecker.checkForUpdates(this, Bukkit.getConsoleSender());
            registerCommandsOnce();

            metricsCollector = new MetricsCollector(this, config);
            MetricsCollector metricsForShutdown = metricsCollector;
            startup.onClose(metricsForShutdown::shutdown);
            ownCritical(startup, teardownSafe, new FancyCompatibility());

            activeLifecycle = startup;
            ticker.start();

            /*Do not delete, this is a legal notice*/Attribution.sendAttributionMessage(Bukkit.getConsoleSender());
        } catch (RuntimeException | Error throwable) {
            activeLifecycle = null;
            try {
                startup.close();
            } catch (RuntimeException cleanupFailure) {
                throwable.addSuppressed(cleanupFailure);
            }
            throw throwable;
        }
    }

    @Override
    @SuppressWarnings("PMD.NullAssignment") // Transfers and clears lifecycle ownership so same-JVM re-enable cannot reuse closed resources.
    public void onDisable() {
        LifecycleScope lifecycle = activeLifecycle;
        activeLifecycle = null;
        if (lifecycle != null) {
            try {
                lifecycle.close();
            } catch (RuntimeException exception) {
                Logger.error("One or more plugin shutdown actions failed.", exception, 1, RaycastedAntiESP.class);
            }
        } else if (!reenableBlocked && engine != null && engine.isShutdownRequested() && engine.isQuiescent()) {
            resetSharedState();
        }

        if (loggerAdapter != null) {
            loggerAdapter.forceFlushToFileNow();
        }
    }

    private Ticker createTicker() {
        if (isFolia) {
            return new FoliaTicker();
        }
        PaperTicker paperTicker = new PaperTicker();
        paperTicker.register();
        return paperTicker;
    }

    private void registerCommandsOnce() {
        if (commandsRegistered) {
            return;
        }
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS.newHandler(event -> {
            RaycastedAntiESPCommandBrigadier.register(event.registrar());
            AttributionBrigadier.register(event.registrar());
            SourceCommandBrigadier.register(event.registrar());
        }));
        commandsRegistered = true;
    }

    private void finishPriorShutdownOrThrow() {
        if (reenableBlocked) {
            throw new IllegalStateException("Previous shutdown could not unregister every listener/controller; restart the server before re-enabling RaycastedAntiESP");
        }
        PaperAsyncEngine previous = engine;
        if (previous == null) {
            return;
        }
        if (!previous.isShutdownRequested()) {
            throw new IllegalStateException("RaycastedAntiESP is already enabled");
        }
        if (!previous.shutdownAndAwait(ENGINE_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Previous RaycastedAntiESP engine workers are still active; refusing to reuse shared state");
        }
        resetSharedState();
    }

    private static <T extends AutoCloseable> T ownCritical(
            LifecycleScope scope, AtomicBoolean teardownSafe, T resource) {
        scope.onClose(() -> {
            try {
                resource.close();
            } catch (Exception exception) {
                teardownSafe.set(false);
                throw exception;
            } catch (Error error) {
                teardownSafe.set(false);
                throw error;
            }
        });
        return resource;
    }

    private void initialiseConfigIfNeeded() {
        if (config == null) {
            config = ConfigManager.initialiseConfigManager(
                    () -> getResource("config.yml"),
                    getDataFolder().toPath(),
                    List.of(PacketEventsBlockProcessorConfig.EXTENSION, TargetFilterConfig.EXTENSION)
            );
        }
    }

    @SuppressWarnings("PMD.NullAssignment") // Re-enable safety requires releasing every lifecycle-owned static reference.
    private static void resetSharedState() {
        PacketEventsCommonViewController.reset(commonController);
        PlayerRegistry.getInstance().clear();
        EntityBypassRegistry.reset();
        ViewRegistry.reset();
        EntityTypeExclusions.reset();

        commonController = null;
        packetEventsController = null;
        targetFilter = null;
        metricsCollector = null;
        currentTickSupplier = null;
        engine = null;
        reenableBlocked = false;
    }

    public static ConfigManager getConfigManager() {
        return config;
    }

    public static PaperPacketEventsEntityViewController getPacketEventsController() {
        return packetEventsController;
    }

    public static PaperAsyncEngine getEngine() {
        return engine;
    }

    public static RaycastedAntiESP get() {
        return instance;
    }

    public static PaperTargetFilterService getTargetFilter() {
        return targetFilter;
    }

    public static int getCurrentTick() {
        return currentTickSupplier == null ? 0 : currentTickSupplier.getAsInt();
    }
}
