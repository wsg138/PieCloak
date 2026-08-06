package games.cubi.raycastedantiesp.core.config.raycast;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityTypeExclusionsLifecycleTest {
    @AfterEach
    void reset() {
        EntityTypeExclusions.reset();
    }

    @Test
    void resetAllowsFreshResolvedPolicyOnReenable() {
        EntityTypeExclusions.initialise(new IntOpenHashSet(new int[]{1, 2}));
        assertTrue(EntityTypeExclusions.excludes(1));
        assertThrows(IllegalStateException.class,
                () -> EntityTypeExclusions.initialise(new IntOpenHashSet(new int[]{3})));

        EntityTypeExclusions.reset();
        EntityTypeExclusions.initialise(new IntOpenHashSet(new int[]{3}));

        assertFalse(EntityTypeExclusions.excludes(1));
        assertTrue(EntityTypeExclusions.excludes(3));
    }
}
