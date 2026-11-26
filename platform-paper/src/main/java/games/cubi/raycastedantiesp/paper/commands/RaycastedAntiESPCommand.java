/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2026 Cubicake.
 * This file is part of RaycastedAntiESP.
 * RaycastedAntiESP is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License v3.0 only, which can be accessed at https://www.gnu.org/licenses/agpl-3.0.html.
 * See README.md for warranty disclaimer and further information.
 */

package games.cubi.raycastedantiesp.paper.commands;


import games.cubi.locatables.api.Locatable;
import games.cubi.locatables.api.MutableFloatingSpatial;
import games.cubi.locatables.api.Spatial;
import games.cubi.locatables.implementations.MutableLocatableImpl;
import games.cubi.locatables.implementations.MutableSpatialImpl;
import games.cubi.logs.Logger;
import games.cubi.raycastedantiesp.core.config.ConfigManager;
import games.cubi.raycastedantiesp.core.tracked.TrackedEntity;
import games.cubi.raycastedantiesp.core.players.PlayerData;
import games.cubi.raycastedantiesp.core.players.PlayerRegistry;
import games.cubi.raycastedantiesp.core.raycast.RaycastUtil;
import games.cubi.raycastedantiesp.core.view.AbstractBlockView;
import games.cubi.raycastedantiesp.core.view.BlockView;
import games.cubi.raycastedantiesp.core.view.EntityView;
import games.cubi.raycastedantiesp.paper.RaycastedAntiESP;
import games.cubi.raycastedantiesp.paper.UpdateChecker;
import games.cubi.raycastedantiesp.paper.packets.PacketEventsPaperBlockInfoResolver;

import games.cubi.raycastedantiesp.paper.utils.PaperScheduler;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;

import net.strokkur.commands.*;
import net.strokkur.commands.arguments.IntArg;
import net.strokkur.commands.arguments.StringArg;
import net.strokkur.commands.arguments.StringArgType;
import net.strokkur.commands.paper.DefaultToExecutor;
import net.strokkur.commands.paper.Description;
import net.strokkur.commands.permission.Permission;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.OptionalInt;
import java.util.SplittableRandom;
import java.util.Locale;
import java.util.UUID;

// Credit to Strokkur for making StrokkCommands, a non-hideous way to use the power of brigadier.

@Command("raycastedantiesp")
@Aliases({"raesp", "antiesp", "reo"})
@Description("Command for management of the RaycastedAntiESP plugin")
@Permission("raycastedantiesp.command")
public class RaycastedAntiESPCommand {
    private static final String VISIBLE_SUMMARY = " <gray>(visible=<white>";
    private static final String HIDDEN_SUMMARY = "<gray>, hidden=<white>";
    private static final String SUMMARY_END = "<gray>)";

    @DefaultExecutes
    public void helpCommand(CommandSender sender) {
        sender.sendRichMessage("<white>RaycastedAntiESP <yellow>v" + RaycastedAntiESP.get().getDescription().getVersion());
        sender.sendRichMessage("<white>Commands:");
        sender.sendRichMessage("<green>/raycastedantiesp reload <gray>- Reloads the config");
        sender.sendRichMessage("<green>/raycastedantiesp config-values <gray>- Shows all config values");
        sender.sendRichMessage("<green>/raycastedantiesp set <key> <value> <gray>- Sets a config value");
        sender.sendRichMessage("<green>/raycastedantiesp add <key> <value> <gray>- Adds a value to a list config");
        sender.sendRichMessage("<green>/raycastedantiesp remove <key> <value> <gray>- Removes a value from a list config");
        sender.sendRichMessage("<green>/raycastedantiesp source <gray>- Shows the PieCloak source repository");
        sender.sendRichMessage("<green>/raycastedantiesp stats <gray>- Shows managed target counts and filter status");
        sender.sendRichMessage("<green>/raycastedantiesp debugplayer <player> <gray>- Shows one viewer's managed target counts");
        sender.sendRichMessage("<green>/raycastedantiesp benchmark <radius> <samples> <gray>- Benchmarks raycast checks");
        sender.sendRichMessage(Attribution.attributionCommandDescription); //Using constant from Attribution class to ensure that it cannot be deleted without the developer noticing that they are obligated to replace it with an equivalent notice.
    }

    @Executes("reload")
    void reloadCommand(CommandSender sender) {
        try {
            ConfigManager.get().load();
            sender.sendMessage("[RaycastedAntiESP] Config reloaded.");
        } catch (RuntimeException e) {
            sender.sendRichMessage("<red>[RaycastedAntiESP] Config reload rejected: <white>" + e.getMessage());
        }
    }

    @Executes("config-values")
    void configValuesCommand(CommandSender sender) {
        ConfigManager config = ConfigManager.get();
        //dynamic config values
        sender.sendMessage("[RaycastedAntiESP] Config values: ");

        for (var entry : config.getConfigValues().entrySet()) {
            String path = entry.getKey();
            Object val = entry.getValue();
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<green>" + path + "<gray> = <white>" + val));
        }
    }

    @Executes("set")
    void setCommand(@StringArg(StringArgType.STRING) String key, @StringArg(StringArgType.GREEDY) String value, CommandSender sender) {
        ConfigManager config = ConfigManager.get();
        ConfigManager.SetConfigResult result = config.setConfigValue(key, value);
        sendConfigMutationResult(sender, result, "Set", key, value);
    }

    @Executes("add")
    void addCommand(@StringArg(StringArgType.STRING) String key, @StringArg(StringArgType.GREEDY) String value, CommandSender sender) {
        ConfigManager config = ConfigManager.get();
        ConfigManager.SetConfigResult result = config.addConfigListValue(key, value);
        sendConfigMutationResult(sender, result, "Added", key, value);
    }

    @Executes("remove")
    void removeCommand(@StringArg(StringArgType.STRING) String key, @StringArg(StringArgType.GREEDY) String value, CommandSender sender) {
        ConfigManager config = ConfigManager.get();
        ConfigManager.SetConfigResult result = config.removeConfigListValue(key, value);
        sendConfigMutationResult(sender, result, "Removed", key, value);
    }

    @Executes("check-for-updates")
    void checkForUpdatesCommand(CommandSender sender) {
        UpdateChecker.checkForUpdates(RaycastedAntiESP.get(), sender);
    }

    @Executes("source")
    void sourceCommand(CommandSender sender) {
        SourceCommand.sendSourceLink(sender);
    }

    @Executes("stats")
    void statsCommand(CommandSender sender) {
        var filter = RaycastedAntiESP.getTargetFilter().snapshot();
        long managedEntities = 0;
        long visibleEntities = 0;
        long managedBlockEntities = 0;
        long visibleBlockEntities = 0;
        int currentTick = RaycastedAntiESP.getCurrentTick();

        for (PlayerData playerData : PlayerRegistry.get().getAllPlayerData()) {
            EntityView<?> entityView = playerData.entityView();
            managedEntities += entityView.size();
            for (UUID entityUUID : entityView.getKnownEntities()) {
                if (entityView.isVisible(entityUUID)) {
                    visibleEntities++;
                }
            }
            for (var tileEntity : playerData.blockView().getKnownTileEntities()) {
                managedBlockEntities++;
                if (playerData.blockView().isVisible(playerData.ownLocation().world(), tileEntity, currentTick)) {
                    visibleBlockEntities++;
                }
            }
        }

        sender.sendRichMessage("<white>[PieCloak] Target filter: <green>" + (filter.enabled() ? "enabled" : "disabled")
                + "<gray>, entity-types=<white>" + filter.entityTypeCount()
                + "<gray>, block-materials=<white>" + filter.blockMaterialCount()
                + "<gray>, block-states=<white>" + filter.blockStateCount()
                + "<gray>, safe-block-entity-types=<white>" + filter.safeBlockEntityTypeCount());
        sender.sendRichMessage("<white>[PieCloak] Managed targets across viewers: <gray>entities=<white>" + managedEntities
                + VISIBLE_SUMMARY + visibleEntities + HIDDEN_SUMMARY + (managedEntities - visibleEntities) + SUMMARY_END
                + " block-entities=<white>" + managedBlockEntities
                + VISIBLE_SUMMARY + visibleBlockEntities + HIDDEN_SUMMARY + (managedBlockEntities - visibleBlockEntities) + SUMMARY_END);
        if (!filter.invalidEntries().isEmpty()) {
            sender.sendRichMessage("<yellow>[PieCloak] Skipped invalid target-filter entries: <white>" + filter.invalidEntries().size());
            filter.invalidEntries().stream().limit(8).forEach(entry -> sender.sendRichMessage("<gray>- <white>" + entry));
        }
    }

    @Executes("debugplayer")
    void debugPlayerCommand(@StringArg(StringArgType.STRING) String playerName, CommandSender sender) {
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            sender.sendRichMessage("<red>Player not found: <white>" + playerName);
            return;
        }
        PlayerData playerData = PlayerRegistry.get().getPlayerData(target.getUniqueId());
        if (playerData == null) {
            sender.sendRichMessage("<red>No PieCloak player data is registered for <white>" + target.getName());
            return;
        }

        long visibleEntities = playerData.entityView().getKnownEntities().stream()
                .filter(playerData.entityView()::isVisible)
                .count();
        long visibleTiles = playerData.blockView().getKnownTileEntities().stream()
                .filter(tile -> playerData.blockView().isVisible(playerData.ownLocation().world(), tile, RaycastedAntiESP.getCurrentTick()))
                .count();
        int entityCount = playerData.entityView().size();
        int tileCount = playerData.blockView().getKnownTileEntities().size();
        sender.sendRichMessage("<white>[PieCloak] Viewer <green>" + target.getName()
                + "<gray>: managed-entities=<white>" + entityCount
                + VISIBLE_SUMMARY + visibleEntities + HIDDEN_SUMMARY + (entityCount - visibleEntities) + SUMMARY_END
                + " managed-block-entities=<white>" + tileCount
                + VISIBLE_SUMMARY + visibleTiles + HIDDEN_SUMMARY + (tileCount - visibleTiles) + SUMMARY_END);
        if (playerData.blockView() instanceof AbstractBlockView blockView) {
            sender.sendRichMessage("<gray>Tracked chunk sections: <white>" + blockView.loadedChunkCount());
        }
    }

    @Executes("benchmark")
    void benchmarkCommand(int radius, int samples, CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendRichMessage("<red>This command must be run by a player.");
            return;
        }
        int clampedRadius = Math.max(1, Math.min(radius, 512));
        int clampedSamples = Math.max(1, Math.min(samples, 100_000));
        PlayerData playerData = PlayerRegistry.get().getPlayerData(player.getUniqueId());
        Locatable origin = playerData == null ? null : playerData.ownLocation();
        if (origin == null || origin.world() == null) {
            player.sendRichMessage("<red>No PieCloak player data is available yet.");
            return;
        }

        Locatable[] targets = new Locatable[clampedSamples];
        MutableFloatingSpatial direction = new MutableSpatialImpl(0, 0, 0);
        for (int i = 0; i < targets.length; i++) {
            direction.setX(Math.random() - 0.5);
            direction.setY(Math.random() - 0.5);
            direction.setZ(Math.random() - 0.5);
            direction.normalise();
            direction.scalarMultiply(Math.random() * clampedRadius);
            targets[i] = new MutableLocatableImpl(origin.world(), origin.x(), origin.y(), origin.z()).add(direction);
        }

        RaycastUtil.Settings raycastSettings = new RaycastUtil.Settings(
                3, 0, clampedRadius, false, playerData.blockView(), null);
        Bukkit.getAsyncScheduler().runNow(RaycastedAntiESP.get(), ignored -> {
            long startNanos = System.nanoTime();
            int visible = 0;
            for (Locatable target : targets) {
                if (RaycastUtil.raycast(origin, target, raycastSettings)) {
                    visible++;
                }
            }
            long duration = System.nanoTime() - startNanos;
            int finalVisible = visible;
            PaperScheduler.runForAudience(RaycastedAntiESP.get(), player, () -> player.sendRichMessage(
                    "<white>PieCloak raycast benchmark: <green>" + clampedSamples
                            + " <gray>samples within <green>" + clampedRadius
                            + " <gray>blocks, avg=<white>" + String.format(Locale.ROOT, "%,d", duration / clampedSamples)
                            + "ns <gray>total=<white>" + String.format(Locale.ROOT, "%.3f", duration / 1_000_000.0)
                            + "ms <gray>visible=<white>" + finalVisible));
        });
    }

    @Executes("print-block-ids")
    void printBlockIDsCommand() {
        PacketEventsPaperBlockInfoResolver.get.iterateBlockIDs(true);
    }

    private void sendConfigMutationResult(CommandSender sender, ConfigManager.SetConfigResult result, String action, String key, String value) {
        if (!result.success()) {
            sender.sendRichMessage("<red>Invalid config change: <white>" + result.message());
            return;
        }
        sender.sendRichMessage("<white>" + action + " <green>" + value + "<white> for <green>" + key);
        if (result.restartRequired()) {
            sender.sendRichMessage("<yellow>This change was saved but requires a restart: <white>" + result.message());
        } else if (result.repaired()) {
            sender.sendRichMessage("<yellow>" + result.message());
        }
    }

    @Subcommand("test")
    static class TestCommands {
        @Executes("log")
        void logString(@StringArg(StringArgType.GREEDY) String message) {
            Logger.info(message, 1, RaycastedAntiESPCommand.class);
        }

        @Executes("location-drift")
        void testCommand(CommandSender sender) {
            assert Attribution.class == Attribution.class;
            assert Attribution.READ_COMMENTS_BEFORE_EDITING_OR_DELETING_CLASS_OR_FACE_LEGAL_ACTION == 0; //Using constant from Attribution class to ensure that it cannot be deleted without the developer noticing that they are obligated to replace it with an equivalent notice.
            Player player = (Player) sender;
            PlayerData playerData = PlayerRegistry.get().getPlayerData(player.getUniqueId());
            Entity closestEntity = player.getNearbyEntities(10,10,10).getFirst();
            if (closestEntity == null) return;
            player.sendRichMessage("Closest entity is "+closestEntity.getName());
            Spatial entityPosition = playerData.entityView().getPosition(closestEntity.getUniqueId());
            Location bukkitLoc = closestEntity.getLocation().clone();
            player.sendRichMessage("Entity location according to PacketEvents is "+entityPosition);
            player.sendRichMessage("Entity location according to Bukkit is "+bukkitLoc);
            double driftX = Math.abs(entityPosition.x() - bukkitLoc.getX());
            double driftZ = Math.abs(entityPosition.z() - bukkitLoc.getZ());
            if (driftX < 0.0005) driftX = 0;
            if (driftZ < 0.0005) driftZ = 0;
            Logger.debug("Drift is X: "+driftX+" Z: "+driftZ);
            sender.sendRichMessage("Drift is X: "+driftX+" Z: "+driftZ);
        }

        @Subcommand("benchmark")
        static class BenchmarkCommand {
            private static final int BENCHMARK_CORPUS_SIZE = 8192;
            private static final int BENCHMARK_CORPUS_MASK = BENCHMARK_CORPUS_SIZE - 1;
            private static final int BENCHMARK_BATCH_SIZE = 2048;
            private static final int BENCHMARK_WARMUP_BATCHES = 1250;
            private static final int BENCHMARK_MEASUREMENT_BATCHES = 512;
            private static final int BENCHMARK_ROUNDS = 5;
            private static final int DEFAULT_RAYCAST_DISTANCE = 50;
            private static final long BENCHMARK_SEED = 0x6a09e667f3bcc909L;
            private static volatile long benchmarkSink;

            @DefaultExecutes
            void benchmark(@DefaultToExecutor Player player, @IntArg(min = 1, max = 512) OptionalInt overrideRaycastDistance, @IntArg(min = 0, max = 1250) OptionalInt overrideWarmup, @IntArg(min = 1, max = 512) OptionalInt overrideBatches) {
                int raycastDistance = overrideRaycastDistance.orElse(DEFAULT_RAYCAST_DISTANCE);
                PlayerData playerData = PlayerRegistry.get().getPlayerData(player.getUniqueId());
                if (playerData == null || playerData.ownLocation().world() == null) {
                    player.sendRichMessage("<red>No PieCloak player data is available yet.");
                    return;
                }
                Locatable current = playerData.ownLocation();
                Locatable origin = new MutableLocatableImpl(current.world(), current.x(), current.y(), current.z());
                Locatable[] targets = createCorpus(origin, raycastDistance);
                RaycastUtil.Settings settings = new RaycastUtil.Settings(3, 0, Math.max(100, raycastDistance + 1), false, playerData.blockView(), null);
                BenchmarkInput input = new BenchmarkInput(origin, targets, settings,
                        overrideWarmup.orElse(BENCHMARK_WARMUP_BATCHES), overrideBatches.orElse(BENCHMARK_MEASUREMENT_BATCHES));
                player.sendRichMessage("Running deterministic raycast benchmark at distance " + raycastDistance + "...");
                Bukkit.getAsyncScheduler().runNow(RaycastedAntiESP.get(), ignored -> runBenchmark(player, input));
            }

            private static Locatable[] createCorpus(Locatable origin, int raycastDistance) {
                Locatable[] targets = new Locatable[BENCHMARK_CORPUS_SIZE];
                MutableFloatingSpatial direction = new MutableSpatialImpl(0, 0, 0);
                SplittableRandom random = new SplittableRandom(BENCHMARK_SEED);
                for (int i = 0; i < targets.length; i++) {
                    direction.setX(random.nextDouble(-0.5, 0.5));
                    direction.setY(random.nextDouble(-0.5, 0.5));
                    direction.setZ(random.nextDouble(-0.5, 0.5));
                    direction.normalise();
                    direction.scalarMultiply(raycastDistance);
                    targets[i] = new MutableLocatableImpl(origin.world(), origin.x(), origin.y(), origin.z()).add(direction);
                }
                return targets;
            }

            private static com.sun.management.ThreadMXBean allocationBean() {
                java.lang.management.ThreadMXBean bean = ManagementFactory.getThreadMXBean();
                if (!(bean instanceof com.sun.management.ThreadMXBean allocation) || !allocation.isThreadAllocatedMemorySupported()) {
                    return null;
                }
                try {
                    if (!allocation.isThreadAllocatedMemoryEnabled()) {
                        allocation.setThreadAllocatedMemoryEnabled(true);
                    }
                    return allocation.isThreadAllocatedMemoryEnabled() ? allocation : null;
                } catch (SecurityException | UnsupportedOperationException ignored) {
                    return null;
                }
            }

            private static void runBenchmark(Player player, BenchmarkInput input) {
                long warmupChecksum = 0;
                int corpusIndex = 0;
                for (int batch = 0; batch < input.warmup(); batch++) {
                    warmupChecksum += runRaycastBatch(input.origin(), input.targets(), corpusIndex, input.settings());
                    corpusIndex = (corpusIndex + BENCHMARK_BATCH_SIZE) & BENCHMARK_CORPUS_MASK;
                }
                benchmarkSink = warmupChecksum;

                var bean = allocationBean();
                long threadId = Thread.currentThread().threadId();
                double[] times = new double[BENCHMARK_ROUNDS];
                double[] bytes = new double[BENCHMARK_ROUNDS];
                long[] checksums = new long[BENCHMARK_ROUNDS];
                double[] batchTimes = new double[BENCHMARK_ROUNDS * input.batches()];
                for (int round = 0; round < BENCHMARK_ROUNDS; round++) {
                    RoundResult result = measureRound(input, batchTimes, round, bean, threadId);
                    times[round] = result.time();
                    bytes[round] = result.bytes();
                    checksums[round] = result.checksum();
                }
                BenchmarkResult result = new BenchmarkResult(times, bytes, checksums, batchTimes,
                        Thread.currentThread().getName() + " (id " + threadId + ")", input.warmup());
                PaperScheduler.runForAudience(RaycastedAntiESP.get(), player, () -> reportBenchmark(player, result));
            }

            private static RoundResult measureRound(BenchmarkInput input, double[] batchTimes, int round,
                                                    com.sun.management.ThreadMXBean bean, long threadId) {
                long allocatedBefore = bean == null ? -1 : bean.getThreadAllocatedBytes(threadId);
                long startTime = System.nanoTime();
                long checksum = 0;
                int corpusIndex = 0;
                for (int batch = 0; batch < input.batches(); batch++) {
                    long batchStartTime = System.nanoTime();
                    checksum += runRaycastBatch(input.origin(), input.targets(), corpusIndex, input.settings());
                    batchTimes[round * input.batches() + batch] = (System.nanoTime() - batchStartTime) / (double) BENCHMARK_BATCH_SIZE;
                    corpusIndex = (corpusIndex + BENCHMARK_BATCH_SIZE) & BENCHMARK_CORPUS_MASK;
                }
                long duration = System.nanoTime() - startTime;
                long allocatedAfter = bean == null ? -1 : bean.getThreadAllocatedBytes(threadId);
                benchmarkSink = checksum;
                int count = BENCHMARK_BATCH_SIZE * input.batches();
                double bytes = allocatedBefore >= 0 && allocatedAfter >= allocatedBefore
                        ? (allocatedAfter - allocatedBefore) / (double) count : Double.NaN;
                return new RoundResult(duration / (double) count, bytes, checksum);
            }

            private static void reportBenchmark(Player player, BenchmarkResult result) {
                player.sendRichMessage("Benchmark thread: " + result.thread());
                player.sendRichMessage("Warmup rays: " + (BENCHMARK_BATCH_SIZE * result.warmup()));
                for (int round = 0; round < BENCHMARK_ROUNDS; round++) {
                    String allocation = Double.isNaN(result.bytes()[round]) ? "allocation unavailable"
                            : String.format(Locale.ROOT, "%.3f B/ray", result.bytes()[round]);
                    player.sendRichMessage(String.format(Locale.ROOT, "Round %d: %.3f ns/ray, %s, checksum %d",
                            round + 1, result.times()[round], allocation, result.checksums()[round]));
                }
                double[] sorted = result.times().clone();
                Arrays.sort(sorted);
                double average = mean(result.times());
                double deviation = standardDeviation(result.times(), average);
                player.sendRichMessage(String.format(Locale.ROOT, "Rounds: median %.3f, mean %.3f, min %.3f, max %.3f ns/ray",
                        sorted[sorted.length / 2], average, sorted[0], sorted[sorted.length - 1]));
                player.sendRichMessage(String.format(Locale.ROOT, "Round deviation: %.3f ns/ray (CV %.2f%%)", deviation, deviation / average * 100.0));
                double[] batchTimes = result.batchTimes().clone();
                Arrays.sort(batchTimes);
                player.sendRichMessage(String.format(Locale.ROOT, "Batches (%d rays, %d samples): p50 %.3f, p90 %.3f, p95 %.3f, p99 %.3f, max %.3f ns/ray",
                        BENCHMARK_BATCH_SIZE, batchTimes.length, percentile(batchTimes, 0.50), percentile(batchTimes, 0.90),
                        percentile(batchTimes, 0.95), percentile(batchTimes, 0.99), batchTimes[batchTimes.length - 1]));
            }

            private record BenchmarkInput(Locatable origin, Locatable[] targets, RaycastUtil.Settings settings,
                                          int warmup, int batches) {}
            private record RoundResult(double time, double bytes, long checksum) {}
            private record BenchmarkResult(double[] times, double[] bytes, long[] checksums, double[] batchTimes,
                                           String thread, int warmup) {}

            private static double mean(double[] values) {
                double total = 0;
                for (double value : values) total += value;
                return total / values.length;
            }

            private static double standardDeviation(double[] values, double mean) {
                double squaredDifferenceTotal = 0;
                for (double value : values) {
                    double difference = value - mean;
                    squaredDifferenceTotal += difference * difference;
                }
                return Math.sqrt(squaredDifferenceTotal / values.length);
            }

            private static double percentile(double[] sortedValues, double percentile) {
                double index = (sortedValues.length - 1) * percentile;
                int lowerIndex = (int) index;
                int upperIndex = Math.min(lowerIndex + 1, sortedValues.length - 1);
                double fraction = index - lowerIndex;
                return sortedValues[lowerIndex] + (sortedValues[upperIndex] - sortedValues[lowerIndex]) * fraction;
            }

            private static int runRaycastBatch(Locatable start, Locatable[] targets, int startIndex, RaycastUtil.Settings settings) {
                int successfulRays = 0;
                for (int offset = 0; offset < BENCHMARK_BATCH_SIZE; offset++) {
                    if (RaycastUtil.raycast(start, targets[startIndex + offset], settings)) successfulRays++;
                }
                return successfulRays;
            }
        }

        @Executes("loaded-chunks")
        void loadedChunksCommand(Player player) {
            PlayerData playerData = PlayerRegistry.get().getPlayerData(player.getUniqueId());
            AbstractBlockView<?, ?> pbsm = (AbstractBlockView<?, ?>) playerData.blockView();
            player.sendMessage(pbsm.loadedChunkCount() +"chunks loaded");
        }

        @Executes("entity-id")
        void getFromEntityID(int entityID, Player player) {
            PlayerData playerData = PlayerRegistry.get().getPlayerData(player.getUniqueId());
            if (playerData == null) {
                player.sendRichMessage("<red>No player data is registered for " + describeViewer(player.getUniqueId()) + ".");
                return;
            }

            Entity bukkitEntity = SpigotConversionUtil.getEntityById(player.getWorld(), entityID);
            player.sendRichMessage("<white>Entity with ID " + entityID + " for viewer " + describeViewer(playerData.getPlayerUUID()) + ":");
            if (bukkitEntity == null) {
                player.sendRichMessage("<gray>According to Bukkit: <red>not found");
            } else {
                sendBukkitEntityData(player, bukkitEntity);
            }

            int matches = reportEntityIDMatches(player, playerData, entityID);
            if (matches == 0) {
                player.sendRichMessage("<gray>According to PacketEvents: <red>not found in either tracked view");
            }
        }

        @Executes("entity-id")
        void getFromEntityID(int entityID, CommandSender sender) {
            sender.sendRichMessage("<white>Searching all connected player views for entity ID " + entityID + ":");
            int matches = 0;
            for (PlayerData playerData : PlayerRegistry.get().getAllPlayerData()) {
                if (!playerData.isConnected()) {
                    continue;
                }
                matches += reportEntityIDMatches(sender, playerData, entityID);
            }
            if (matches == 0) {
                sender.sendRichMessage("<red>No tracked entity with ID " + entityID + " was found.");
            }
        }

        @Executes("entity-uuid-raw")
        void getFromRawUUID(String entityUUIDraw, CommandSender sender) {
            sender.sendRichMessage("<white>Searching all connected player views for entity UUID " + entityUUIDraw + ":");
            UUID entityUUID = UUID.fromString(entityUUIDraw);
            getFromUUID(entityUUID, sender);
        }
        void getFromUUID(UUID entityUUID, CommandSender sender) {
            int matches = 0;
            for (PlayerData playerData : PlayerRegistry.get().getAllPlayerData()) {
                if (!playerData.isConnected()) {
                    continue;
                }
                matches += reportEntityUUIDMatches(sender, playerData, entityUUID);
            }
            if (matches == 0) {
                sender.sendRichMessage("<red>No tracked entity with UUID " + entityUUID + " was found.");
            }
        }

        @Executes("entity-uuid")
        void getFromEntityUUID(Entity entity, CommandSender sender) {
            UUID entityUUID = entity.getUniqueId();
            sender.sendRichMessage("<white>Bukkit entity data:");
            sendBukkitEntityData(sender, entity);
            getFromUUID(entityUUID, sender);
        }

        private int reportEntityIDMatches(CommandSender sender, PlayerData playerData, int entityID) {
            return reportEntityIDMatches(sender, playerData, playerData.entityView(), "entity view", entityID)
                    + reportEntityIDMatches(sender, playerData, playerData.playerView(), "player view", entityID);
        }

        private int reportEntityIDMatches(CommandSender sender, PlayerData playerData, EntityView<?> view, String viewName, int entityID) {
            int matches = 0;
            for (UUID entityUUID : view.getKnownEntities()) {
                TrackedEntity<?> entity = view.getEntity(entityUUID);
                if (entity == null || entity.entityID() != entityID) {
                    continue;
                }
                sendTrackedEntityMatch(sender, playerData, viewName, entity);
                matches++;
            }
            return matches;
        }

        private int reportEntityUUIDMatches(CommandSender sender, PlayerData playerData, UUID entityUUID) {
            int matches = reportEntityUUIDMatch(sender, playerData, playerData.entityView(), "entity view", entityUUID);
            return matches + reportEntityUUIDMatch(sender, playerData, playerData.playerView(), "player view", entityUUID);
        }

        private int reportEntityUUIDMatch(CommandSender sender, PlayerData playerData, EntityView<?> view, String viewName, UUID entityUUID) {
            TrackedEntity<?> entity = view.getEntity(entityUUID);
            if (entity == null) {
                return 0;
            }
            sendTrackedEntityMatch(sender, playerData, viewName, entity);
            return 1;
        }

        private void sendTrackedEntityMatch(CommandSender sender, PlayerData playerData, String viewName, TrackedEntity<?> entity) {
            sender.sendRichMessage("<green>Match for viewer <white>" + describeViewer(playerData.getPlayerUUID()) + "<green> in <white>" + viewName + "<green>:");
            sender.sendRichMessage("<gray>According to PacketEvents: <white>" + entity);
        }

        private void sendBukkitEntityData(CommandSender sender, Entity entity) {
            sender.sendRichMessage("<gray>According to Bukkit: <white>" + entity);
            sender.sendRichMessage("<gray>Entity ID: <white>" + entity.getEntityId());
            sender.sendRichMessage("<gray>Entity UUID: <white>" + entity.getUniqueId());
            sender.sendRichMessage("<gray>Entity type: <white>" + entity.getType());
            sender.sendRichMessage("<gray>Entity name: <white>" + entity.getName());
            sender.sendRichMessage("<gray>Entity string: <white>" + entity.getAsString());
        }

        private String describeViewer(UUID playerUUID) {
            Player player = Bukkit.getPlayer(playerUUID);
            return player == null ? playerUUID.toString() : player.getName() + " (" + playerUUID + ")";
        }

        @DefaultExecutes
        public void helpCommand(@NotNull CommandSender sender) {
            sender.sendRichMessage("<white>Test subcommands:");
            sender.sendRichMessage("<green>/raycastedantiesp test location-drift <gray>- Tests the drift between Bukkit and PacketEvents entity locations");
            sender.sendRichMessage("<green>/raycastedantiesp test benchmark <gray>- Benchmarks raycast speed by raycasting to 1000 random locatables around the player and printing the average time taken");
            sender.sendRichMessage("<green>/raycastedantiesp test loaded-chunks <gray>- Shows the number of chunks currently loaded in the player's block view");
            sender.sendRichMessage("<green>/raycastedantiesp test entity-id <entity ID> [player] <gray>- Finds an entity by ID in one player's views, or in all player views when no player is supplied");
            sender.sendRichMessage("<green>/raycastedantiesp test entity-uuid <entity> <gray>- Shows Bukkit data and all tracked view data for a native entity selection or UUID");
        }
    }
}
