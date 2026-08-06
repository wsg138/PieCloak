package games.cubi.raycastedantiesp.packetevents.view;

import games.cubi.locatables.implementations.ImmutableBlockSpatialImpl;
import games.cubi.raycastedantiesp.core.chunks.BlockInfoResolver;
import games.cubi.raycastedantiesp.core.chunks.OccludingChunkData;
import games.cubi.raycastedantiesp.core.view.BlockView;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PacketEventsBlockViewTrackingTest {
    private static final BlockInfoResolver RESOLVER = new BlockInfoResolver() {
        @Override
        public boolean isOccluding(int blockStateID) {
            return blockStateID == 7;
        }

        @Override
        public boolean isTileEntity(int blockStateID) {
            return blockStateID == 5;
        }

        @Override
        public boolean hasBlockEntityData(int blockStateID) {
            return blockStateID == 5 || blockStateID == 6;
        }
    };

    @Test
    void packetBlockUpdatesClassifyOnlyThePositionsTheyAuthoritativelyReplace() {
        assertPartialClassification(true);
        assertPartialClassification(false);
    }

    @Test
    void authoritativeChunkSectionsClassifyManagedRawAndOrdinaryPositions() {
        assertAuthoritativeClassification(true);
        assertAuthoritativeClassification(false);
    }

    private static void assertPartialClassification(boolean trackAllBlocks) {
        UUID world = UUID.randomUUID();
        PacketEventsBlockView view = new PacketEventsBlockView(RESOLVER, trackAllBlocks, () -> 2);
        ImmutableBlockSpatialImpl managed = new ImmutableBlockSpatialImpl(3, 64, 5);
        ImmutableBlockSpatialImpl rawNonManaged = new ImmutableBlockSpatialImpl(4, 64, 5);
        ImmutableBlockSpatialImpl ordinary = new ImmutableBlockSpatialImpl(5, 64, 5);
        ImmutableBlockSpatialImpl untouched = new ImmutableBlockSpatialImpl(6, 64, 5);

        assertEquals(BlockView.BlockEntityStatus.UNKNOWN, view.getBlockEntityStatus(world, managed));
        view.upsertBlock(world, managed.blockX(), managed.blockY(), managed.blockZ(), 5);
        view.upsertBlock(world, rawNonManaged.blockX(), rawNonManaged.blockY(), rawNonManaged.blockZ(), 6);
        view.upsertBlock(world, ordinary.blockX(), ordinary.blockY(), ordinary.blockZ(), 7);

        assertEquals(BlockView.BlockEntityStatus.MANAGED, view.getBlockEntityStatus(world, managed));
        assertEquals(BlockView.BlockEntityStatus.NON_MANAGED, view.getBlockEntityStatus(world, rawNonManaged));
        assertEquals(BlockView.BlockEntityStatus.NON_MANAGED, view.getBlockEntityStatus(world, ordinary));
        assertEquals(BlockView.BlockEntityStatus.UNKNOWN, view.getBlockEntityStatus(world, untouched));
    }

    private static void assertAuthoritativeClassification(boolean trackAllBlocks) {
        UUID world = UUID.randomUUID();
        PacketEventsBlockView view = new PacketEventsBlockView(RESOLVER, trackAllBlocks, () -> 2);
        int chunkX = 2;
        int sectionY = 4;
        int chunkZ = -1;
        ImmutableBlockSpatialImpl managed = new ImmutableBlockSpatialImpl(33, 66, -13);
        ImmutableBlockSpatialImpl rawNonManaged = new ImmutableBlockSpatialImpl(34, 66, -13);
        ImmutableBlockSpatialImpl ordinary = new ImmutableBlockSpatialImpl(35, 66, -13);
        ImmutableBlockSpatialImpl outside = new ImmutableBlockSpatialImpl(49, 66, -13);

        OccludingChunkData managedBits = OccludingChunkData.empty();
        managedBits = managedBits.setOccluding(1, 2, 3, true);
        view.replaceChunkSectionBlockEntities(world, chunkX, sectionY, chunkZ, managedBits);

        assertEquals(BlockView.BlockEntityStatus.MANAGED, view.getBlockEntityStatus(world, managed));
        assertEquals(BlockView.BlockEntityStatus.NON_MANAGED, view.getBlockEntityStatus(world, rawNonManaged));
        assertEquals(BlockView.BlockEntityStatus.NON_MANAGED, view.getBlockEntityStatus(world, ordinary));
        assertEquals(BlockView.BlockEntityStatus.UNKNOWN, view.getBlockEntityStatus(world, outside));
    }
}
