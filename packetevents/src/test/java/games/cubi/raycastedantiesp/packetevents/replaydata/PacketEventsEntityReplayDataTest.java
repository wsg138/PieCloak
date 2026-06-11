package games.cubi.raycastedantiesp.packetevents.replaydata;

import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.util.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketEventsEntityReplayDataTest {
    @Test
    void repeatedVelocityPacketsReplacePreviousState() {
        PacketEventsEntityReplayData.Impl replayData = new PacketEventsEntityReplayData.Impl();
        for (int i = 0; i < 1000; i++) {
            replayData.addVelocity(new Vector3d(i, i + 1, i + 2));
        }

        assertEquals(1, replayData.size());
        assertEquals(999, replayData.velocitySnapshot().getX());
    }

    @Test
    void metadataIsMergedByIndexAndBounded() {
        PacketEventsEntityReplayData.Impl replayData = new PacketEventsEntityReplayData.Impl();
        for (int i = 0; i < 400; i++) {
            replayData.addMetadata(List.of(new EntityData<>(i, null, i)));
        }
        replayData.addMetadata(List.of(new EntityData<>(399, null, 9001)));

        assertTrue(replayData.size() <= PacketEventsEntityReplayData.MAX_REPLAY_ENTRIES);
        assertEquals(9001, replayData.metadataSnapshot().stream()
                .filter(entry -> entry.getIndex() == 399)
                .findFirst()
                .orElseThrow()
                .getValue());
    }
}
