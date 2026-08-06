package games.cubi.raycastedantiesp.core.view;

import games.cubi.raycastedantiesp.core.testsupport.TestProxySupport;
import games.cubi.raycastedantiesp.core.tracked.TrackedTileEntity;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PackedBlockTransitionQueueFailureTest {
    @Test
    void callbackFailureInPackedEntryDoesNotDiscardLaterTransitions() {
        PackedBlockTransitionQueue queue = new PackedBlockTransitionQueue();
        List<TrackedTileEntity<?>> tiles = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            TrackedTileEntity<?> tile = tileEntity();
            tiles.add(tile);
            queue.add(BlockViewTransition.Type.SHOW, tile, 9L, 2);
        }
        queue.flushPendingTransitions();

        List<TrackedTileEntity<?>> observed = new ArrayList<>();
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> queue.drainTransitions((type, tileEntity, modeToken, worldEpoch) -> {
                    observed.add(tileEntity);
                    if (tileEntity == tiles.get(2)) {
                        throw new IllegalStateException("injected middle-entry failure");
                    }
                }));

        assertEquals("injected middle-entry failure", failure.getMessage());
        assertEquals(tiles, observed);
        assertFalse(queue.hasPendingTransitions());
    }

    @SuppressWarnings("unchecked")
    private static TrackedTileEntity<?> tileEntity() {
        return (TrackedTileEntity<?>) Proxy.newProxyInstance(
                TestProxySupport.contextClassLoader(),
                new Class<?>[]{TrackedTileEntity.class},
                (object, method, args) -> {
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    if (method.getReturnType() == long.class) return 0L;
                    if (method.getReturnType() == char.class) return (char) 0;
                    return null;
                });
    }
}
