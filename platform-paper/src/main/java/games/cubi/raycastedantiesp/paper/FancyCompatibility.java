/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2026 Cubicake.
 * This file is part of RaycastedAntiESP.
 * RaycastedAntiESP is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License v3.0 only, which can be accessed at https://www.gnu.org/licenses/agpl-3.0.html.
 * See README.md for warranty disclaimer and further information.
 */

package games.cubi.raycastedantiesp.paper;

import games.cubi.logs.Logger;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class FancyCompatibility implements AutoCloseable, Listener {
    static final String FANCY_NPCS_PLUGIN = "FancyNpcs";
    static final String FANCY_HOLOGRAMS_PLUGIN = "FancyHolograms";

    private enum Integration {
        FANCY_NPCS(FANCY_NPCS_PLUGIN, "games.cubi.raycastedantiesp.paper.FancyNpcsCompatibility"),
        FANCY_HOLOGRAMS(FANCY_HOLOGRAMS_PLUGIN, "games.cubi.raycastedantiesp.paper.FancyHologramsCompatibility");

        private final String pluginName;
        private final String implementationClass;

        Integration(String pluginName, String implementationClass) {
            this.pluginName = pluginName;
            this.implementationClass = implementationClass;
        }
    }

    @FunctionalInterface
    interface IntegrationFactory {
        AutoCloseable create(String className, ClassLoader loader)
                throws ReflectiveOperationException;
    }

    private static AutoCloseable loadReflectively(String name, ClassLoader loader)
            throws ReflectiveOperationException {
        // Class names come only from the fixed internal integration enum.
        Object obj = Class.forName(name, true, loader).getDeclaredConstructor().newInstance();
        if (!(obj instanceof AutoCloseable result)) {
            throw new IllegalStateException("Integration is not AutoCloseable: " + name);
        }
        return result;
    }

    private final IntegrationFactory factory;
    private final List<AutoCloseable> integrations = new ArrayList<>();
    private final EnumSet<Integration> startedIntegrations = EnumSet.noneOf(Integration.class);
    private final EnumSet<Integration> blockedIntegrations = EnumSet.noneOf(Integration.class);
    private final EnumMap<Integration, AutoCloseable> activeIntegrations = new EnumMap<>(Integration.class);
    private Throwable integrationFailure;
    private final Predicate<String> isPluginEnabled;
    private final ClassLoader integrationClassLoader;
    private boolean listenerRegistered;
    private boolean closed;

    @SuppressWarnings("PMD.UseProperClassLoader") // Bukkit requires this plugin's defining loader, not a container context loader.
    FancyCompatibility() {
        this(pluginName -> Bukkit.getPluginManager().isPluginEnabled(pluginName),
                FancyCompatibility.class.getClassLoader(), true, FancyCompatibility::loadReflectively);
    }

    FancyCompatibility(Predicate<String> pluginEnabled, ClassLoader classLoader) {
        this(pluginEnabled, classLoader, false, FancyCompatibility::loadReflectively);
    }

    FancyCompatibility(Predicate<String> pluginEnabled, ClassLoader classLoader, IntegrationFactory factory) {
        this(pluginEnabled, classLoader, false, factory);
    }

    private FancyCompatibility(Predicate<String> pluginEnabled, ClassLoader classLoader,
                               boolean registerForLateEnable, IntegrationFactory factory) {
        isPluginEnabled = Objects.requireNonNull(pluginEnabled, "pluginEnabled");
        integrationClassLoader = Objects.requireNonNull(classLoader, "classLoader");
        this.factory = Objects.requireNonNull(factory, "factory");
        try {
            // Register BEFORE reading already-enabled state; plugin enable callbacks
            // cannot be lost during construction. No optional Fancy API is touched.
            if (registerForLateEnable) {
                registerWithRollback(
                        () -> Bukkit.getPluginManager().registerEvents(this, RaycastedAntiESP.get()),
                        () -> HandlerList.unregisterAll(this));
                listenerRegistered = true;
            }
            startIfEnabled(Integration.FANCY_NPCS);
            startIfEnabled(Integration.FANCY_HOLOGRAMS);
        } catch (RuntimeException | Error failure) {
            try {
                close();
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    // A plugin manager may partially register handlers before it throws. In that case,
    // clean up this listener even though registration never reported success.
    static void registerWithRollback(Runnable register, Runnable unregister) {
        try {
            register.run();
        } catch (RuntimeException | Error failure) {
            try {
                unregister.run();
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginEnable(PluginEnableEvent event) {
        if (!closed && event.getPlugin().isEnabled()) {
            onPluginEnabledName(event.getPlugin().getName());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        if (!closed) {
            onPluginDisabledName(event.getPlugin().getName());
        }
    }

    // Called while the optional plugin is still loaded, before its classloader can go away.
    @SuppressWarnings("PMD.GuardLogStatement") // CubiLogging does its own filtering.
    void onPluginDisabledName(String pluginName) {
        if (closed) {
            return;
        }
        for (Integration integration : Integration.values()) {
            if (!integration.pluginName.equals(pluginName)) {
                continue;
            }
            AutoCloseable layer = activeIntegrations.get(integration);
            if (layer == null) {
                return;
            }
            try {
                layer.close();
            } catch (Exception | Error failure) {
                // Retain failed layers and fence same-JVM reload if teardown was unsafe.
                blockedIntegrations.add(integration);
                if (integrationFailure == null) {
                    integrationFailure = failure;
                } else if (integrationFailure != failure) {
                    integrationFailure.addSuppressed(failure);
                }
                Logger.error("Failed to disable optional " + pluginName
                        + " compatibility; same-JVM reload is blocked.", failure, 2, FancyCompatibility.class);
                return;
            }
            activeIntegrations.remove(integration);
            integrations.remove(layer);
            startedIntegrations.remove(integration);
        }
    }
    void onPluginEnabledName(String pluginName) {
        if (closed) {
            return;
        }
        for (Integration integration : Integration.values()) {
            if (integration.pluginName.equals(pluginName)) {
                startIfEnabled(integration);
            }
        }
    }

    private void startIfEnabled(Integration integration) {
        if (closed || startedIntegrations.contains(integration) || blockedIntegrations.contains(integration)) {
            return;
        }
        int countBefore = integrations.size();
        loadIfEnabled(integration, isPluginEnabled, integrationClassLoader, factory, layer -> {
            integrations.add(layer);
            activeIntegrations.put(integration, layer);
        });
        if (integrations.size() != countBefore) {
            startedIntegrations.add(integration);
        }
    }

    @SuppressWarnings("PMD.GuardLogStatement") // CubiLogging performs its own level filtering.
    private static void loadIfEnabled(
            Integration integration,
            Predicate<String> pluginEnabled,
            ClassLoader classLoader,
            IntegrationFactory factory,
            Consumer<AutoCloseable> integrationOwner) {
        Objects.requireNonNull(integrationOwner, "integrationOwner");
        if (!pluginEnabled.test(integration.pluginName)) {
            return;
        }

        try {
            integrationOwner.accept(factory.create(integration.implementationClass, classLoader));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException throwable) {
            Logger.error(
                    "Unable to initialize optional " + integration.pluginName
                            + " compatibility. PieCloak will continue without it.",
                    throwable,
                    2,
                    FancyCompatibility.class
            );
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        Throwable failure = integrationFailure;
        if (listenerRegistered) {
            listenerRegistered = false;
            try {
                HandlerList.unregisterAll(this);
            } catch (RuntimeException | Error throwable) {
                // Keep closing integrations even when Bukkit listener removal fails.
                failure = throwable;
            }
        }
        for (int index = integrations.size() - 1; index >= 0; index--) {
            try {
                integrations.get(index).close();
            } catch (Exception | Error throwable) {
                if (failure == null) {
                    failure = throwable;
                } else if (failure != throwable) {
                    failure.addSuppressed(throwable);
                }
            }
        }
        integrations.clear();
        activeIntegrations.clear();
        startedIntegrations.clear();

        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        if (failure != null) {
            throw new IllegalStateException("Unable to close one or more optional compatibility layers", failure);
        }
    }
}
