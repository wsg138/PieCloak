package games.cubi.raycastedantiesp.core.view;

import java.util.UUID;

// Used to cache visibility changes until the player's netty thread next processes
public record EntityViewTransition(Type type, UUID targetUUID, int entityID, int attempts) {
    public EntityViewTransition(Type type, UUID targetUUID, int entityID) {
        this(type, targetUUID, entityID, 0);
    }

    public EntityViewTransition retry() {
        return new EntityViewTransition(type, targetUUID, entityID, attempts + 1);
    }

    public enum Type {
        SHOW,
        HIDE,
        FORGET,
    }
}
