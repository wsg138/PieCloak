package games.cubi.raycastedantiesp.packetevents.viewcontrollers;

import games.cubi.raycastedantiesp.core.view.BlockView;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static games.cubi.raycastedantiesp.packetevents.viewcontrollers.BlockTransitionRetryQueue.Operation.HIDE;
import static games.cubi.raycastedantiesp.packetevents.viewcontrollers.BlockTransitionRetryQueue.Operation.MODE_REPAIR;
import static games.cubi.raycastedantiesp.packetevents.viewcontrollers.BlockTransitionRetryQueue.Operation.SHOW;
import static games.cubi.raycastedantiesp.packetevents.viewcontrollers.BlockTransitionRetryQueue.Stage.BLOCK;
import static games.cubi.raycastedantiesp.packetevents.viewcontrollers.BlockTransitionRetryQueue.Stage.BLOCK_ENTITY_DATA;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketEventsBlockTransitionWriteTest {
    @Test
    void hideFailureReportsFakeBlockStage() {
        PacketEventsBlockViewController.TransitionWriteException failure =
                assertThrows(PacketEventsBlockViewController.TransitionWriteException.class,
                        () -> PacketEventsBlockViewController.executeTransitionWrites(
                                HIDE, BLOCK,
                                () -> {
                                    throw new IllegalStateException("fake-block failure");
                                },
                                null));

        assertEquals(BLOCK, failure.stage());
    }

    @Test
    void showFailureBeforeRealBlockDoesNotAttemptNbt() {
        AtomicInteger nbtWrites = new AtomicInteger();
        PacketEventsBlockViewController.TransitionWriteException failure =
                assertThrows(PacketEventsBlockViewController.TransitionWriteException.class,
                        () -> PacketEventsBlockViewController.executeTransitionWrites(
                                SHOW, BLOCK,
                                () -> {
                                    throw new IllegalStateException("real-block failure");
                                },
                                nbtWrites::incrementAndGet));

        assertEquals(BLOCK, failure.stage());
        assertEquals(0, nbtWrites.get());
    }

    @Test
    void showRetryResumesAtNbtWithoutDuplicatingRealBlock() {
        AtomicInteger blockWrites = new AtomicInteger();
        AtomicInteger nbtWrites = new AtomicInteger();

        PacketEventsBlockViewController.TransitionWriteException failure =
                assertThrows(PacketEventsBlockViewController.TransitionWriteException.class,
                        () -> PacketEventsBlockViewController.executeTransitionWrites(
                                SHOW, BLOCK,
                                blockWrites::incrementAndGet,
                                () -> {
                                    nbtWrites.incrementAndGet();
                                    throw new IllegalStateException("nbt failure");
                                }));

        assertEquals(BLOCK_ENTITY_DATA, failure.stage());
        assertEquals(1, blockWrites.get());
        assertEquals(1, nbtWrites.get());

        PacketEventsBlockViewController.executeTransitionWrites(
                SHOW, failure.stage(), blockWrites::incrementAndGet, nbtWrites::incrementAndGet);

        assertEquals(1, blockWrites.get());
        assertEquals(2, nbtWrites.get());
    }

    @Test
    void disableRepairUsesTheSameStageAwareShowSequence() {
        AtomicInteger blockWrites = new AtomicInteger();
        AtomicInteger nbtWrites = new AtomicInteger();

        PacketEventsBlockViewController.TransitionWriteException failure =
                assertThrows(PacketEventsBlockViewController.TransitionWriteException.class,
                        () -> PacketEventsBlockViewController.executeTransitionWrites(
                                MODE_REPAIR, BLOCK,
                                blockWrites::incrementAndGet,
                                () -> {
                                    nbtWrites.incrementAndGet();
                                    throw new IllegalStateException("disable-repair nbt failure");
                                }));

        assertEquals(BLOCK_ENTITY_DATA, failure.stage());
        PacketEventsBlockViewController.executeTransitionWrites(
                MODE_REPAIR, failure.stage(), blockWrites::incrementAndGet, nbtWrites::incrementAndGet);
        assertEquals(1, blockWrites.get());
        assertEquals(2, nbtWrites.get());
    }

    @Test
    void managedUnknownTileFailsClosedButKnownVirtualDataPasses() {
        assertTrue(PacketEventsBlockViewController.shouldFailClosedForUnknownBlockEntity(
                true, BlockView.BlockEntityStatus.MANAGED, false));
        assertTrue(PacketEventsBlockViewController.shouldFailClosedForUnknownBlockEntity(
                true, BlockView.BlockEntityStatus.UNKNOWN, true));
        assertFalse(PacketEventsBlockViewController.shouldFailClosedForUnknownBlockEntity(
                true, BlockView.BlockEntityStatus.NON_MANAGED, true));
        assertFalse(PacketEventsBlockViewController.shouldFailClosedForUnknownBlockEntity(
                true, BlockView.BlockEntityStatus.UNKNOWN, false));
        assertFalse(PacketEventsBlockViewController.shouldFailClosedForUnknownBlockEntity(
                false, BlockView.BlockEntityStatus.MANAGED, true));
    }

    @Test
    void unexpectedBulkChunkPacketIsPassThroughAndDiagnosticsAreBounded() {
        AtomicInteger diagnostics = new AtomicInteger();
        AtomicInteger warnings = new AtomicInteger();

        assertDoesNotThrow(() -> {
            for (int index = 0; index < 100; index++) {
                PacketEventsBlockViewController.handleUnexpectedBulkChunkPacket(
                        diagnostics, UUID.randomUUID(), index, ignored -> warnings.incrementAndGet());
            }
        });

        assertEquals(100, diagnostics.get());
        assertEquals(5, warnings.get());
    }
}
