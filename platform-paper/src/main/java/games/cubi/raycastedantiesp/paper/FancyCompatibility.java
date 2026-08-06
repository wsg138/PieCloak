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
import java.util.function.Predicate;

public final class FancyCompatibility implements AutoCloseable {
    static final String FANCY_NPCS_PLUGIN = "FancyNpcs";
    static final String FANCY_HOLOGRAMS_PLUGIN = "FancyHolograms";

    private static final String FANCY_NPCS_CLASS = "games.cubi.raycastedantiesp.paper.FancyNpcsCompatibility";
    private static final String FANCY_HOLOGRAMS_CLASS = "games.cubi.raycastedantiesp.paper.FancyHologramsCompatibility";

    private final List<AutoCloseable> integrations = new ArrayList<>();

    FancyCompatibility() {
        this(pluginName -> Bukkit.getPluginManager().isPluginEnabled(pluginName), FancyCompatibility.class.getClassLoader());
    }

    FancyCompatibility(Predicate<String> pluginEnabled, ClassLoader classLoader) {
        Objects.requireNonNull(pluginEnabled, "pluginEnabled");
        Objects.requireNonNull(classLoader, "classLoader");

        startIfEnabled(FANCY_NPCS_PLUGIN, FANCY_NPCS_CLASS, pluginEnabled, classLoader);
        startIfEnabled(FANCY_HOLOGRAMS_PLUGIN, FANCY_HOLOGRAMS_CLASS, pluginEnabled, classLoader);
    }

    private void startIfEnabled(String pluginName, String integrationClassName, Predicate<String> pluginEnabled, ClassLoader classLoader) {
        AutoCloseable integration = loadIfEnabled(pluginName, integrationClassName, pluginEnabled, classLoader);
        if (integration != null) {
            integrations.add(integration);
        }
    }

    static AutoCloseable loadIfEnabled(
            String pluginName,
            String integrationClassName,
            Predicate<String> pluginEnabled,
            ClassLoader classLoader) {
        if (!pluginEnabled.test(pluginName)) {
            return null;
        }

        try {
            Class<?> integrationClass = Class.forName(integrationClassName, true, classLoader);
            Object integration = integrationClass.getDeclaredConstructor().newInstance();
            if (!(integration instanceof AutoCloseable closeable)) {
                throw new IllegalStateException("Optional integration does not implement AutoCloseable: " + integrationClassName);
            }
            Logger.info(pluginName + " compatibility enabled.", 5);
            return closeable;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException throwable) {
            Logger.error(
                    "Unable to initialize optional " + pluginName + " compatibility. PieCloak will continue without it.",
                    throwable,
                    2,
                    FancyCompatibility.class
            );
            return null;
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
