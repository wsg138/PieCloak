package games.cubi.raycastedantiesp.paper.packets;

import games.cubi.raycastedantiesp.core.players.PlayerData;
import games.cubi.raycastedantiesp.core.players.WorldEpochGuard;

import java.util.Objects;

/**
 * Runs a visibility-repair batch only for the world session that scheduled it and flushes every
 * successful partial/full packet batch before returning. PacketEvents' silent write API only queues
 * bytes on the channel; after-send callbacks run after the triggering packet has completed, so they
 * must not depend on some unrelated future packet to flush visibility repairs to the client.
 */
final class AfterSendVisibilityRepair {
    private AfterSendVisibilityRepair() {
    }

    static Runnable fenceAndFlush(
            PlayerData playerData,
            int expectedWorldEpoch,
            Runnable repair,
            Runnable flush) {
        Objects.requireNonNull(repair, "repair");
        Objects.requireNonNull(flush, "flush");
        return WorldEpochGuard.fence(
                playerData,
                expectedWorldEpoch,
                () -> runAndFlush(repair, flush));
    }

    static void runAndFlush(Runnable repair, Runnable flush) {
        Objects.requireNonNull(repair, "repair");
        Objects.requireNonNull(flush, "flush");
        try {
            repair.run();
        } finally {
            flush.run();
        }
    }
}
