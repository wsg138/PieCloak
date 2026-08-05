package games.cubi.raycastedantiesp.packetevents.viewcontrollers;

import games.cubi.raycastedantiesp.core.view.EntityView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class EntityTransitionRetryQueue<P> {
    static final int MAX_PER_VIEWER = 256;
    static final int MAX_GLOBAL = 4096;

    enum RetryResult {
        ENQUEUED,
        SUPERSEDED,
        CAPACITY_REJECTED
    }

    private final LinkedHashMap<Key, EntityTransitionWork<P>> pending = new LinkedHashMap<>();
    private final Map<UUID, Integer> countsByViewer = new HashMap<>();

    synchronized RetryResult retry(EntityTransitionWork<P> work) {
        Key key = key(work.viewerUUID(), work.view(), work.entity().entityUUID());
        if (pending.containsKey(key)) {
            // A newer transition for this entity won while this work was in flight.
            return RetryResult.SUPERSEDED;
        }
        if (countsByViewer.getOrDefault(work.viewerUUID(), 0) >= MAX_PER_VIEWER
                || pending.size() >= MAX_GLOBAL) {
            // Preserve already-queued partial repairs. The caller reports this repair as terminal
            // rather than silently evicting older work and losing its reconciliation state.
            return RetryResult.CAPACITY_REJECTED;
        }
        pending.put(key, work);
        countsByViewer.merge(key.viewerUUID(), 1, Integer::sum);
        return RetryResult.ENQUEUED;
    }

    synchronized List<EntityTransitionWork<P>> drainDue(UUID viewerUUID, int currentTick) {
        if (!countsByViewer.containsKey(viewerUUID)) {
            return List.of();
        }
        List<EntityTransitionWork<P>> due = new ArrayList<>();
        Iterator<Map.Entry<Key, EntityTransitionWork<P>>> iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Key, EntityTransitionWork<P>> entry = iterator.next();
            if (!entry.getKey().viewerUUID().equals(viewerUUID) || !entry.getValue().isDue(currentTick)) {
                continue;
            }
            due.add(entry.getValue());
            iterator.remove();
            decrement(viewerUUID);
        }
        return List.copyOf(due);
    }

    synchronized EntityTransitionWork<P> cancel(UUID viewerUUID, EntityView<?> view, UUID entityUUID) {
        return remove(key(viewerUUID, view, entityUUID));
    }

    synchronized void clearEntities(UUID viewerUUID, int[] entityIDs) {
        if (entityIDs == null || entityIDs.length == 0 || !countsByViewer.containsKey(viewerUUID)) {
            return;
        }
        Iterator<Map.Entry<Key, EntityTransitionWork<P>>> iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Key, EntityTransitionWork<P>> entry = iterator.next();
            if (!entry.getKey().viewerUUID().equals(viewerUUID)
                    || !contains(entityIDs, entry.getValue().entity().entityID())) {
                continue;
            }
            iterator.remove();
            decrement(viewerUUID);
        }
    }

    synchronized boolean hasPending(UUID viewerUUID) {
        return countsByViewer.containsKey(viewerUUID);
    }

    synchronized void clear(UUID viewerUUID) {
        Iterator<Key> iterator = pending.keySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().viewerUUID().equals(viewerUUID)) {
                iterator.remove();
            }
        }
        countsByViewer.remove(viewerUUID);
    }

    synchronized int pendingCount() {
        return pending.size();
    }

    synchronized int pendingCount(UUID viewerUUID) {
        return countsByViewer.getOrDefault(viewerUUID, 0);
    }

    private EntityTransitionWork<P> remove(Key key) {
        EntityTransitionWork<P> removed = pending.remove(key);
        if (removed != null) {
            decrement(key.viewerUUID());
        }
        return removed;
    }

    private void decrement(UUID viewerUUID) {
        countsByViewer.computeIfPresent(viewerUUID, (ignored, count) -> count == 1 ? null : count - 1);
    }

    private static Key key(UUID viewerUUID, EntityView<?> view, UUID entityUUID) {
        return new Key(viewerUUID, view.isPlayerView(), entityUUID);
    }

    private static boolean contains(int[] values, int target) {
        for (int value : values) {
            if (value == target) {
                return true;
            }
        }
        return false;
    }

    private record Key(UUID viewerUUID, boolean playerView, UUID entityUUID) {
    }
}
