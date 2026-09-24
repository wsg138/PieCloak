package games.cubi.raycastedantiesp.core.raycast;

import games.cubi.locatables.api.Locatable;
import games.cubi.locatables.implementations.ImmutableBlockSpatialImpl;
import games.cubi.locatables.implementations.ImmutableLocatableImpl;
import games.cubi.raycastedantiesp.core.testsupport.TestProxySupport;
import games.cubi.raycastedantiesp.core.view.BlockView;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;

class RaycastUtilBlockTargetOffsetRegressionTest {
    @Test
    void blockTargetsRemainCentredWhenLegacyCallSitePassesYOffset() {
        UUID world = UUID.randomUUID();
        Locatable start = new ImmutableLocatableImpl(world, 0.5, 0.5, 0.5);
        BlockView blocks = occludingAt(3, 0, 0);
        RaycastUtil.Settings settings = new RaycastUtil.Settings(1, 0, 10, false, blocks, null);

        assertFalse(RaycastUtil.raycast(
                start,
                new ImmutableBlockSpatialImpl(4, 0, 0),
                settings,
                1.0f));
    }

    private static BlockView occludingAt(int x, int y, int z) {
        return (BlockView) Proxy.newProxyInstance(
                TestProxySupport.contextClassLoader(),
                new Class<?>[]{BlockView.class},
                (proxy, method, args) -> "isBlockOccluding".equals(method.getName())
                        ? (Integer) args[0] == x && (Integer) args[1] == y && (Integer) args[2] == z
                        : TestProxySupport.defaultValue(method.getReturnType())
        );
    }
}
