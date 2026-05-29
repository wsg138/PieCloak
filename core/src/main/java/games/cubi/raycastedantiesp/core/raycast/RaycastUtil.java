package games.cubi.raycastedantiesp.core.raycast;

import games.cubi.locatables.Locatable;
import games.cubi.locatables.MutableLocatable;
import games.cubi.locatables.implementations.MutableBlockVector;
import games.cubi.logs.Logger;
import games.cubi.raycastedantiesp.core.players.PlayerData;
import games.cubi.raycastedantiesp.core.view.BlockView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RaycastUtil {

//True: Has line-of-sight
    public static boolean raycast(PlayerData player, Locatable start, Locatable end, int maxOccluding, int alwaysShowRadius, int maxRaycastRadius, boolean debug, BlockView snap, int stepSize, ParticleSpawner particleSpawner) {
        if (!start.world().equals(end.world())) return false;

        MutableLocatable clonedEnd = end.clonePlainAndCentreIfBlockLocation();
        double total = start.distance(clonedEnd) - stepSize; //benchmarking shows that calling distance() is faster than distanceSquared() then checking distanceSquared < stepSize*stepSize every time despite the latter replacing a square root with multiplication
        if (total <= alwaysShowRadius) return true;
        if (total > maxRaycastRadius) return false;
        if (debug && particleSpawner == null) {
            Logger.errorAndReturn(new RuntimeException("raycast called with debug enabled but no ParticleSpawner supplied"), 2, RaycastUtil.class);
        }

        Locatable dir = clonedEnd.subtract(start).normalize().scalarMultiply(stepSize);

        MutableBlockVector current = new MutableBlockVector(start.world(), start.x(),start.y(),start.z());

        for (double traveled = 0; traveled < total; traveled += stepSize) { //benchmarking shows that for loop is marginally faster than while loop initially (after running for a while they are equal
            current.add(dir);

            if (snap.isBlockOccluding(current)) {//This works as MutableBlockVector resolves to a block location in #equals and #hashCode, and thus works fine as a key in the snapshot manager
                maxOccluding--;
                if (debug) particleSpawner.spawnParticleAt(current, ParticleSpawner.Colour.RED);
                if (maxOccluding < 1) return false;
                continue;
            }

            if (debug) particleSpawner.spawnParticleAt(current, ParticleSpawner.Colour.GREEN);
        }
        return true;
    }

    public static RaycastDetails raycastDetailed(PlayerData player, Locatable start, Locatable end, int maxOccluding, int alwaysShowRadius, int maxRaycastRadius, boolean debug, BlockView snap, int stepSize, ParticleSpawner particleSpawner) {
        int allowedOccluding = maxOccluding;
        if (start == null || end == null || start.world() == null || end.world() == null || !start.world().equals(end.world())) {
            return new RaycastDetails(false, "different-world-or-null", 0, 0, 0, allowedOccluding, alwaysShowRadius, maxRaycastRadius, List.of());
        }

        MutableLocatable clonedEnd = end.clonePlainAndCentreIfBlockLocation();
        double rawDistance = start.distance(clonedEnd);
        double total = rawDistance - stepSize;
        if (total <= alwaysShowRadius) {
            return new RaycastDetails(true, "within-always-show-radius", rawDistance, 0, 0, allowedOccluding, alwaysShowRadius, maxRaycastRadius, List.of());
        }
        if (total > maxRaycastRadius) {
            return new RaycastDetails(false, "beyond-raycast-radius", rawDistance, 0, 0, allowedOccluding, alwaysShowRadius, maxRaycastRadius, List.of());
        }
        if (debug && particleSpawner == null) {
            Logger.errorAndReturn(new RuntimeException("raycastDetailed called with debug enabled but no ParticleSpawner supplied"), 2, RaycastUtil.class);
        }

        Locatable dir = clonedEnd.subtract(start).normalize().scalarMultiply(stepSize);
        MutableBlockVector current = new MutableBlockVector(start.world(), start.x(), start.y(), start.z());
        List<String> occludingBlocks = new ArrayList<>(8);
        int samples = 0;
        int occludingHits = 0;

        for (double traveled = 0; traveled < total; traveled += stepSize) {
            samples++;
            current.add(dir);

            if (snap.isBlockOccluding(current)) {
                occludingHits++;
                if (occludingBlocks.size() < 12) {
                    occludingBlocks.add(current.blockX() + "," + current.blockY() + "," + current.blockZ());
                }
                maxOccluding--;
                if (debug) particleSpawner.spawnParticleAt(current, ParticleSpawner.Colour.RED);
                if (maxOccluding < 1) {
                    return new RaycastDetails(false, "blocked-by-occluding-blocks", rawDistance, samples, occludingHits, allowedOccluding, alwaysShowRadius, maxRaycastRadius, List.copyOf(occludingBlocks));
                }
                continue;
            }

            if (debug) particleSpawner.spawnParticleAt(current, ParticleSpawner.Colour.GREEN);
        }
        return new RaycastDetails(true, "clear-enough", rawDistance, samples, occludingHits, allowedOccluding, alwaysShowRadius, maxRaycastRadius, List.copyOf(occludingBlocks));
    }

    public record RaycastDetails(
            boolean canSee,
            String reason,
            double distance,
            int samples,
            int occludingHits,
            int allowedOccluding,
            int alwaysShowRadius,
            int maxRaycastRadius,
            List<String> occludingBlocks
    ) {
        public String describe() {
            return "canSee=" + canSee
                    + ",reason=" + reason
                    + ",distance=" + format(distance)
                    + ",samples=" + samples
                    + ",occludingHits=" + occludingHits
                    + ",allowedOccluding=" + allowedOccluding
                    + ",alwaysShowRadius=" + alwaysShowRadius
                    + ",maxRaycastRadius=" + maxRaycastRadius
                    + ",occludingBlocks=" + occludingBlocks;
        }

        private String format(double value) {
            return String.format(Locale.ROOT, "%.3f", value);
        }
    }
}
