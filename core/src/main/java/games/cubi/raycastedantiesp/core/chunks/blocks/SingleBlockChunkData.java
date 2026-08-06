package games.cubi.raycastedantiesp.core.chunks.blocks;

import games.cubi.raycastedantiesp.core.chunks.BlockChunkData;

import games.cubi.raycastedantiesp.core.chunks.BlockInfoResolver;
import games.cubi.raycastedantiesp.core.chunks.ChunkData;

// If it is air, just use a null reference
public class SingleBlockChunkData implements BlockChunkData {
    final char blockID;
    private final boolean occluding;

    public SingleBlockChunkData(char blockID, boolean occluding) {
        this.blockID = blockID;
        this.occluding = occluding;
    }

    @Override
    public char getBlockID(int x, int y, int z) {
        ChunkData.packLocalChecked(x, y, z);
        return blockID;
    }

    @Override
    public BlockChunkData setBlockID(int x, int y, int z, char newBlockID, BlockInfoResolver blockInfoResolver) {
        ChunkData.packLocalChecked(x, y, z);
        if (newBlockID == this.blockID) {
            return this; // Chunk data did not change, no need to resize
        }
        return TwoBlockChunkData.upgradeFromSingleBlock(this, newBlockID, x, y, z, blockInfoResolver);
    }

    @Override
    public boolean isOccludingLocal(int x, int y, int z) {
        ChunkData.packLocalChecked(x, y, z);
        return occluding;
    }
}
