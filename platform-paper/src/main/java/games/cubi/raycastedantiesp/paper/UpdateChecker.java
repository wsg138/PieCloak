/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2026 Cubicake.
 * This file is part of RaycastedAntiESP.
 * RaycastedAntiESP is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License v3.0 only, which can be accessed at https://www.gnu.org/licenses/agpl-3.0.html.
 * See README.md for warranty disclaimer and further information.
 */

package games.cubi.raycastedantiesp.paper;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import games.cubi.logs.Logger;
import games.cubi.raycastedantiesp.core.config.ConfigManager;
import games.cubi.raycastedantiesp.core.config.UpdateConfig;
import games.cubi.raycastedantiesp.core.utils.BuildProperties;
import games.cubi.raycastedantiesp.core.utils.BuildProperties.Version;
import games.cubi.raycastedantiesp.paper.utils.PaperScheduler;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class UpdateChecker {
    private static final String VERSION_API_ENDPOINT = "https://api.modrinth.com/v2/project/bCjNZu0C/version?include_changelog=false";
    private static final String PLATFORM_NAME = "Paper";
    private static final String MODRINTH_PAGE_URL = "https://modrinth.com/project/bCjNZu0C";
    static final int MAX_RESPONSE_BYTES = 50 * 1024;

    enum UpdateChannel {
        STABLE("release", "release"),
        BETA("Beta build", "beta"),
        ALPHA("Alpha build", "alpha");

        private final String messageName;
        private final String versionType;

        UpdateChannel(String messageName, String versionType) {
            this.messageName = messageName;
            this.versionType = versionType;
        }

        String messageName() {
            return messageName;
        }

        String versionType() {
            return versionType;
        }

        static UpdateChannel fromVersionType(String versionType) {
            for (UpdateChannel channel : values()) {
                if (channel.versionType.equals(versionType)) return channel;
            }
            return null;
        }
    }

    enum UpdateStatus {
        BEHIND,
        UP_TO_DATE,
        AHEAD,
        NO_COMPARABLE_VERSION,
        INVALID_CURRENT_VERSION
    }

    record VersionEntry(String rawVersion, UpdateChannel channel) {}

    record ApiVersion(String rawVersion, Version coreVersion, Version platformVersion) {}

    record UpdateCheckReport(List<UpdateCheckResult> results) {
        UpdateCheckReport {
            results = List.copyOf(results);
        }

        static UpdateCheckReport invalidCurrentVersion() {
            return new UpdateCheckReport(List.of(UpdateCheckResult.invalidCurrentVersion()));
        }
    }

    record UpdateCheckResult(UpdateStatus status, UpdateChannel channel, ApiVersion apiVersion) {
        static UpdateCheckResult behind(UpdateChannel channel, ApiVersion apiVersion) {
            return new UpdateCheckResult(UpdateStatus.BEHIND, channel, apiVersion);
        }

        static UpdateCheckResult upToDate(UpdateChannel channel, ApiVersion apiVersion) {
            return new UpdateCheckResult(UpdateStatus.UP_TO_DATE, channel, apiVersion);
        }

        static UpdateCheckResult ahead(UpdateChannel channel, ApiVersion apiVersion) {
            return new UpdateCheckResult(UpdateStatus.AHEAD, channel, apiVersion);
        }

        static UpdateCheckResult noComparableVersion(UpdateChannel channel) {
            return new UpdateCheckResult(UpdateStatus.NO_COMPARABLE_VERSION, channel, null);
        }

        static UpdateCheckResult invalidCurrentVersion() {
            return new UpdateCheckResult(UpdateStatus.INVALID_CURRENT_VERSION, null, null);
        }
    }

    private static CompletableFuture<List<VersionEntry>> fetchVersions(RaycastedAntiESP plugin) {
        CompletableFuture<List<VersionEntry>> future = new CompletableFuture<>();

        try {
            Bukkit.getAsyncScheduler().runNow(plugin, ignored -> {
                try {
                    URLConnection connection = new URI(VERSION_API_ENDPOINT).toURL().openConnection();
                    connection.setConnectTimeout(5_000);
                    connection.setReadTimeout(5_000);
                    future.complete(readVersionEntries(connection));
                } catch (IOException | URISyntaxException | RuntimeException exception) {
                    future.completeExceptionally(new IllegalStateException("Unable to fetch latest version", exception));
                }
            });
        } catch (RuntimeException exception) {
            future.completeExceptionally(new IllegalStateException("Unable to schedule update check", exception));
        }

        return future.orTimeout(10_000, TimeUnit.MILLISECONDS);
    }

    static List<VersionEntry> readVersionEntries(URLConnection connection) throws IOException {
        long declaredLength = connection.getContentLengthLong();
        if (declaredLength > MAX_RESPONSE_BYTES) {
            throw new IOException("Update response exceeded " + MAX_RESPONSE_BYTES + " bytes");
        }

        try (InputStream input = connection.getInputStream()) {
            return parseVersionEntries(readUtf8Body(input, MAX_RESPONSE_BYTES));
        }
    }

    static String readUtf8Body(InputStream input, int maxBytes) throws IOException {
        if (maxBytes < 0) {
            throw new IllegalArgumentException("maxBytes must not be negative");
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maxBytes, 8 * 1024));
        byte[] buffer = new byte[Math.min(Math.max(maxBytes + 1, 1), 8 * 1024)];
        while (true) {
            int remaining = maxBytes - output.size();
            int readLimit = Math.min(buffer.length, remaining + 1);
            int read = input.read(buffer, 0, readLimit);
            if (read == -1) {
                break;
            }
            if (read == 0) {
                int singleByte = input.read();
                if (singleByte == -1) {
                    break;
                }
                if (remaining == 0) {
                    throw new IOException("Update response exceeded " + maxBytes + " bytes");
                }
                output.write(singleByte);
                continue;
            }
            if (read > remaining) {
                throw new IOException("Update response exceeded " + maxBytes + " bytes");
            }
            output.write(buffer, 0, read);
        }

        return output.toString(StandardCharsets.UTF_8);
    }

    private static List<VersionEntry> parseVersionEntries(String responseBody) {
        JsonArray jsonArray = JsonParser.parseString(responseBody).getAsJsonArray();
        List<VersionEntry> versionEntries = new ArrayList<>();

        for (JsonElement element : jsonArray) {
            JsonObject versionObject = element.getAsJsonObject();
            if (versionObject.has("version_number") && versionObject.has("version_type")) {
                UpdateChannel channel = UpdateChannel.fromVersionType(versionObject.get("version_type").getAsString());
                if (channel != null) {
                    versionEntries.add(new VersionEntry(versionObject.get("version_number").getAsString(), channel));
                }
            }
        }

        return List.copyOf(versionEntries);
    }

    private static CompletableFuture<UpdateCheckReport> fetchUpdateCheck(RaycastedAntiESP plugin) {
        UpdateConfig updateConfig = ConfigManager.get().getUpdateConfig();
        if (!updateConfig.anyChannelEnabled()) {
            return CompletableFuture.completedFuture(new UpdateCheckReport(List.of()));
        }

        return fetchVersions(plugin).thenApply(versionEntries -> checkVersions(
                BuildProperties.CORE.version(),
                BuildProperties.PLATFORM.version(),
                versionEntries,
                updateConfig.checkRelease(),
                updateConfig.checkBeta(),
                updateConfig.checkAlpha()
        ));
    }

    static UpdateCheckReport checkVersions(Version currentCoreVersion, Version currentPlatformVersion, List<VersionEntry> versionEntries) {
        return checkVersions(currentCoreVersion, currentPlatformVersion, versionEntries, true, true, true);
    }

    static UpdateCheckReport checkVersions(Version currentCoreVersion, Version currentPlatformVersion, List<VersionEntry> versionEntries, boolean checkRelease, boolean checkBetaReleases, boolean checkAlphaReleases) {
        if (isErrorVersion(currentCoreVersion) || isErrorVersion(currentPlatformVersion)) {
            return UpdateCheckReport.invalidCurrentVersion();
        }

        List<UpdateCheckResult> results = new ArrayList<>();
        for (UpdateChannel channel : enabledChannels(checkRelease, checkBetaReleases, checkAlphaReleases)) {
            ApiVersion apiVersion = findComparablePaperVersion(versionEntries, channel);
            if (apiVersion == null) {
                results.add(UpdateCheckResult.noComparableVersion(channel));
                continue;
            }

            int comparison = compareCurrentToApi(currentCoreVersion, currentPlatformVersion, apiVersion);
            if (comparison < 0) {
                results.add(UpdateCheckResult.behind(channel, apiVersion));
            } else if (comparison == 0) {
                results.add(UpdateCheckResult.upToDate(channel, apiVersion));
            } else {
                results.add(UpdateCheckResult.ahead(channel, apiVersion));
            }
        }

        return new UpdateCheckReport(results);
    }

    private static List<UpdateChannel> enabledChannels(boolean checkRelease, boolean checkBetaReleases, boolean checkAlphaReleases) {
        List<UpdateChannel> channels = new ArrayList<>();
        if (checkRelease) channels.add(UpdateChannel.STABLE);
        if (checkBetaReleases) channels.add(UpdateChannel.BETA);
        if (checkAlphaReleases) channels.add(UpdateChannel.ALPHA);
        return channels;
    }

    private static boolean isErrorVersion(Version version) {
        return version.major() < 0 || version.minor() < 0 || version.patch() < 0;
    }

    static ApiVersion findComparablePaperVersion(List<VersionEntry> versionEntries, UpdateChannel channel) {
        for (VersionEntry versionEntry : versionEntries) {
            if (versionEntry.channel() != channel) continue;
            if (isLegacyApiVersion(versionEntry.rawVersion())) continue;

            try {
                return parseApiVersion(versionEntry.rawVersion());
            } catch (IllegalArgumentException ignored) {
                // Continue scanning older API entries until a Paper version is found.
            }
        }

        return null;
    }

    static ApiVersion parseApiVersion(String versionNumber) {
        String normalized = stripLeadingV(versionNumber.trim());
        String[] parts = normalized.split("-", 3);

        if (parts.length != 3) {
            throw new IllegalArgumentException("Version string must be in the format 'core-platform-platformVersion'. Attempted: " + versionNumber);
        }

        if (!PLATFORM_NAME.equals(parts[1])) {
            throw new IllegalArgumentException("Version platform must be " + PLATFORM_NAME + ". Attempted: " + versionNumber);
        }

        return new ApiVersion(versionNumber, Version.parse(parts[0]), Version.parse(parts[2]));
    }

    private static boolean isLegacyApiVersion(String versionNumber) {
        String normalized = stripLeadingV(versionNumber.trim());
        if (normalized.split("-", 3).length == 3) return false;

        try {
            return Version.parse(normalized).major() == 1;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static String stripLeadingV(String versionNumber) {
        if (versionNumber.startsWith("v") || versionNumber.startsWith("V")) {
            return versionNumber.substring(1);
        }
        return versionNumber;
    }

    private static int compareCurrentToApi(Version currentCoreVersion, Version currentPlatformVersion, ApiVersion apiVersion) {
        int coreComparison = currentCoreVersion.compareTo(apiVersion.coreVersion());
        int platformComparison = currentPlatformVersion.compareTo(apiVersion.platformVersion());

        if (coreComparison < 0 || platformComparison < 0) return -1;
        if (coreComparison > 0 || platformComparison > 0) return 1;
        return 0;
    }

    static String formatUpdateMessage(UpdateCheckResult result) {
        return switch (result.status()) {
            case BEHIND -> "<red>You are behind the latest " + result.channel().messageName() + " of RaycastedAntiESP. Please upgrade to <green>v" + result.apiVersion().rawVersion() + "<red> at " + modrinthLink() + "<red>.";
            case UP_TO_DATE -> "<green>You are up to date with the latest " + result.channel().messageName() + " of RaycastedAntiESP.";
            case AHEAD, NO_COMPARABLE_VERSION -> "<yellow>You are ahead of the latest " + result.channel().messageName() + " of RaycastedAntiESP.";
            case INVALID_CURRENT_VERSION -> "<red>Unable to check for updates to RaycastedAntiESP, invalid current version format.";
        };
    }

    static String formatUpdateMessage(UpdateCheckReport report) {
        List<String> messages = new ArrayList<>();
        for (UpdateCheckResult result : report.results()) {
            messages.add(formatUpdateMessage(result));
        }

        return String.join("\n", messages);
    }

    private static String modrinthLink() {
        return "<hover:show_text:'" + MODRINTH_PAGE_URL + "'><aqua><u><click:open_url:'" + MODRINTH_PAGE_URL + "'>" + MODRINTH_PAGE_URL + "</click></u></aqua></hover>";
    }

    public static void checkForUpdates(RaycastedAntiESP plugin, CommandSender audience) {
        fetchUpdateCheck(plugin).thenAccept(report -> {
            if (report.results().isEmpty()) {
                return;
            }
            PaperScheduler.runForAudience(plugin, audience, () -> audience.sendRichMessage(formatUpdateMessage(report)));
        }).exceptionally(exception -> {
            Logger.error("An error occurred while checking for plugin updates", exception, 4, UpdateChecker.class);
            return null;
        });
    }
}
