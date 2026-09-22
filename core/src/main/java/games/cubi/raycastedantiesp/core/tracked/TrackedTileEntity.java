package games.cubi.raycastedantiesp.core.tracked;

import games.cubi.locatables.api.ImmutableBlockSpatial;
import games.cubi.raycastedantiesp.core.utils.Clearable;

public interface TrackedTileEntity<T> extends ImmutableBlockSpatial, Clearable {
    int NEVER_CHECKED = Integer.MIN_VALUE;

    boolean visible();
    TrackedTileEntity<T> setVisible(boolean visible);

    /** Whether a target-location exemption currently overrides normal hiding for this tile entity. */
    boolean visibilityExempt();
    TrackedTileEntity<T> setVisibilityExempt(boolean visibilityExempt);

    int lastChecked();
    TrackedTileEntity<T> setLastChecked(int lastChecked);

    /** Packet-thread-only structural state. */
    int blockID();
    TrackedTileEntity<T> setBlockID(char blockID);

    /** Packet-thread-only replay state. */
    T extraData();
    TrackedTileEntity<T> setExtraData(T extraData);
    void clearExtraData();
}
