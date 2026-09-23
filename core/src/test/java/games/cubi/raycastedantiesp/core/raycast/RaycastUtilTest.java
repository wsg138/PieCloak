package games.cubi.raycastedantiesp.core.raycast;

import games.cubi.locatables.api.Locatable;
import games.cubi.locatables.api.Spatial;
import games.cubi.locatables.implementations.ImmutableBlockSpatialImpl;
import games.cubi.locatables.implementations.ImmutableLocatableImpl;
import games.cubi.locatables.implementations.ImmutableSpatialImpl;
import games.cubi.raycastedantiesp.core.view.BlockView;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaycastUtilTest {
    @Test
    void spatialTargetUsesStartWorldForDebugParticles() {
        UUID world = UUID.randomUUID();
        Locatable start = new ImmutableLocatableImpl(world, 0, 0, 0);
        RecordingParticleSpawner particles = new RecordingParticleSpawner();

        assertTrue(RaycastUtil.raycast(start, new ImmutableSpatialImpl(2, 0, 0),
                settings(1, 0, 10, true, emptyBlockView(), particles)));
        assertEquals(List.of(world), particles.worlds);
    }

    @Test
    void blockSpatialTargetIsCentredBeforeRaycast() {
        UUID world = UUID.randomUUID();
        Locatable start = new ImmutableLocatableImpl(world, 0.5, 0.5, 0.5);
        RecordingParticleSpawner particles = new RecordingParticleSpawner();

        assertTrue(RaycastUtil.raycast(start, new ImmutableBlockSpatialImpl(3, 0, 0),
                settings(1, 0, 10, true, emptyBlockView(), particles)));
        assertEquals(0.5, particles.positions.getFirst().y());
        assertEquals(0.5, particles.positions.getFirst().z());
    }

    @Test
    void maxRaycastRadiusUsesActualTargetDistance() {
        UUID world = UUID.randomUUID();
        Locatable start = new ImmutableLocatableImpl(world, 0, 0, 0);

        assertFalse(RaycastUtil.raycast(
                start,
                new ImmutableSpatialImpl(48.5, 0, 0),
                settings(1, 0, 48, false, emptyBlockView(), null)));
    }

    @Test
    void alwaysShowRadiusUsesActualTargetDistance() {
        UUID world = UUID.randomUUID();
        Locatable start = new ImmutableLocatableImpl(world, 0, 0, 0);

        assertFalse(RaycastUtil.raycast(
                start,
                new ImmutableSpatialImpl(24.5, 0, 0),
                settings(1, 24, 48, false, fullyOccludingBlockView(), null)));
    }

    @Test
    void exactTraversalChecksVoxelsSkippedByOneBlockSampling() {
        UUID world = UUID.randomUUID();
        Locatable start = new ImmutableLocatableImpl(world, 0.5, 0.5, 0.5);
        Spatial target = new ImmutableSpatialImpl(5.5, 1.5, 2.5);

        assertFalse(RaycastUtil.raycast(
                start, target, settings(1, 0, 48, false, occludingAt(1, 0, 1), null)));
    }

    @Test
    void targetVoxelDoesNotOccludeItself() {
        UUID world = UUID.randomUUID();
        Locatable start = new ImmutableLocatableImpl(world, 0.5, 0.5, 0.5);

        assertTrue(RaycastUtil.raycast(
                start,
                new ImmutableBlockSpatialImpl(3, 0, 0),
                settings(1, 0, 48, false, occludingAt(3, 0, 0), null)));
    }

    @Test
    void blockTargetNeedsNoExtraOccluderCompensation() {
        UUID world = UUID.randomUUID();
        Locatable start = new ImmutableLocatableImpl(world, 0.5, 0.5, 0.5);
        Spatial target = new ImmutableBlockSpatialImpl(4, 0, 0);
        BlockView blocks = fullyOccludingBlockView();

        assertFalse(RaycastUtil.raycast(start, target, settings(3, 0, 48, false, blocks, null)));
        assertTrue(RaycastUtil.raycast(start, target, settings(4, 0, 48, false, blocks, null)));
    }

    private static RaycastUtil.Settings settings(
            int maxOccluding,
            int alwaysShowRadius,
            int maxRaycastRadius,
            boolean debug,
            BlockView blockView,
            ParticleSpawner particleSpawner) {
        return new RaycastUtil.Settings(
                maxOccluding, alwaysShowRadius, maxRaycastRadius, debug, blockView, particleSpawner);
    }

    private static BlockView emptyBlockView() {
        return blockView(false);
    }

    private static BlockView fullyOccludingBlockView() {
        return blockView(true);
    }

    private static BlockView blockView(boolean occluding) {
        return (BlockView) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{BlockView.class},
                (proxy, method, args) -> method.getReturnType() == boolean.class ? occluding : null
        );
    }

    private static BlockView occludingAt(int x, int y, int z) {
        return (BlockView) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{BlockView.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("isBlockOccluding")
                            && args != null && args.length == 3) {
                        return (Integer) args[0] == x
                                && (Integer) args[1] == y
                                && (Integer) args[2] == z;
                    }
                    return method.getReturnType() == boolean.class ? false : null;
                }
        );
    }

    private static final class RecordingParticleSpawner implements ParticleSpawner {
        private final List<UUID> worlds = new ArrayList<>();
        private final List<Spatial> positions = new ArrayList<>();

        @Override
        public void spawnParticleAt(Locatable locatable, Colour colour) {
            spawnParticleAt(locatable.world(), locatable, colour);
        }

        @Override
        public void spawnParticleAt(UUID world, Spatial spatial, Colour colour) {
            worlds.add(world);
            positions.add(new ImmutableSpatialImpl(spatial.x(), spatial.y(), spatial.z()));
        }
    }
}
