package games.cubi.raycastedantiesp.core.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TargetFilterConfigTest {
    @Test
    void normalizesBareAndNamespacedKeysIdentically() {
        assertEquals(
                TargetFilterConfig.normalizeKey("villager"),
                TargetFilterConfig.normalizeKey("minecraft:villager"));
    }
}