package games.cubi.raycastedantiesp.paper.staging;

import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import games.cubi.logs.Logger;
import games.cubi.raycastedantiesp.packetevents.BlockInfoResolver;
import games.cubi.raycastedantiesp.packetevents.config.PacketEventsBlockProcessorConfig;
import games.cubi.raycastedantiesp.paper.RaycastedAntiESP;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PacketEventsPaperBlockInfoResolver implements BlockInfoResolver {
    private final boolean[] occlusionArray;
    private final boolean[] tileEntityArray;

    public static PacketEventsPaperBlockInfoResolver get;

    public PacketEventsPaperBlockInfoResolver() {
        get = this;
        boolean[][] result = scanBlockIds(false);
        occlusionArray = result[0];
        tileEntityArray = result[1];
        PacketEventsBlockProcessorConfig config = RaycastedAntiESP.getConfigManager().getExtensionConfig(PacketEventsBlockProcessorConfig.class);
        if (config != null) {
            for (int blockStateId : config.tileEntityExemptedIds()) {
                if (blockStateId >= 0 && blockStateId < tileEntityArray.length) {
                    tileEntityArray[blockStateId] = false;
                }
            }
            for (int blockStateId : config.tileEntityForceIncludedIds()) {
                if (blockStateId >= 0 && blockStateId < tileEntityArray.length) {
                    tileEntityArray[blockStateId] = true;
                }
            }
        }
    }

    /**
     * @return Boolean array with two nested boolean arrays. <code>boolean[0]</code> returns the occlusion status array, <code>boolean[1]</code> returns the tile entity status array. Both arrays are indexed by block state ID. Air blocks and invalid IDs are treated as non-occluding and non-tile-entity, and trailing air IDs are ignored to save memory.
     */
    public boolean[][] iterateBlockIDs(boolean materialToIDMode) {
        return scanBlockIds(materialToIDMode);
    }

    private boolean[][] scanBlockIds(boolean materialToIDMode) {
        int airs = 0;
        int lastNonAirID = 0;
        Map<Integer, Boolean> occlusion = new HashMap<>(111000); //Tests show 30,000 block IDs in 1.21.11, and we scan forwards for 80k air ids just in case, so 111k is enough. This is a pointless micro optimization but why not
        Map<Integer, Boolean> tileEntity = new HashMap<>(111000);
        int iterator = 0;
        while (true) {
            BlockData blockData = SpigotConversionUtil.toBukkitBlockData(WrappedBlockState.getByGlobalId(iterator));
            if (blockData == null) {
                Logger.warning("Material for block state ID " + iterator + " is null, stopping iteration. This is not expected to happen.", 5, PacketEventsPaperBlockInfoResolver.class);
                break;
            }
            if (blockData.getMaterial() == Material.AIR) {
                airs++;
                if (airs > 80000) { // There is a sequence of ~40 air blocks around ID 100, and another of several hundred at ~3000. We scan forwards 80k to future-proof any mojank. Since it runs once at startup, perf is irrelevant here
                    break;
                }
            }
            else {
                airs = 0;
                lastNonAirID = iterator;
                logMaterialIdIfRequested(materialToIDMode, blockData, iterator);
            }

            occlusion.put(iterator, blockData.isOccluding());
            tileEntity.put(iterator, isTileEntity(blockData));
            iterator++;
        }
        boolean[][] result = new boolean[2][lastNonAirID + 1];
        for (int i = 0; i < (lastNonAirID + 1) /*Ignore the trailing airs*/; i++) {
            result[0][i] = occlusion.get(i);
            result[1][i] = tileEntity.get(i);
        }
        return result;
    }

    private void logMaterialIdIfRequested(boolean materialToIDMode, BlockData blockData, int iterator) {
        if (materialToIDMode) {
            Logger.debug(blockData.getAsString() + iterator);
        }
    }

    private boolean isTileEntity(BlockData blockData) {
        try {
            return blockData.createBlockState() instanceof TileState;
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override
    public boolean isOccluding(int blockStateID) {
        if (blockStateID < 0 || blockStateID >= occlusionArray.length) {
            return false; // Default to non-occluding for invalid IDs, should be safe since invalid IDs shouldn't exist in the world
        }
        return occlusionArray[blockStateID];
    }

    @Override
    public boolean isTileEntity(int blockStateID) {
        if (blockStateID < 0 || blockStateID >= tileEntityArray.length) {
            return false; // Default to non-tile-entity for invalid IDs, should be safe since invalid IDs shouldn't exist in the world
        }
        return tileEntityArray[blockStateID];
    }

    public boolean[] dumpOcclusionArray() {
        return occlusionArray;
    }

    private boolean[] dumpTileEntityArray() {
        return tileEntityArray;
    }

    @Override
    public int getCombinedId(int x, int y, int z, UUID world) {
        World bukkitWorld = Bukkit.getWorld(world);
        if (bukkitWorld == null) {
            return -1;
        }
        if (!bukkitWorld.isChunkLoaded(x >> 4, z >> 4)) {
            return -1;
        }
        BlockData blockData = bukkitWorld.getBlockAt(x, y, z).getBlockData();
        WrappedBlockState wrapped = SpigotConversionUtil.fromBukkitBlockData(blockData);
        return wrapped == null ? -1 : wrapped.getGlobalId();
    }
}
