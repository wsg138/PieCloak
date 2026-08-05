package games.cubi.raycastedantiesp.core.view.chunks;

import ca.spottedleaf.concurrentutil.map.SWMRLong2ObjectHashTable;
import games.cubi.raycastedantiesp.core.chunks.BlockInfoResolver;
import games.cubi.raycastedantiesp.core.chunks.ChunkData;
import games.cubi.raycastedantiesp.core.chunks.OccludingChunkData;
import games.cubi.raycastedantiesp.core.view.BlockView.BlockEntityStatus;

import static games.cubi.raycastedantiesp.core.view.chunks.ChunkSectionStore.packChunkCoords;
import static games.cubi.raycastedantiesp.core.view.chunks.ChunkSectionStore.packGlobalCoords;

/**
 * Tracks whether an authoritative block position is a managed, non-managed, or unknown block entity without retaining
 * every full block-state ID. Packet-driven writes are single-threaded; readers may observe a weakly consistent snapshot.
 */
public final class BlockEntitySectionStore {
    private final SWMRLong2ObjectHashTable<SectionState> sections = new SWMRLong2ObjectHashTable<>();
    private final BlockInfoResolver blockInfoResolver;

    public BlockEntitySectionStore(BlockInfoResolver blockInfoResolver) {
        this.blockInfoResolver = blockInfoResolver;
    }

    public BlockEntityStatus getStatus(int x, int y, int z) {
        SectionState section = sections.get(packGlobalCoords(x, y, z));
        if (section == null) {
            return BlockEntityStatus.UNKNOWN;
        }
        int localX = x & ChunkData.LOCAL_MASK;
        int localY = y & ChunkData.LOCAL_MASK;
        int localZ = z & ChunkData.LOCAL_MASK;
        if (!section.known.isOccludingLocal(localX, localY, localZ)) {
            return BlockEntityStatus.UNKNOWN;
        }
        return section.managed != null && section.managed.isOccludingLocal(localX, localY, localZ)
                ? BlockEntityStatus.MANAGED
                : BlockEntityStatus.NON_MANAGED;
    }

    public void setBlockID(int x, int y, int z, int blockID) {
        long key = packGlobalCoords(x, y, z);
        SectionState section = sections.get(key);
        if (section == null) {
            section = SectionState.partial();
            sections.put(key, section);
        }
        boolean managedBlockEntity = blockID != 0 && blockInfoResolver.isTileEntity(blockID);
        section.set(x & ChunkData.LOCAL_MASK, y & ChunkData.LOCAL_MASK, z & ChunkData.LOCAL_MASK,
                managedBlockEntity);
    }

    public void replaceSection(int chunkX, int sectionY, int chunkZ,
            OccludingChunkData managedBlockEntities) {
        sections.put(packChunkCoords(chunkX, sectionY, chunkZ),
                SectionState.authoritative(managedBlockEntities));
    }

    public void removeSection(int chunkX, int sectionY, int chunkZ) {
        sections.remove(packChunkCoords(chunkX, sectionY, chunkZ));
    }

    public void removeColumn(int chunkX, int chunkZ) {
        long key = packChunkCoords(chunkX, 0, chunkZ);
        for (int index = 0; index < ChunkSectionStore.SECTION_Y_COUNT; index++) {
            sections.remove(key);
            key += ChunkSectionStore.SECTION_Y_INCREMENT;
        }
    }

    public void clear() {
        sections.clear();
    }

    private static final class SectionState {
        private OccludingChunkData known;
        private OccludingChunkData managed;

        private SectionState(OccludingChunkData known, OccludingChunkData managed) {
            this.known = known;
            this.managed = managed;
        }

        private static SectionState partial() {
            return new SectionState(OccludingChunkData.empty(), null);
        }

        private static SectionState authoritative(OccludingChunkData managed) {
            return new SectionState(OccludingChunkData.solid(), managed);
        }

        private void set(int x, int y, int z, boolean managedBlockEntity) {
            if (!known.isOccludingLocal(x, y, z)) {
                known = known.setOccluding(x, y, z, true);
            }
            managed = set(managed, x, y, z, managedBlockEntity);
        }

        private static OccludingChunkData set(OccludingChunkData data, int x, int y, int z, boolean value) {
            if (data == null) {
                if (!value) {
                    return null;
                }
                data = OccludingChunkData.empty();
            }
            return data.setOccluding(x, y, z, value);
        }
    }
}
