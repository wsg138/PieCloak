package games.cubi.raycastedantiesp.core.raycast;

import games.cubi.locatables.api.BlockSpatial;
import games.cubi.locatables.api.Locatable;
import games.cubi.locatables.api.Spatial;
import games.cubi.locatables.implementations.ImmutableSpatialImpl;
import games.cubi.logs.Logger;
import games.cubi.raycastedantiesp.core.view.BlockView;

public class RaycastUtil {
    private static final double AXIS_TIE_EPSILON = 1.0E-12;

    // True: has line-of-sight.
    // Traverse every voxel whose interior the centre ray enters. The start and target voxels are
    // intentionally excluded so the viewer's own block and the target block cannot occlude themselves.
    public static boolean raycast(Locatable start, Spatial end, int maxOccluding, int alwaysShowRadius,
            int maxRaycastRadius, boolean debug, BlockView snap, int stepSize,
            ParticleSpawner particleSpawner) {
        return raycast(start, end, maxOccluding, alwaysShowRadius, maxRaycastRadius,
                debug, snap, 0f, stepSize, particleSpawner);
    }

    public static boolean raycast(Locatable start, Spatial end, int maxOccluding, int alwaysShowRadius,
            int maxRaycastRadius, boolean debug, BlockView snap, float yOffsetEnd, int stepSize,
            ParticleSpawner particleSpawner) {
        if (stepSize <= 0) {
            throw new IllegalArgumentException("stepSize must be positive");
        }

        double endOffset = end instanceof BlockSpatial ? 0.5 : 0.0;
        double endX = end.x() + endOffset;
        double endY = end.y() + endOffset + yOffsetEnd;
        double endZ = end.z() + endOffset;
        double deltaX = endX - start.x();
        double deltaY = endY - start.y();
        double deltaZ = endZ - start.z();
        double distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);

        if (distance <= alwaysShowRadius) {
            return true;
        }
        if (distance > maxRaycastRadius) {
            return false;
        }
        if (debug && particleSpawner == null) {
            Logger.errorAndReturn(new RuntimeException(
                    "raycast called with debug enabled but no ParticleSpawner supplied"),
                    2, RaycastUtil.class);
        }

        int x = floorBlock(start.x());
        int y = floorBlock(start.y());
        int z = floorBlock(start.z());
        int targetX = floorBlock(endX);
        int targetY = floorBlock(endY);
        int targetZ = floorBlock(endZ);
        if (x == targetX && y == targetY && z == targetZ) {
            return true;
        }

        int stepX = Integer.compare(targetX, x);
        int stepY = Integer.compare(targetY, y);
        int stepZ = Integer.compare(targetZ, z);
        double tDeltaX = axisDelta(deltaX);
        double tDeltaY = axisDelta(deltaY);
        double tDeltaZ = axisDelta(deltaZ);
        double tMaxX = firstBoundaryT(start.x(), deltaX, x, stepX);
        double tMaxY = firstBoundaryT(start.y(), deltaY, y, stepY);
        double tMaxZ = firstBoundaryT(start.z(), deltaZ, z, stepZ);

        while (x != targetX || y != targetY || z != targetZ) {
            double nextBoundary = Math.min(tMaxX, Math.min(tMaxY, tMaxZ));
            if (tMaxX <= nextBoundary + AXIS_TIE_EPSILON) {
                x += stepX;
                tMaxX += tDeltaX;
            }
            if (tMaxY <= nextBoundary + AXIS_TIE_EPSILON) {
                y += stepY;
                tMaxY += tDeltaY;
            }
            if (tMaxZ <= nextBoundary + AXIS_TIE_EPSILON) {
                z += stepZ;
                tMaxZ += tDeltaZ;
            }

            if (x == targetX && y == targetY && z == targetZ) {
                return true;
            }

            boolean occluding = snap.isBlockOccluding(x, y, z);
            if (debug) {
                spawnVoxelParticle(start, particleSpawner, x, y, z,
                        occluding ? ParticleSpawner.Colour.RED : ParticleSpawner.Colour.GREEN);
            }
            if (occluding && --maxOccluding < 1) {
                return false;
            }
        }
        return true;
    }

    private static int floorBlock(double coordinate) {
        return (int) Math.floor(coordinate);
    }

    private static double axisDelta(double delta) {
        return delta == 0.0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / delta);
    }

    private static double firstBoundaryT(double start, double delta, int block, int step) {
        if (step > 0) {
            return (block + 1.0 - start) / delta;
        }
        if (step < 0) {
            return (start - block) / -delta;
        }
        return Double.POSITIVE_INFINITY;
    }

    private static void spawnVoxelParticle(Locatable start, ParticleSpawner particleSpawner,
            int x, int y, int z, ParticleSpawner.Colour colour) {
        particleSpawner.spawnParticleAt(start.world(),
                new ImmutableSpatialImpl(x + 0.5, y + 0.5, z + 0.5), colour);
    }
}
