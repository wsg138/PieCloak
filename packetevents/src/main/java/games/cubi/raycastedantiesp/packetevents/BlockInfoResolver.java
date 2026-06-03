package games.cubi.raycastedantiesp.packetevents;

import java.util.UUID;

import org.jetbrains.annotations.Contract;

public interface BlockInfoResolver {
    @Contract(pure = true)
    boolean isOccluding(int blockStateID);
    @Contract(pure = true)
    boolean isTileEntity(int blockStateID);
    /**
     * Gets the combined block state ID at the given world coordinates.
     * @return the combined block state ID, or -1 if unavailable
     */
    default int getCombinedId(int x, int y, int z, UUID world) {
        return -1;
    }
}
