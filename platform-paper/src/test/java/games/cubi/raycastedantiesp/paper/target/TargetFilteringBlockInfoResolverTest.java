package games.cubi.raycastedantiesp.paper.target;

import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.world.blockentity.BlockEntityType;
import games.cubi.raycastedantiesp.core.chunks.BlockInfoResolver;
import games.cubi.raycastedantiesp.packetevents.target.PacketEventsTargetFilter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TargetFilteringBlockInfoResolverTest {
    @Test
    void filtersOnlyManagedTileEntityCapability() {
        BlockInfoResolver delegate = delegate();

        TargetFilteringBlockInfoResolver resolver =
                new TargetFilteringBlockInfoResolver(delegate, blockStateID -> blockStateID == 7);
        assertTrue(resolver.isOccluding(7));
        assertTrue(resolver.isTileEntity(7));
        assertFalse(resolver.isTileEntity(8));
        assertTrue(resolver.hasBlockEntityData(8));
    }

    @Test
    void exposesThePacketTargetClassifierUsedByTheBlockController() {
        PacketEventsTargetFilter filter = new PacketEventsTargetFilter() {
            @Override
            public boolean shouldCullEntity(EntityType entityType, boolean isPlayer) {
                return false;
            }

            @Override
            public boolean shouldCullBlockState(int blockStateId) {
                return blockStateId == 7;
            }

            @Override
            public boolean shouldCullBlockEntity(BlockEntityType blockEntityType) {
                return true;
            }

            @Override
            public boolean shouldCullTileEntity(int blockStateId) {
                return blockStateId == 7;
            }
        };

        TargetFilteringBlockInfoResolver resolver = new TargetFilteringBlockInfoResolver(delegate(), filter);

        assertTrue(resolver.shouldCullBlockEntity(null));
        assertTrue(resolver.shouldCullBlockState(7));
        assertTrue(resolver.shouldCullTileEntity(7));
        assertFalse(resolver.shouldCullTileEntity(8));
    }

    private static BlockInfoResolver delegate() {
        return new BlockInfoResolver() {
            @Override
            public boolean isOccluding(int blockStateID) {
                return blockStateID == 7;
            }

            @Override
            public boolean isTileEntity(int blockStateID) {
                return blockStateID == 7 || blockStateID == 8;
            }

            @Override
            public boolean hasBlockEntityData(int blockStateID) {
                return blockStateID == 7 || blockStateID == 8;
            }
        };
    }
}
