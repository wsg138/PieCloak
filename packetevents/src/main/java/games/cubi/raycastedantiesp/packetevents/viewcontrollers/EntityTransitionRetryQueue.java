package games.cubi.raycastedantiesp.packetevents.viewcontrollers;

import games.cubi.raycastedantiesp.core.tracked.TrackedEntity;
import games.cubi.raycastedantiesp.core.view.EntityView;
import games.cubi.raycastedantiesp.core.view.EntityViewTransition;
import games.cubi.raycastedantiesp.packetevents.tracked.PacketEventsEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class EntityTransitionRetryQueue {
    private final ConcurrentHashMap<UUID, LinkedHashMap<Key, Retry>> retriesByViewer = new ConcurrentHashMap<>();

    void enqueue(UUID viewerUUID, EntityView<PacketEventsEntity> view, EntityViewTransition.Type type,
            TrackedEntity<?> entity, int worldEpoch, int attempts) {
        Key key = new Key(view.isPlayerView(), entity.entityUUID(), type);
        Retry retry = new Retry(view, type, entity, worldEpoch, attempts);
        retriesByViewer.compute(viewerUUID, (ignored, retries) -> {
            LinkedHashMap<Key, Retry> current = retries == null ? new LinkedHashMap<>() : retries;
            current.put(key, retry);
            return current;
        });
    }

    List<Retry> drain(UUID viewerUUID) {
        LinkedHashMap<Key, Retry> retries = retriesByViewer.remove(viewerUUID);
        return retries == null ? List.of() : List.copyOf(retries.values());
    }

    boolean hasPending(UUID viewerUUID) {
        return retriesByViewer.containsKey(viewerUUID);
    }

    void clear(UUID viewerUUID) {
        retriesByViewer.remove(viewerUUID);
    }

    private record Key(boolean playerView, UUID entityUUID, EntityViewTransition.Type type) {
    }

    record Retry(EntityView<PacketEventsEntity> view, EntityViewTransition.Type type,
                 TrackedEntity<?> entity, int worldEpoch, int attempts) {
    }
}
