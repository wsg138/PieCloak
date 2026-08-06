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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

class FancyCompatibilityTest {
    @Test
    void absentOptionalPluginsDoNotLoadIntegrationClasses() {
        AtomicBoolean integrationLoadAttempted = new AtomicBoolean();
        ClassLoader rejectingClassLoader = new ClassLoader(FancyCompatibilityTest.class.getClassLoader()) {
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
