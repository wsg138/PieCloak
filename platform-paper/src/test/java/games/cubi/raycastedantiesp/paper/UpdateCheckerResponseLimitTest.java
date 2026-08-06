/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2026 Cubicake.
 * This file is part of RaycastedAntiESP.
 * RaycastedAntiESP is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License v3.0 only, which can be accessed at https://www.gnu.org/licenses/agpl-3.0.html.
 * See README.md for warranty disclaimer and further information.
 */

package games.cubi.raycastedantiesp.paper;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateCheckerResponseLimitTest {
    @Test
    void declaredOversizedResponseIsRejectedBeforeOpeningBody() throws Exception {
        AtomicBoolean bodyOpened = new AtomicBoolean();
        URLConnection connection = new URLConnection(URI.create("https://example.invalid").toURL()) {
            @Override
            public void connect() {
            }

            @Override
            public long getContentLengthLong() {
                return UpdateChecker.MAX_RESPONSE_BYTES + 1L;
            }

            @Override
            public InputStream getInputStream() {
                bodyOpened.set(true);
                return InputStream.nullInputStream();
            }
        };

        IOException exception = assertThrows(IOException.class, () -> UpdateChecker.readVersionEntries(connection));

        assertTrue(exception.getMessage().contains("exceeded"));
        assertFalse(bodyOpened.get());
    }

    @Test
    void streamingResponseStopsAfterOneByteBeyondLimit() {
        CountingInputStream input = new CountingInputStream(UpdateChecker.MAX_RESPONSE_BYTES + 100);

        IOException exception = assertThrows(
                IOException.class,
                () -> UpdateChecker.readUtf8Body(input, UpdateChecker.MAX_RESPONSE_BYTES)
        );

        assertTrue(exception.getMessage().contains("exceeded"));
        assertEquals(UpdateChecker.MAX_RESPONSE_BYTES + 1, input.bytesRead());
    }

    @Test
    void boundedResponseIsDecodedAsUtf8() throws Exception {
        String body = "[\"✓\"]";

        assertEquals(
                body,
                UpdateChecker.readUtf8Body(
                        new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)),
                        UpdateChecker.MAX_RESPONSE_BYTES
                )
        );
    }

    private static final class CountingInputStream extends InputStream {
        private final int totalBytes;
        private int bytesRead;

        private CountingInputStream(int totalBytes) {
            this.totalBytes = totalBytes;
        }

        @Override
        public int read() {
            if (bytesRead >= totalBytes) {
                return -1;
            }
            bytesRead++;
            return 'x';
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            if (bytesRead >= totalBytes) {
                return -1;
            }
            int read = Math.min(length, totalBytes - bytesRead);
            for (int index = 0; index < read; index++) {
                buffer[offset + index] = 'x';
            }
            bytesRead += read;
            return read;
        }

        private int bytesRead() {
            return bytesRead;
        }
    }
}
