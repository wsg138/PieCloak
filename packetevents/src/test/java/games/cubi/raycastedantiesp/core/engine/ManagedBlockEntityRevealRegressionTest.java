package games.cubi.raycastedantiesp.core.engine;

import games.cubi.locatables.api.Locatable;
import games.cubi.locatables.implementations.ImmutableBlockSpatialImpl;
import games.cubi.locatables.implementations.ImmutableLocatableImpl;
import games.cubi.raycastedantiesp.core.chunks.BlockInfoResolver;
import games.cubi.raycastedantiesp.core.raycast.RaycastUtil;
import games.cubi.raycastedantiesp.core.tracked.TrackedTileEntity;
import games.cubi.raycastedantiesp.core.view.BlockView;
import games.cubi.raycastedantiesp.core.view.BlockViewTransition;
import games.cubi.raycastedantiesp.packetevents.view.PacketEventsBlockView;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManagedBlockEntityRevealRegressionTest {
    private static final BlockInfoResolver RESOLVER = new BlockInfoResolver() {
        @Override public boolean isOccluding(int blockStateID) { return false; }
        @Override public boolean isTileEntity(int blockStateID) { return blockStateID != 0; }
        @Override public boolean hasBlockEntityData(int blockStateID) { return blockStateID != 0; }
    };

    @Test
    void initiallyHiddenManagedBlockInsideAlwaysShowRadiusPublishesShowTransition() {
        UUID world = UUID.randomUUID();
        int worldEpoch = 2;
        Locatable viewer = new ImmutableLocatableImpl(world, 0.5, 64.5, 0.5);
        ImmutableBlockSpatialImpl targetLocation = new ImmutableBlockSpatialImpl(4, 64, 0);
        PacketEventsBlockView view = new PacketEventsBlockView(RESOLVER, true, () -> worldEpoch);
        view.applyTileEntityCheckMode(true, 0, unused -> { });
        TrackedTileEntity<?> target = view.updateOrInsertTileEntity(world, targetLocation, (char) 1, false);
        long modeToken = view.tileEntityCheckModeToken();
        RaycastUtil.Settings settings = new RaycastUtil.Settings(3, 24, 48, false, view, null);

        int checked = view.updateVisibilityForEachNeedingRecheck(
                20,
                10,
                modeToken,
                worldEpoch,
                tile -> RaycastUtil.raycast(viewer, tile, settings)
                        ? BlockView.VisibilityResolver.SHOW
                        : BlockView.VisibilityResolver.HIDE);

        assertEquals(1, checked);
        assertTrue(target.visible(), "the first async block recheck must reveal a nearby hidden managed block");

        view.flushPendingTransitions();
        AtomicReference<BlockViewTransition.Type> transitionType = new AtomicReference<>();
        AtomicReference<TrackedTileEntity<?>> transitionedTile = new AtomicReference<>();
        AtomicLong transitionModeToken = new AtomicLong();
        AtomicInteger transitionEpoch = new AtomicInteger();
        view.drainTransitions((type, tile, queuedModeToken, queuedWorldEpoch) -> {
            transitionType.set(type);
            transitionedTile.set(tile);
            transitionModeToken.set(queuedModeToken);
            transitionEpoch.set(queuedWorldEpoch);
        });

        assertEquals(BlockViewTransition.Type.SHOW, transitionType.get());
        assertSame(target, transitionedTile.get());
        assertEquals(modeToken, transitionModeToken.get());
        assertEquals(worldEpoch, transitionEpoch.get());
    }
}
