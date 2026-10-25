/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2026 Cubicake.
 * This file is part of RaycastedAntiESP.
 * RaycastedAntiESP is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License v3.0 only, which can be accessed at https://www.gnu.org/licenses/agpl-3.0.html.
 * See README.md for warranty disclaimer and further information.
 */

package games.cubi.raycastedantiesp.paper;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FancyCompatibilityTest {
    @Test
    void lateEnabledIntegrationsStartOnceAndCloseInReverseOrder() {
        AtomicBoolean npc = new AtomicBoolean();
        AtomicBoolean holo = new AtomicBoolean();
        List<String> calls = new ArrayList<>();
        FancyCompatibility compat = new FancyCompatibility(
            plugin -> plugin.equals("FancyNpcs") ? npc.get() : holo.get(),
            getClass().getClassLoader(),
            (name, loader) -> {
                String type=name.contains("Npcs") ? "npc" : "holo";
                calls.add("start-"+type);
                return () -> calls.add("close-"+type);
            });
        assertEquals(List.of(), calls);
        npc.set(true);
        compat.onPluginEnabledName("FancyNpcs");
        compat.onPluginEnabledName("FancyNpcs");
        holo.set(true);
        compat.onPluginEnabledName("FancyHolograms");
        compat.onPluginEnabledName("FancyHolograms");
        compat.close();
        compat.close();
        assertEquals(List.of("start-npc","start-holo","close-holo","close-npc"), calls);
    }

    @Test
    void pluginAlreadyEnabledAtConstructionStartsImmediately() {
        AtomicBoolean holo = new AtomicBoolean(false);
        List<String> calls = new ArrayList<>();
        FancyCompatibility compat = new FancyCompatibility(
            plugin -> plugin.equals("FancyNpcs") || holo.get(),
            getClass().getClassLoader(),
            (name, loader) -> {
                calls.add(name.contains("Npcs") ? "npc" : "holo");
                return () -> {};
            });
        assertEquals(List.of("npc"), calls);
        holo.set(true);
        compat.onPluginEnabledName("FancyHolograms");
        assertEquals(List.of("npc","holo"), calls);
        compat.close();
    }

    @Test
    void afterCloseLateNotificationsDoNotStartAnything() {
        AtomicBoolean enabled = new AtomicBoolean();
        List<String> calls = new ArrayList<>();
        FancyCompatibility compat = new FancyCompatibility(
            plugin -> enabled.get(), getClass().getClassLoader(),
            (name, loader) -> {
                calls.add(name);
                return () -> {};
            });
        compat.close();
        enabled.set(true);
        compat.onPluginEnabledName("FancyNpcs");
        compat.onPluginEnabledName("FancyHolograms");
        assertEquals(List.of(), calls);
    }

    @Test
    void unknownPluginNotificationHasNoEffect() {
        List<String> calls = new ArrayList<>();
        FancyCompatibility compat = new FancyCompatibility(
            plugin -> true, getClass().getClassLoader(),
            (name, loader) -> {
                calls.add(name);
                return () -> {};
            });
        assertEquals(2, calls.size());
        compat.onPluginEnabledName("OtherPlugin");
        assertEquals(2, calls.size());
        compat.close();
    }

    @Test
    void absentOptionalPluginsDoNotLoadIntegrationClasses() {
        AtomicBoolean integrationLoadAttempted = new AtomicBoolean();
        ClassLoader rejectingClassLoader = new ClassLoader(Thread.currentThread().getContextClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.contains("FancyNpcsCompatibility") || name.contains("FancyHologramsCompatibility")) {
                    integrationLoadAttempted.set(true);
                    throw new AssertionError("Disabled optional integration class was loaded: " + name);
                }
                return super.loadClass(name, resolve);
            }
        };

        assertDoesNotThrow(() -> {
            try (FancyCompatibility ignored = new FancyCompatibility(pluginName -> false, rejectingClassLoader)) {
                // No integration should be loaded or registered.
            }
        });
        assertFalse(integrationLoadAttempted.get());
    }
}
