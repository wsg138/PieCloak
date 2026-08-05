package games.cubi.raycastedantiesp.packetevents.replaydata;

import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

class PacketEventsTileEntityReplayDataTest {
    @Test
    void nbtIsCopiedOnWriteAndReadAndClearedWithReplayState() {
        PacketEventsTileEntityReplayData replayData = new PacketEventsTileEntityReplayData();
        NBTCompound source = new NBTCompound();

        replayData.setBlockEntityData(null, source);

        NBTCompound firstRead = replayData.nbt();
        assertNotNull(firstRead);
        assertNotSame(source, firstRead);
        assertNotSame(firstRead, replayData.nbt());

        replayData.clear();
        assertNull(replayData.blockEntityType());
        assertNull(replayData.nbt());
    }
}
