package games.cubi.raycastedantiesp.core.players;

import games.cubi.raycastedantiesp.core.testsupport.TestEntityViewProxy;
import games.cubi.raycastedantiesp.core.testsupport.TestProxySupport;
import games.cubi.raycastedantiesp.core.tracked.NettyEntity;
import games.cubi.raycastedantiesp.core.utils.Clearable;
import games.cubi.raycastedantiesp.core.view.BlockView;
import games.cubi.raycastedantiesp.core.view.ViewRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class WorldEpochGuardTest {
    @BeforeEach
    void initialiseViews() {
        ViewRegistry.reset();
        ViewRegistry.initialise(
                ignored -> emptyBlockView(),
                epochSupplier -> TestEntityViewProxy.create(epochSupplier, false, "EntityView"),
                epochSupplier -> TestEntityViewProxy.create(epochSupplier, true, "PlayerView")
        );
    }

    @AfterEach
    void resetState() {
        PlayerRegistry.getInstance().clear();
        ViewRegistry.reset();
    }

    @Test
    void deferredWorkCannotRunDuringUnstableWorldTransitionEpoch() {
        PlayerData playerData = PlayerRegistry.getInstance().registerAndGetPlayer(
                UUID.randomUUID(), 0, 1, EntityStub::createSelf);
        playerData.beginWorldTransition();
        int unstableEpoch = playerData.acquireWorldEpoch();
        AtomicInteger runs = new AtomicInteger();

        assertFalse(PlayerData.isStableWorldEpoch(unstableEpoch));
        WorldEpochGuard.fence(playerData, unstableEpoch, runs::incrementAndGet).run();

        assertEquals(0, runs.get());
    }

    private static BlockView emptyBlockView() {
        return (BlockView) Proxy.newProxyInstance(
                TestProxySupport.contextClassLoader(),
                new Class[]{BlockView.class},
                (proxy, method, args) -> TestProxySupport.defaultValue(method.getReturnType())
        );
    }

    private static final class EntityStub extends NettyEntity<Clearable> {
        private EntityStub(PlayerData owningPlayer, int entityID, UUID entityUUID) {
            super(owningPlayer, entityID, entityUUID);
        }

        private static EntityStub createSelf(PlayerData owningPlayer, int entityID, UUID playerUUID) {
            return new EntityStub(owningPlayer, entityID, playerUUID);
        }
    }
}
