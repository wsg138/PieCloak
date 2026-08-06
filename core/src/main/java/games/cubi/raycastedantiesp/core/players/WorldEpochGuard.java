package games.cubi.raycastedantiesp.core.players;

import java.util.Objects;

/** Guards deferred work against disconnects and client-world generation changes. */
public final class WorldEpochGuard {
    private WorldEpochGuard() {}

    public static Runnable fence(PlayerData playerData, int expectedWorldEpoch, Runnable task) {
        Objects.requireNonNull(playerData, "playerData");
        Objects.requireNonNull(task, "task");
        return () -> {
            if (playerData.isConnected() && playerData.acquireWorldEpoch() == expectedWorldEpoch) {
                task.run();
            }
        };
    }
}
