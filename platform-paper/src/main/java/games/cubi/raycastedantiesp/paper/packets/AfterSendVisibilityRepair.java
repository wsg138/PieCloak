package games.cubi.raycastedantiesp.paper.packets;

import games.cubi.raycastedantiesp.core.players.PlayerData;
import games.cubi.raycastedantiesp.core.players.WorldEpochGuard;

import java.util.List;
import java.util.Objects;

/**
 * Runs visibility-repair batches only for the world session that scheduled them and flushes every
 * successful partial/full packet batch before returning. PacketEvents' silent write API only queues
 * bytes on the channel; after-send callbacks run after the triggering packet has completed, so they
 * must not depend on some unrelated future packet to flush visibility repairs to the client.
 */
final class AfterSendVisibilityRepair {
    private AfterSendVisibilityRepair() {
    }

    static void wrapNewTasks(
            List<Runnable> tasks,
            int firstNewTask,
            PlayerData playerData,
            int expectedWorldEpoch,
            Runnable flush) {
        Objects.requireNonNull(tasks, "tasks");
        Objects.requireNonNull(flush, "flush");
        for (int index = firstNewTask; index < tasks.size(); index++) {
            tasks.set(index, fenceAndFlush(
                    playerData,
                    expectedWorldEpoch,
                    tasks.get(index),
                    flush));
        }
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
