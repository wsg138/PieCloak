/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2026 Cubicake.
 * This file is part of RaycastedAntiESP.
 * RaycastedAntiESP is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License v3.0 only, which can be accessed at https://www.gnu.org/licenses/agpl-3.0.html.
 * See README.md for warranty disclaimer and further information.
 */

package games.cubi.raycastedantiesp.core.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildPropertiesVersionTest {
    @Test
    void parseAcceptsMultiDigitVersions() {
        BuildProperties.Version version = BuildProperties.Version.parse("17.41.264");

        assertEquals(17, version.major());
        assertEquals(41, version.minor());
        assertEquals(264, version.patch());
    }

    @Test
    void parseIgnoresSuffixesAndMetadata() {
        BuildProperties.Version snapshot = BuildProperties.Version.parse("17.41.264-SNAPSHOT+build-2026-07-08+git-abcdef12");
        BuildProperties.Version release = BuildProperties.Version.parse("17.41.264-RELEASE");

        assertEquals(new BuildProperties.Version(17, 41, 264, true), snapshot);
        assertEquals(new BuildProperties.Version(17, 41, 264, false), release);
    }

    @Test
    void compareToIgnoresSnapshotState() {
        BuildProperties.Version snapshot = new BuildProperties.Version(1, 2, 3, true);
        BuildProperties.Version release = new BuildProperties.Version(1, 2, 3, false);

        assertEquals(0, snapshot.compareTo(release));
    }

    @Test
    void compareToOrdersMajorMinorThenPatch() {
        assertTrue(new BuildProperties.Version(2, 0, 0, false).compareTo(new BuildProperties.Version(1, 99, 99, false)) > 0);
        assertTrue(new BuildProperties.Version(1, 3, 0, false).compareTo(new BuildProperties.Version(1, 2, 99, false)) > 0);
        assertTrue(new BuildProperties.Version(1, 2, 4, false).compareTo(new BuildProperties.Version(1, 2, 3, false)) > 0);
        assertTrue(new BuildProperties.Version(1, 2, 3, false).compareTo(new BuildProperties.Version(1, 2, 4, false)) < 0);
    }

    @Test
    void parseRejectsInvalidVersions() {
        assertThrows(IllegalArgumentException.class, () -> BuildProperties.Version.parse("17.41"));
        assertThrows(IllegalArgumentException.class, () -> BuildProperties.Version.parse("17.41.not-a-number"));
    }
}
