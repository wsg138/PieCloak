package games.cubi.raycastedantiesp.packetevents.view;

import games.cubi.locatables.implementations.ImmutableBlockSpatialImpl;
import games.cubi.raycastedantiesp.core.chunks.BlockInfoResolver;
import games.cubi.raycastedantiesp.core.tracked.TrackedTileEntity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketEventsBlockModeRepairTest {
    private static final BlockInfoResolver RESOLVER = new BlockInfoResolver() {
        @Override
        public boolean isOccluding(int blockStateID) {
            return false;
        }

        @Override
        public boolean isTileEntity(int blockStateID) {
            return blockStateID != 0;
        }

        @Override
        public boolean hasBlockEntityData(int blockStateID) {
            return blockStateID != 0;
        }
    };

    @Test
    void disablingChecksCommitsDesiredVisibilityForEveryTileDespiteOneRepairFailure() {
        PacketEventsBlockView view = new PacketEventsBlockView(RESOLVER, true, () -> 2);
        UUID world = UUID.randomUUID();
        view.applyTileEntityCheckMode(true, 0, ignored -> {});

        List<TrackedTileEntity<?>> tiles = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            tiles.add(view.updateOrInsertTileEntity(
                    world, new ImmutableBlockSpatialImpl(index, 64, 0), (char) 5, false));
        }

        AtomicInteger repairs = new AtomicInteger();
        assertThrows(IllegalStateException.class,
                () -> view.applyTileEntityCheckMode(false, 10, tile -> {
                    if (repairs.incrementAndGet() == 2) {
                        throw new IllegalStateException("injected repair failure");
                    }
                }));

        assertEquals(3, repairs.get());
        assertTrue(tiles.stream().allMatch(TrackedTileEntity::visible));
        assertEquals(0L, view.tileEntityCheckModeToken() & 1L);
    }
}
