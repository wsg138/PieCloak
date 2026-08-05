package games.cubi.raycastedantiesp.core.config.engine;

import games.cubi.raycastedantiesp.core.config.Config;
import games.cubi.raycastedantiesp.core.config.ConfigLoadException;
import games.cubi.raycastedantiesp.core.config.ConfigReader;
import org.spongepowered.configurate.ConfigurationNode;

public record AsyncEngineConfig(int asyncProcessingThreads) implements Config {
    public static AsyncEngineConfig load(ConfigurationNode node) {
        int threads = ConfigReader.integer(ConfigReader.node(node, "processing-threads"), "engine.async.processing-threads");
        if (threads < 1) {
            throw new ConfigLoadException("engine.async.processing-threads must be at least 1");
        }
        return new AsyncEngineConfig(threads);
    }
}
