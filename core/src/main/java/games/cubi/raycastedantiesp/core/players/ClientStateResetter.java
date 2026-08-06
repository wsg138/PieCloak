package games.cubi.raycastedantiesp.core.players;

import games.cubi.logs.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Establishes a fresh client-visible world generation for a player after an outbound respawn packet.
 * The odd transition epoch is published before any state is cleared, so concurrent readers reject
 * the old generation until every world-scoped structure has been reset successfully.
 */
public final class ClientStateResetter {
    private ClientStateResetter() {}

    @SuppressWarnings("PMD.GuardLogStatement") // CubiLogging applies its configured level filter internally.
    public static boolean resetForRespawn(PlayerData playerData, String worldName, UUID worldUUID,
            int minWorldHeight) {
        Objects.requireNonNull(playerData, "playerData");
        if (!playerData.isConnected()) {
            Logger.error("Cannot reset client state for a disconnected respawn viewer. player="
                    + playerData.getPlayerUUID(), 1, ClientStateResetter.class);
            return false;
        }

        UUID playerUUID = playerData.getPlayerUUID();
        String committedWorldName = committedWorldName(worldName, worldUUID, playerUUID);
        UUID committedWorldUUID = worldUUID;

        PlayerRegistry registry = PlayerRegistry.getInstance();
        boolean completed = false;
        try {
            playerData.beginWorldTransition();
            int[] remainingEntityIDs = drainRemainingEntityIDs(playerData);
            playerData.nettyData().setExpectedWorldTransitionDestroyEntityIDs(remainingEntityIDs);

            List<RuntimeException> failures = resetWorldScopedState(
                    playerData, committedWorldName, minWorldHeight);

            if (!failures.isEmpty()) {
                IllegalStateException combined = new IllegalStateException(
                        "Respawn client-state reset failed for player=" + playerUUID);
                failures.forEach(combined::addSuppressed);
                Logger.error("Respawn client-state reset failed; unregistering the invalid player generation. player="
                        + playerUUID + " failedSteps=" + failures.size(), combined, 1, ClientStateResetter.class);
                return false;
            }

            playerData.completeWorldTransition(committedWorldUUID);
            completed = true;
            return true;
        } catch (RuntimeException exception) {
            Logger.error("Respawn client-state reset aborted; unregistering the invalid player generation. player="
                    + playerUUID, exception, 1, ClientStateResetter.class);
            return false;
        } finally {
            if (!completed) {
                registry.unregisterPlayer(playerUUID, playerData);
            }
        }
    }

    @SuppressWarnings("PMD.GuardLogStatement") // CubiLogging applies its configured level filter internally.
    private static String committedWorldName(String worldName, UUID worldUUID, UUID playerUUID) {
        if (worldName != null && worldUUID != null) {
            return worldName;
        }
        Logger.error("Respawn world metadata is incomplete; invalidating the prior visibility generation "
                + "with unresolved world state. player=" + playerUUID
                + " worldName=" + worldName + " worldUUID=" + worldUUID,
                1, ClientStateResetter.class);
        return null;
    }

    private static List<RuntimeException> resetWorldScopedState(
            PlayerData playerData, String committedWorldName, int minWorldHeight) {
        List<RuntimeException> failures = new ArrayList<>(6);
        resetStep("block view", playerData.blockView()::clear, failures);
        resetStep("entity view", playerData.entityView()::clear, failures);
        resetStep("player view", playerData.playerView()::clear, failures);
        resetStep("deferred reconciliation", playerData.nettyData()::clearPendingReconciliationState, failures);
        resetStep("self entity", playerData.nettyData().getSelfEntity()::clear, failures);
        resetStep("own location", () -> playerData.updateOwnLocation(null, 0, 0, 0), failures);
        resetStep("world metadata", () -> playerData.nettyData()
                .setCurrentWorldName(committedWorldName)
                .setCurrentWorldMinHeight(minWorldHeight), failures);
        return failures;
    }

    private static void resetStep(String description, Runnable step, List<RuntimeException> failures) {
        try {
            step.run();
        } catch (RuntimeException exception) {
            failures.add(new IllegalStateException("Failed to clear " + description, exception));
        }
    }

    private static int[] drainRemainingEntityIDs(PlayerData playerData) {
        int[] entityIDs = playerData.entityView().getKnownEntityIDs();
        int[] playerEntityIDs = playerData.playerView().getKnownEntityIDs();
        int[] remainingEntityIDs = new int[entityIDs.length + playerEntityIDs.length];
        System.arraycopy(entityIDs, 0, remainingEntityIDs, 0, entityIDs.length);
        System.arraycopy(playerEntityIDs, 0, remainingEntityIDs, entityIDs.length, playerEntityIDs.length);
        return remainingEntityIDs;
    }
}
