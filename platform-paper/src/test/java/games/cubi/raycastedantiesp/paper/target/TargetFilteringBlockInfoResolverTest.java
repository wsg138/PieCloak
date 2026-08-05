package games.cubi.raycastedantiesp.paper.target;

import games.cubi.raycastedantiesp.core.chunks.BlockInfoResolver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TargetFilteringBlockInfoResolverTest {
    @Test
    void filtersOnlyManagedTileEntityCapability() {
        BlockInfoResolver delegate = new BlockInfoResolver() {
            @Override public boolean isOccluding(int blockStateID) { return blockStateID == 7; }
            @Override public boolean isTileEntity(int blockStateID) { return blockStateID == 7 || blockStateID == 8; }
            @Override public boolean hasBlockEntityData(int blockStateID) { return blockStateID == 7 || blockStateID == 8; }
        };

        TargetFilteringBlockInfoResolver resolver = new TargetFilteringBlockInfoResolver(delegate, blockStateID -> blockStateID == 7);
        assertTrue(resolver.isOccluding(7));
        assertTrue(resolver.isTileEntity(7));
        assertFalse(resolver.isTileEntity(8));
        assertTrue(resolver.hasBlockEntityData(8));
    }
}
