package games.cubi.raycastedantiesp.packetevents.viewcontrollers;

import games.cubi.raycastedantiesp.core.players.PlayerData;
import games.cubi.raycastedantiesp.core.tracked.TrackedTileEntity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class BlockTransitionRetryQueue {
    static final int MAX_RETRIES_PER_VIEWER = 256;
    private static final int MAX_BACKOFF_TICKS = 20;

    enum Operation {
        HIDE,
        SHOW,
        MODE_REPAIR
    }

    enum Stage {
        BLOCK,
        BLOCK_ENTITY_DATA
    }

    private final Map<UUID, LinkedHashMap<Key, Retry>> retriesByViewer = new LinkedHashMap<>();

    synchronized boolean enqueue(UUID viewerUUID, Operation operation, Stage stage,
            TrackedTileEntity<?> tileEntity, int expectedBlockID, int worldEpoch, long modeToken,
            int attempts, int currentTick) {
        if (viewerUUID == null || tileEntity == null || !PlayerData.isStableWorldEpoch(worldEpoch)) {
            return false;
        }

        LinkedHashMap<Key, Retry> retries = retriesByViewer.computeIfAbsent(
                viewerUUID, ignored -> new LinkedHashMap<>());
        Key key = new Key(tileEntity, expectedBlockID, worldEpoch, modeToken, operation);
        Retry existing = retries.get(key);
        int nextAttemptTick = currentTick + retryDelay(attempts);

        if (existing != null) {
            Stage earliestStage = earlier(existing.stage(), stage);
            int mergedAttempts = Math.max(existing.attempts(), attempts);
            int mergedTick = isDue(currentTick, existing.nextAttemptTick())
                    ? currentTick
                    : existing.nextAttemptTick();
            retries.put(key, new Retry(operation, earliestStage, tileEntity, expectedBlockID,
                    worldEpoch, modeToken, mergedAttempts, mergedTick));
            return false;
        }

        boolean evicted = false;
        if (retries.size() >= MAX_RETRIES_PER_VIEWER) {
            Iterator<Key> iterator = retries.keySet().iterator();
            if (iterator.hasNext()) {
                iterator.next();
                iterator.remove();
                evicted = true;
            }
        }
        retries.put(key, new Retry(operation, stage, tileEntity, expectedBlockID,
                worldEpoch, modeToken, attempts, nextAttemptTick));
        return evicted;
    }

    synchronized List<Retry> drainDue(UUID viewerUUID, int currentWorldEpoch, int currentTick) {
        LinkedHashMap<Key, Retry> retries = retriesByViewer.get(viewerUUID);
        if (retries == null) {
            return List.of();
        }
        if (!PlayerData.isStableWorldEpoch(currentWorldEpoch)) {
            retriesByViewer.remove(viewerUUID);
            return List.of();
        }

        ArrayList<Retry> due = new ArrayList<>();
        Iterator<Retry> iterator = retries.values().iterator();
        while (iterator.hasNext()) {
            Retry retry = iterator.next();
            if (retry.worldEpoch() != currentWorldEpoch) {
                iterator.remove();
                continue;
            }
            if (isDue(currentTick, retry.nextAttemptTick())) {
                due.add(retry);
                iterator.remove();
            }
        }
        if (retries.isEmpty()) {
            retriesByViewer.remove(viewerUUID);
        }
        return List.copyOf(due);
    }

    synchronized void discardStale(UUID viewerUUID, int currentWorldEpoch, long currentModeToken) {
        LinkedHashMap<Key, Retry> retries = retriesByViewer.get(viewerUUID);
        if (retries == null) {
            return;
        }
        if (!PlayerData.isStableWorldEpoch(currentWorldEpoch)) {
            retriesByViewer.remove(viewerUUID);
            return;
        }
        retries.values().removeIf(retry -> retry.worldEpoch() != currentWorldEpoch
                || retry.modeToken() != currentModeToken);
        if (retries.isEmpty()) {
            retriesByViewer.remove(viewerUUID);
        }
    }

    synchronized boolean hasPending(UUID viewerUUID) {
        LinkedHashMap<Key, Retry> retries = retriesByViewer.get(viewerUUID);
        return retries != null && !retries.isEmpty();
    }

    synchronized int size(UUID viewerUUID) {
        LinkedHashMap<Key, Retry> retries = retriesByViewer.get(viewerUUID);
        return retries == null ? 0 : retries.size();
    }

    synchronized void clear(UUID viewerUUID) {
        if (viewerUUID != null) {
            retriesByViewer.remove(viewerUUID);
        }
    }

    private static int retryDelay(int attempts) {
        int shift = Math.min(Math.max(attempts - 1, 0), 4);
        return Math.min(1 << shift, MAX_BACKOFF_TICKS);
    }

    private static Stage earlier(Stage first, Stage second) {
        return first.ordinal() <= second.ordinal() ? first : second;
    }

    private static boolean isDue(int currentTick, int dueTick) {
        return currentTick - dueTick >= 0;
    }

    private static final class Key {
        private final TrackedTileEntity<?> tileEntity;
        private final int expectedBlockID;
        private final int worldEpoch;
        private final long modeToken;
        private final Operation operation;

        private Key(TrackedTileEntity<?> tileEntity, int expectedBlockID, int worldEpoch,
                long modeToken, Operation operation) {
            this.tileEntity = tileEntity;
            this.expectedBlockID = expectedBlockID;
            this.worldEpoch = worldEpoch;
            this.modeToken = modeToken;
            this.operation = operation;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key key)) {
                return false;
            }
            return tileEntity == key.tileEntity
                    && expectedBlockID == key.expectedBlockID
                    && worldEpoch == key.worldEpoch
                    && modeToken == key.modeToken
                    && operation == key.operation;
        }

        @Override
        public int hashCode() {
            int result = System.identityHashCode(tileEntity);
            result = 31 * result + expectedBlockID;
            result = 31 * result + worldEpoch;
            result = 31 * result + Long.hashCode(modeToken);
            result = 31 * result + operation.hashCode();
            return result;
        }
    }

    record Retry(Operation operation, Stage stage, TrackedTileEntity<?> tileEntity,
                 int expectedBlockID, int worldEpoch, long modeToken, int attempts,
                 int nextAttemptTick) {
    }
}
