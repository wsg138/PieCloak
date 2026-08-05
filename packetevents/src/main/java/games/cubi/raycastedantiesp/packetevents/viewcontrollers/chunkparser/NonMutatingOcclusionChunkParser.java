package games.cubi.raycastedantiesp.packetevents.viewcontrollers.chunkparser;

import games.cubi.raycastedantiesp.core.chunks.BlockInfoResolver;

import java.util.function.IntUnaryOperator;

public final class NonMutatingOcclusionChunkParser extends OcclusionChunkParser {
    public NonMutatingOcclusionChunkParser(BlockInfoResolver blockInfoResolver, IntUnaryOperator hiddenBlockID) {
        super(blockInfoResolver, false, hiddenBlockID);
    }
}
