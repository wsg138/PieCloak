/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2026 Cubicake.
 * This file is part of RaycastedAntiESP.
 * RaycastedAntiESP is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License v3.0 only, which can be accessed at https://www.gnu.org/licenses/agpl-3.0.html.
 * See README.md for warranty disclaimer and further information.
 */

package games.cubi.raycastedantiesp.core.logging;

import games.cubi.logs.Logger;

/**
 * Records messages through CubiLogging, which performs level filtering internally.
 */
@SuppressWarnings("PMD.GuardLogStatement")
public final class CubiLog {
    private CubiLog() {
    }

    public static void recordInfo(String message, int level, Class<?> source) {
        Logger.info(message, level, source);
    }

    public static void recordWarning(String message, int level, Class<?> source) {
        Logger.warning(message, level, source);
    }

    public static void recordError(String message, Throwable failure, int level, Class<?> source) {
        Logger.error(message, failure, level, source);
    }
}
