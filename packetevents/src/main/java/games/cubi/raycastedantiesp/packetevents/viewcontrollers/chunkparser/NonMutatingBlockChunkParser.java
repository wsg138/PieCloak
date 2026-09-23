package games.cubi.raycastedantiesp.packetevents.viewcontrollers.chunkparser;

import games.cubi.raycastedantiesp.core.chunks.BlockInfoResolver;
import games.cubi.raycastedantiesp.core.policy.VisibilityExemptionPolicy;

import java.util.function.IntUnaryOperator;

public final class NonMutatingBlockChunkParser extends BlockChunkParser {
    public NonMutatingBlockChunkParser(BlockInfoResolver blockInfoResolver, IntUnaryOperator hiddenBlockID) {
        super(blockInfoResolver, false, hiddenBlockID, VisibilityExemptionPolicy.DISABLED);
    }
}
