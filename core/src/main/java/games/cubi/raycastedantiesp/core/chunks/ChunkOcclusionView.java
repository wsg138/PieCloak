package games.cubi.raycastedantiesp.core.chunks;

import org.jetbrains.annotations.Range;

public interface ChunkOcclusionView {
    /**
     * The parameters here are local coordinates within the chunk section, so they must be in the range [0, 15].
     *
     * @return true if the block at the given local coordinates is occluding, false otherwise.
     */
    boolean isOccludingLocal(@Range(from = 0, to = 15) int x, @Range(from = 0, to = 15) int y, @Range(from = 0, to = 15) int z);
}
