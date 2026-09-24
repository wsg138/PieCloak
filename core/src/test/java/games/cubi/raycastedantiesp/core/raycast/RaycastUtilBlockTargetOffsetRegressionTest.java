package games.cubi.raycastedantiesp.core.raycast;

import games.cubi.locatables.api.Locatable;
import games.cubi.locatables.implementations.ImmutableBlockSpatialImpl;
import games.cubi.locatables.implementations.ImmutableLocatableImpl;
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
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{BlockView.class},
                (proxy, method, args) -> {
                    if ("isBlockOccluding".equals(method.getName())) {
                        return (Integer) args[0] == x && (Integer) args[1] == y && (Integer) args[2] == z;
                    }
                    Class<?> returnType = method.getReturnType();
                    if (!returnType.isPrimitive()) {
                        return null;
                    }
                    if (returnType == boolean.class) return false;
                    if (returnType == byte.class) return (byte) 0;
                    if (returnType == short.class) return (short) 0;
                    if (returnType == int.class) return 0;
                    if (returnType == long.class) return 0L;
                    if (returnType == float.class) return 0F;
                    if (returnType == double.class) return 0D;
                    if (returnType == char.class) return (char) 0;
                    return null;
                }
        );
    }
}
