package games.cubi.raycastedantiesp.core.config.raycast;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockSelectorTest {
    private static final String EXAMPLE_BLOCK = "example:block";

    @Test
    void blockKeySelectsAllStates() {
        BlockSelector selector = BlockSelector.parse(EXAMPLE_BLOCK);

        assertEquals(EXAMPLE_BLOCK, selector.blockKey());
        assertTrue(selector.matchesAllStates());
        assertEquals(EXAMPLE_BLOCK, selector.toString());
    }

    @Test
    void propertySelectorPreservesItsText() {
        BlockSelector selector = BlockSelector.parse("example:block[type=left,facing=north]");

        assertFalse(selector.matchesAllStates());
        assertEquals(EXAMPLE_BLOCK, selector.blockKey());
        assertEquals("example:block[type=left,facing=north]", selector.toString());
    }

    @Test
    void malformedSelectorsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> BlockSelector.parse("block"));
        assertThrows(IllegalArgumentException.class, () -> BlockSelector.parse("Example:block"));
        assertThrows(IllegalArgumentException.class, () -> BlockSelector.parse("example:block[]"));
        assertThrows(IllegalArgumentException.class, () -> BlockSelector.parse("example:block[type]"));
        assertThrows(IllegalArgumentException.class, () -> BlockSelector.parse("example:block[type=left,type=right]"));
        assertThrows(IllegalArgumentException.class, () -> BlockSelector.parse("example:block[type=left"));
    }
}
