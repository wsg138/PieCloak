package games.cubi.raycastedantiesp.packetevents.target;

import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.world.blockentity.BlockEntityType;

public interface PacketEventsTargetFilter {
    PacketEventsTargetFilter DISABLED = new PacketEventsTargetFilter() {
        @Override
        public boolean shouldCullEntity(EntityType entityType, boolean isPlayer) {
            return false;
        }

        @Override
        public boolean shouldCullBlockState(int blockStateId) {
            return false;
        }

        @Override
        public boolean shouldCullBlockEntity(BlockEntityType blockEntityType) {
            return false;
        }

        @Override
        public boolean shouldCullTileEntity(int blockStateId) {
            return false;
        }
    };

    boolean shouldCullEntity(EntityType entityType, boolean isPlayer);

    boolean shouldCullBlockState(int blockStateId);

    boolean shouldCullBlockEntity(BlockEntityType blockEntityType);

    boolean shouldCullTileEntity(int blockStateId);
}
