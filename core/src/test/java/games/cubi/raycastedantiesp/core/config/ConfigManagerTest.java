package games.cubi.raycastedantiesp.core.config;

import games.cubi.raycastedantiesp.core.config.engine.EngineMode;
import games.cubi.raycastedantiesp.core.config.raycast.BlockSelector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.NodePath;
import org.spongepowered.configurate.loader.HeaderMode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigManagerTest {
    private static final String INVALID = "invalid";
    private static final String UPDATES = "updates";
    private static final String CHECKS = "checks";
    private static final String DEBUG = "debug";
    private static final String BLOCK_PROCESSOR = "block-processor";
    private static final String ENABLED = "enabled";
    private static final String ENTITY = "entity";
    private static final String EXCLUDED_BLOCKS = "excluded-blocks";
    private static final String CONFIG_VERSION = "config-version";
    private static final String TARGET_FILTER = "target-filter";
    private static final String MODE = "mode";

    @TempDir
    Path directory;

    @Test
    void freshConfigLoadsInTwoPhasesFromCodeDefaults() {
        ConfigManager manager = new ConfigManager(directory);

        assertEquals(LoggingConfig.DEFAULT, manager.getLoggingConfig());
        assertFalse(Files.exists(configPath()));
        manager.loadLoggingConfig();

        assertEquals(RootConfig.DEFAULT.loggingConfig(), manager.getLoggingConfig());
        assertThrows(IllegalStateException.class, manager::getDebugConfig);
        assertThrows(IllegalStateException.class, manager::getConfigValues);

        manager.completeInitialLoad();

        assertEquals(RootConfig.DEFAULT.debugConfig(), manager.getDebugConfig());
        assertEquals(RootConfig.DEFAULT.engineConfig(), manager.getEngineConfig());
        assertEquals(RootConfig.DEFAULT.checksConfig().playerConfig(), manager.getPlayerConfig());
        assertTrue(Files.isRegularFile(configPath()));
    }

    @Test
    void lateCompletionCanOnlyRunOnce() {
        ConfigManager manager = earlyManager();

        manager.completeInitialLoad();

        assertThrows(IllegalStateException.class, manager::completeInitialLoad);
    }

    @Test
    void malformedSectionsUseTheirDefaultsWithoutLosingOtherOverrides() throws IOException {
        CommentedConfigurationNode node = defaultNode();
        boolean particles = !RootConfig.DEFAULT.debugConfig().particles();
        node.node("logging").raw(INVALID);
        node.node(UPDATES).raw(List.of(INVALID));
        node.node(CHECKS, "player").comment("User-authored comment").raw(INVALID);
        node.node(DEBUG, "particles").raw(particles);
        saveNode(node);

        ConfigManager manager = earlyManager();
        assertEquals(LoggingConfig.DEFAULT, manager.getLoggingConfig());
        manager.completeInitialLoad();

        assertEquals(UpdateConfig.DEFAULT, manager.getUpdateConfig());
        assertEquals(RootConfig.DEFAULT.checksConfig().playerConfig(), manager.getPlayerConfig());
        assertEquals(particles, manager.getDebugConfig().particles());
        assertEquals("User-authored comment", loadNode().node(CHECKS, "player").comment());
    }

    @Test
    void commandRepairsScalarAndListSectionsBeforePublishing() {
        ConfigManager manager = earlyManager();
        manager.completeInitialLoad();

        for (String path : List.of("logging", UPDATES, "checks.player")) {
            for (String rawValue : List.of(INVALID, "[invalid]")) {
                ConfigManager.SetConfigResult result = manager.setConfigValue(path, rawValue);

                assertTrue(result.success(), path + ": " + rawValue);
                assertTrue(result.repaired(), path + ": " + rawValue);
                assertEquals(LoggingConfig.DEFAULT, manager.getLoggingConfig());
                assertEquals(UpdateConfig.DEFAULT, manager.getUpdateConfig());
                assertEquals(RootConfig.DEFAULT.checksConfig().playerConfig(), manager.getPlayerConfig());
            }
        }
    }

    @Test
    void invalidValueAndCrossFieldViolationRepairOnlyTheirFields() throws IOException {
        ConfigManager manager = earlyManager();
        CommentedConfigurationNode node = loadNode();
        Object unrelatedValue = node.node(UPDATES, "notify-in-game").raw();
        node.node(BLOCK_PROCESSOR, "track-all-blocks").raw(false);
        node.node(CHECKS, "chunk-section", ENABLED).raw(true);
        saveNode(node);

        manager.completeInitialLoad();
        ConfigManager.SetConfigResult result = manager.setConfigValue("engine.async.processing-threads", "0");

        assertTrue(result.success());
        assertTrue(result.repaired());
        assertEquals(RootConfig.DEFAULT.engineConfig().asyncConfig(), manager.getEngineConfig().asyncConfig());
        assertEquals(RootConfig.DEFAULT.checksConfig().chunkSectionConfig().enabled(), manager.getChunkSectionConfig().enabled());
        assertEquals(unrelatedValue, loadNode().node(UPDATES, "notify-in-game").raw());
    }

    @Test
    void invalidListElementResetsItsListWithoutLosingOtherOverrides() throws IOException {
        ConfigManager manager = earlyManager();
        CommentedConfigurationNode node = loadNode();
        boolean onlyCheckSneaking = !RootConfig.DEFAULT.checksConfig().playerConfig().onlyCheckSneaking();
        node.node(CHECKS, ENTITY, "excluded-types").raw(List.of(1));
        node.node(CHECKS, "player", "only-check-sneaking").raw(onlyCheckSneaking);
        saveNode(node);

        manager.completeInitialLoad();

        assertEquals(RootConfig.DEFAULT.checksConfig().entityConfig().excludedTypes(), manager.getEntityConfig().excludedTypes());
        assertEquals(onlyCheckSneaking, manager.getPlayerConfig().onlyCheckSneaking());
    }

    @Test
    void invalidBlockSelectorResetsItsListWithoutLosingOtherOverrides() throws IOException {
        ConfigManager manager = earlyManager();
        CommentedConfigurationNode node = loadNode();
        boolean onlyCheckSneaking = !RootConfig.DEFAULT.checksConfig().playerConfig().onlyCheckSneaking();
        node.node(CHECKS, "tile-entity", EXCLUDED_BLOCKS).raw(List.of("minecraft:chest[]"));
        node.node(CHECKS, "player", "only-check-sneaking").raw(onlyCheckSneaking);
        saveNode(node);

        manager.completeInitialLoad();

        assertEquals(RootConfig.DEFAULT.checksConfig().tileEntityConfig().excludedBlocks(), manager.getTileEntityConfig().excludedBlocks());
        assertEquals(onlyCheckSneaking, manager.getPlayerConfig().onlyCheckSneaking());
    }

    @Test
    void excludedBlockMutationIsSavedForRestartWithoutChangingActiveSnapshot() throws IOException {
        ConfigManager manager = earlyManager();
        manager.completeInitialLoad();
        BlockSelector added = BlockSelector.parse("example:block");

        ConfigManager.SetConfigResult result = manager.addConfigListValue("checks.tile-entity.excluded-blocks", added.toString());

        assertTrue(result.success());
        assertTrue(result.restartRequired());
        assertEquals(RootConfig.DEFAULT.checksConfig().tileEntityConfig().excludedBlocks(), manager.getTileEntityConfig().excludedBlocks());
        assertTrue(loadNode().node(CHECKS, "tile-entity", EXCLUDED_BLOCKS).getList(String.class).contains(added.toString()));
    }

    @Test
    void migrationPrefersExistingDestinationAndPreservesUnrelatedValues() throws IOException {
        CommentedConfigurationNode node = defaultNode();
        byte destinationLevel = differentValidLevel(RootConfig.DEFAULT.loggingConfig().info().level());
        byte legacyLevel = differentValidLevel(destinationLevel);
        boolean soundEnabled = !RootConfig.DEFAULT.checksConfig().soundEffectsConfig().enabled();
        String unknownValue = "preserve-me";
        node.node(CONFIG_VERSION).raw("2.0");
        node.node("logging", "info", "level").raw(destinationLevel);
        node.node(DEBUG, "info-level").raw(legacyLevel);
        node.node(CHECKS, "sound-effects", ENABLED).raw(soundEnabled);
        node.node(CHECKS, "tile-entity", EXCLUDED_BLOCKS).raw(null);
        node.node("unknown-setting").raw(unknownValue);
        node.node(BLOCK_PROCESSOR, "packetevents", "tile-entity-exempted-ids").raw(List.of(1));
        saveNode(node);

        ConfigManager manager = earlyManager();
        manager.completeInitialLoad();
        CommentedConfigurationNode migrated = loadNode();

        assertEquals(destinationLevel, manager.getLoggingConfig().info().level());
        assertEquals(soundEnabled, manager.getSoundEffectsConfig().enabled());
        assertEquals(RootConfig.CURRENT_VERSION, migrated.node(CONFIG_VERSION).getString());
        assertTrue(migrated.node(DEBUG, "info-level").virtual());
        assertEquals(List.of(1), migrated.node(BLOCK_PROCESSOR, "packetevents", "tile-entity-exempted-ids").getList(Integer.class));
        assertTrue(Files.isRegularFile(configPath().resolveSibling("config.yml.pre-2.1.bak")));
        assertEquals(
                RootConfig.DEFAULT.checksConfig().tileEntityConfig().excludedBlocks().stream().map(BlockSelector::toString).toList(),
                migrated.node(CHECKS, "tile-entity", EXCLUDED_BLOCKS).getList(String.class)
        );
        assertEquals(unknownValue, migrated.node("unknown-setting").getString());

        String afterMigration = Files.readString(configPath());
        manager.load();
        assertEquals(afterMigration, Files.readString(configPath()));
    }

    @Test
    void migratesForkAllowlistWithoutReintroducingDefaultsOrLosingExplicitFalse() throws IOException {
        CommentedConfigurationNode node = defaultNode();
        node.node(CONFIG_VERSION).raw("2.0");
        node.node(TARGET_FILTER, ENABLED).raw(false);
        node.node(TARGET_FILTER, "entities").raw(List.of());
        node.node(TARGET_FILTER, "block-entities").raw(List.of("minecraft:bell"));
        node.node(TARGET_FILTER, "block-entity-groups").raw(List.of());
        node.node(CHECKS, "player", ENABLED).raw(false);
        saveNode(node);
        byte[] original = Files.readAllBytes(configPath());

        ConfigManager manager = earlyManager();
        manager.completeInitialLoad();

        assertFalse(manager.getTargetFilterConfig().enabled());
        assertEquals(List.of(), manager.getTargetFilterConfig().entities());
        assertEquals(List.of("minecraft:bell"), manager.getTargetFilterConfig().blockEntities());
        assertEquals(List.of(), manager.getTargetFilterConfig().blockEntityGroups());
        assertFalse(manager.getPlayerConfig().enabled());
        org.junit.jupiter.api.Assertions.assertArrayEquals(original,
                Files.readAllBytes(configPath().resolveSibling("config.yml.pre-2.1.bak")));

        String migrated = Files.readString(configPath());
        manager.load();
        assertEquals(migrated, Files.readString(configPath()));
    }

    @Test
    void acceptedEnumSpellingsArePersistedCanonically() throws IOException {
        CommentedConfigurationNode node = defaultNode();
        node.node("engine", MODE).raw("SiMpLe");
        node.node(BLOCK_PROCESSOR, MODE).raw(
                RootConfig.DEFAULT.blockProcessorConfig().mode().getName().toUpperCase(java.util.Locale.ROOT)
        );
        saveNode(node);

        ConfigManager manager = earlyManager();
        manager.completeInitialLoad();
        CommentedConfigurationNode canonical = loadNode();

        assertEquals(EngineMode.ASYNC, manager.getEngineConfig().mode());
        assertEquals(EngineMode.ASYNC.getName(), canonical.node("engine", MODE).raw());
        assertEquals(
                RootConfig.DEFAULT.blockProcessorConfig().mode().getName(),
                canonical.node(BLOCK_PROCESSOR, MODE).raw()
        );
    }

    @Test
    void invalidScalarShapeAndEnumUseTheirFieldDefaults() {
        ConfigManager manager = earlyManager();
        manager.completeInitialLoad();

        ConfigManager.SetConfigResult scalarResult = manager.setConfigValue("debug.timings", "{invalid: shape}");
        ConfigManager.SetConfigResult enumResult = manager.setConfigValue("engine.mode", "definitely-invalid");

        assertTrue(scalarResult.success());
        assertTrue(scalarResult.repaired());
        assertEquals(RootConfig.DEFAULT.debugConfig().timings(), manager.getDebugConfig().timings());
        assertTrue(enumResult.success());
        assertTrue(enumResult.repaired());
        assertEquals(RootConfig.DEFAULT.engineConfig().mode(), manager.getEngineConfig().mode());
    }

    @Test
    void commandSectionsFillMissingFieldsAndNullUsesTheFieldDefault() {
        ConfigManager manager = earlyManager();
        manager.completeInitialLoad();
        boolean particles = !RootConfig.DEFAULT.debugConfig().particles();

        ConfigManager.SetConfigResult sectionResult = manager.setConfigValue(
                DEBUG,
                "{particles: " + particles + "}"
        );
        ConfigManager.SetConfigResult nullResult = manager.setConfigValue("updates.notify-in-game", "null");

        assertTrue(sectionResult.success());
        assertEquals(particles, manager.getDebugConfig().particles());
        assertEquals(RootConfig.DEFAULT.debugConfig().timings(), manager.getDebugConfig().timings());
        assertTrue(nullResult.success());
        assertTrue(nullResult.repaired());
        assertEquals(RootConfig.DEFAULT.updateConfig().notifyInGame(), manager.getUpdateConfig().notifyInGame());
    }

    @Test
    void fatalReloadRetainsActiveSnapshotAndDoesNotRewriteFile() throws IOException {
        ConfigManager manager = earlyManager();
        manager.completeInitialLoad();
        LoggingConfig activeLogging = manager.getLoggingConfig();
        CommentedConfigurationNode node = loadNode();
        String unsupportedVersion = RootConfig.CURRENT_VERSION + "-future";
        node.node(CONFIG_VERSION).raw(unsupportedVersion);
        saveNode(node);
        String beforeReload = Files.readString(configPath());

        assertThrows(ConfigLoadException.class, manager::load);

        assertEquals(activeLogging, manager.getLoggingConfig());
        assertEquals(beforeReload, Files.readString(configPath()));
    }

    @Test
    void commandCannotPublishAnUnsupportedVersion() throws IOException {
        ConfigManager manager = earlyManager();
        manager.completeInitialLoad();
        String beforeMutation = Files.readString(configPath());

        ConfigManager.SetConfigResult result = manager.setConfigValue(
                CONFIG_VERSION,
                "'" + RootConfig.CURRENT_VERSION + "-future'"
        );

        assertFalse(result.success());
        assertEquals(beforeMutation, Files.readString(configPath()));
        assertEquals(RootConfig.CURRENT_VERSION, manager.getConfigValues().get(CONFIG_VERSION));
    }

    @Test
    void malformedYamlIsNotOverwritten() throws IOException {
        String malformed = "invalid: [";
        Files.writeString(configPath(), malformed);
        ConfigManager manager = new ConfigManager(directory);

        assertThrows(ConfigLoadException.class, manager::loadLoggingConfig);
        assertEquals(malformed, Files.readString(configPath()));
    }

    @Test
    void missingMappedCommentsArePersisted() throws IOException {
        CommentedConfigurationNode node = defaultNode();
        CommentedPath expected = firstComment(node, NodePath.path());
        clearComments(node);
        saveNode(node);

        ConfigManager manager = earlyManager();
        manager.completeInitialLoad();

        assertEquals(expected.comment(), loadNode().node(expected.path().array()).comment());
    }

    @Test
    void freshConfigWritesGuidanceAboveIndividualEntries() throws IOException {
        ConfigManager manager = earlyManager();
        manager.completeInitialLoad();
        CommentedConfigurationNode defaults = defaultNode().node(CHECKS, ENTITY, "excluded-types");
        CommentedConfigurationNode saved = loadNode().node(CHECKS, ENTITY, "excluded-types");
        List<String> lines = Files.readAllLines(configPath()).stream().map(String::strip).toList();

        assertEquals(defaults.raw(), saved.raw());
        for (int index = 0; index < defaults.childrenList().size(); index++) {
            String comment = defaults.node(index).comment();
            assertEquals(comment, saved.node(index).comment());
            if (comment != null) {
                String[] commentLines = comment.split("\\R");
                int line = lines.indexOf("# " + commentLines[commentLines.length - 1]);
                assertTrue(line >= 0);
                assertEquals("- " + defaults.node(index).getString(), lines.get(line + 1));
            }
        }
    }

    @Test
    void missingEntryCommentsArePersistedWithoutOtherChanges() throws IOException {
        CommentedConfigurationNode node = defaultNode();
        CommentedConfigurationNode list = node.node(CHECKS, ENTITY, "excluded-types");
        for (CommentedConfigurationNode child : list.childrenList()) {
            child.comment(null);
        }
        saveNode(node);

        ConfigManager manager = earlyManager();
        manager.completeInitialLoad();
        CommentedConfigurationNode defaults = defaultNode().node(CHECKS, ENTITY, "excluded-types");
        CommentedConfigurationNode saved = loadNode().node(CHECKS, ENTITY, "excluded-types");

        assertEquals(defaults.raw(), saved.raw());
        for (int index = 0; index < defaults.childrenList().size(); index++) {
            assertEquals(defaults.node(index).comment(), saved.node(index).comment());
        }

        String content = Files.readString(configPath());
        Files.setLastModifiedTime(configPath(), FileTime.fromMillis(1_000));
        FileTime modified = Files.getLastModifiedTime(configPath());
        manager.load();
        assertEquals(content, Files.readString(configPath()));
        assertEquals(modified, Files.getLastModifiedTime(configPath()));
    }

    @Test
    void entryCommentMergePreservesReorderedSubsetAndUserComments() throws IOException {
        CommentedConfigurationNode node = defaultNode();
        CommentedConfigurationNode list = node.node(CHECKS, ENTITY, "excluded-types");
        List<String> values = List.of("minecraft:block_display", "minecraft:arrow", "example:custom", "minecraft:potion", "minecraft:wither");
        list.raw(values);
        clearComments(list);
        list.comment("Existing consolidated guidance");
        list.node(1).comment("User-authored projectile guidance");
        list.node(2).comment("User-authored custom entry guidance");
        saveNode(node);

        ConfigManager manager = earlyManager();
        manager.completeInitialLoad();
        CommentedConfigurationNode saved = loadNode().node(CHECKS, ENTITY, "excluded-types");
        CommentedConfigurationNode defaults = defaultNode().node(CHECKS, ENTITY, "excluded-types");

        assertEquals(values, saved.getList(String.class));
        assertEquals("Existing consolidated guidance", saved.comment());
        assertEquals("User-authored projectile guidance", saved.node(1).comment());
        assertEquals("User-authored custom entry guidance", saved.node(2).comment());
        assertNull(saved.node(4).comment());
        for (CommentedConfigurationNode child : defaults.childrenList()) {
            if (values.get(0).equals(child.getString())) {
                assertEquals(child.comment(), saved.node(0).comment());
            } else if (values.get(3).equals(child.getString())) {
                assertEquals(child.comment(), saved.node(3).comment());
            }
        }
    }

    private ConfigManager earlyManager() {
        ConfigManager manager = new ConfigManager(directory);
        manager.loadLoggingConfig();
        return manager;
    }

    private Path configPath() {
        return directory.resolve("config.yml");
    }

    private CommentedConfigurationNode defaultNode() {
        CommentedConfigurationNode node = YamlConfigurationLoader.builder()
                .defaultOptions(ConfigMapping.options())
                .build()
                .createNode();
        try {
            node.set(RootConfig.class, RootConfig.DEFAULT);
        } catch (SerializationException e) {
            throw new AssertionError("Code defaults must serialize", e);
        }
        return node;
    }

    private CommentedConfigurationNode loadNode() throws IOException {
        return loader().load();
    }

    private void saveNode(CommentedConfigurationNode node) throws IOException {
        loader().save(node);
    }

    private YamlConfigurationLoader loader() {
        return YamlConfigurationLoader.builder()
                .path(configPath())
                .nodeStyle(NodeStyle.BLOCK)
                .headerMode(HeaderMode.PRESERVE)
                .defaultOptions(ConfigMapping.options())
                .build();
    }

    private byte differentValidLevel(byte level) {
        return level == 0 ? (byte) 1 : (byte) 0;
    }

    private CommentedPath firstComment(CommentedConfigurationNode node, NodePath path) {
        if (node.comment() != null) {
            return new CommentedPath(path, node.comment());
        }
        for (var entry : node.childrenMap().entrySet()) {
            CommentedPath found = firstComment(
                    (CommentedConfigurationNode) entry.getValue(),
                    path.plus(NodePath.path(entry.getKey()))
            );
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private void clearComments(CommentedConfigurationNode node) {
        node.comment(null);
        for (var child : node.childrenMap().values()) {
            clearComments((CommentedConfigurationNode) child);
        }
        for (var child : node.childrenList()) {
            clearComments((CommentedConfigurationNode) child);
        }
    }

    private record CommentedPath(NodePath path, String comment) {
    }

}
