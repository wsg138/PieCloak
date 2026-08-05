package games.cubi.raycastedantiesp.core.view;

import games.cubi.raycastedantiesp.core.tracked.TrackedTileEntity;

// Retains the originating object identity so a delayed transition cannot target a replacement at the same coordinates.
public record BlockViewTransition(Type type, TrackedTileEntity<?> tileEntity, long modeToken, int worldEpoch) {
    public enum Type {
        SHOW,
        HIDE,
    }
}
