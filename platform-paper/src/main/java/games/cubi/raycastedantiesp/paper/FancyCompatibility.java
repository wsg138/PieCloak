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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class FancyCompatibility implements AutoCloseable {
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

    private final List<AutoCloseable> integrations = new ArrayList<>();

    @SuppressWarnings("PMD.UseProperClassLoader") // Bukkit requires this plugin's defining loader, not a container context loader.
    FancyCompatibility() {
        this(pluginName -> Bukkit.getPluginManager().isPluginEnabled(pluginName),
                FancyCompatibility.class.getClassLoader());
    }

    FancyCompatibility(Predicate<String> pluginEnabled, ClassLoader classLoader) {
        Objects.requireNonNull(pluginEnabled, "pluginEnabled");
        Objects.requireNonNull(classLoader, "classLoader");

        startIfEnabled(Integration.FANCY_NPCS, pluginEnabled, classLoader);
        startIfEnabled(Integration.FANCY_HOLOGRAMS, pluginEnabled, classLoader);
    }

    private void startIfEnabled(
            Integration integration, Predicate<String> pluginEnabled, ClassLoader classLoader) {
        loadIfEnabled(integration, pluginEnabled, classLoader, integrations::add);
    }

    @SuppressWarnings("PMD.GuardLogStatement") // CubiLogging performs its own level filtering.
    private static void loadIfEnabled(
            Integration integration,
            Predicate<String> pluginEnabled,
            ClassLoader classLoader,
            Consumer<AutoCloseable> integrationOwner) {
        Objects.requireNonNull(integrationOwner, "integrationOwner");
        if (!pluginEnabled.test(integration.pluginName)) {
            return;
        }

        try {
            // nosemgrep: java.lang.security.audit.unsafe-reflection.unsafe-reflection -- class name is selected only from the private enum above.
            Class<?> integrationClass = Class.forName(integration.implementationClass, true, classLoader);
            Object instance = integrationClass.getDeclaredConstructor().newInstance();
            if (!(instance instanceof AutoCloseable closeable)) {
                throw new IllegalStateException(
                        "Optional integration does not implement AutoCloseable: " + integration.implementationClass);
            }
            integrationOwner.accept(closeable);
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
        Throwable failure = null;
        for (int index = integrations.size() - 1; index >= 0; index--) {
            try {
                integrations.get(index).close();
            } catch (Exception | LinkageError throwable) {
                if (failure == null) {
                    failure = throwable;
                } else {
                    failure.addSuppressed(throwable);
                }
            }
        }
        integrations.clear();

        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (failure instanceof LinkageError linkageError) {
            throw linkageError;
        }
        if (failure != null) {
            throw new IllegalStateException("Unable to close one or more optional compatibility layers", failure);
        }
    }
}
