package games.cubi.raycastedantiesp.core.players;

import games.cubi.raycastedantiesp.core.testsupport.TestProxySupport;
import games.cubi.raycastedantiesp.core.tracked.NettyEntity;
import games.cubi.raycastedantiesp.core.utils.Clearable;
import games.cubi.raycastedantiesp.core.view.BlockView;
import games.cubi.raycastedantiesp.core.view.EntityView;
import games.cubi.raycastedantiesp.core.view.ViewRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientStateResetterMissingMetadataTest {
    @BeforeEach
    void initialiseViews() {
        ViewRegistry.reset();
        ViewRegistry.initialise(
                ignored -> blockView(),
                ignored -> entityView(false),
                ignored -> entityView(true)
        );
    }

    @AfterEach
    void resetRegistries() {
        PlayerRegistry.getInstance().clear();
        ViewRegistry.reset();
    }

    @Test
    void missingWorldMetadataStillInvalidatesThePreRespawnGeneration() {
        UUID playerUUID = UUID.randomUUID();
        UUID oldWorldUUID = UUID.randomUUID();
        PlayerData playerData = PlayerRegistry.getInstance().registerAndGetPlayer(
                playerUUID, 0, 1, EntityStub::createSelf);

        playerData.beginWorldTransition();
        playerData.nettyData().setCurrentWorldName("world").setCurrentWorldMinHeight(-64);
        playerData.completeWorldTransition(oldWorldUUID);

        int previousEpoch = playerData.acquireWorldEpoch();
        AtomicInteger staleRuns = new AtomicInteger();
        Runnable staleWork = WorldEpochGuard.fence(
                playerData, previousEpoch, staleRuns::incrementAndGet);

        assertTrue(ClientStateResetter.resetForRespawn(playerData, null, null, -32));

        assertEquals(previousEpoch + 2, playerData.acquireWorldEpoch());
        assertTrue(PlayerData.isStableWorldEpoch(playerData.acquireWorldEpoch()));
        assertNull(playerData.nettyData().getCurrentWorldName());
        assertEquals(-32, playerData.nettyData().getCurrentWorldMinHeight());
        assertEquals(PlayerData.INVALID_WORLD_EPOCH,
                playerData.tryAcquireWorldEpochFor(oldWorldUUID));
        assertSame(playerData, PlayerRegistry.getInstance().getPlayerData(playerUUID));
        assertTrue(playerData.isConnected());

        staleWork.run();
        assertEquals(0, staleRuns.get());
    }

    private static BlockView blockView() {
        return (BlockView) Proxy.newProxyInstance(
                TestProxySupport.contextClassLoader(),
                new Class<?>[]{BlockView.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "clear" -> null;
                    case "toString" -> "MissingMetadataBlockView";
                    default -> TestProxySupport.defaultValue(method.getReturnType());
                }
        );
    }

    private static EntityView<?> entityView(boolean playerView) {
        return (EntityView<?>) Proxy.newProxyInstance(
                TestProxySupport.contextClassLoader(),
                new Class<?>[]{EntityView.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "clear" -> null;
                    case "getKnownEntityIDs" -> new int[0];
                    case "size" -> 0;
                    case "isPlayerView" -> playerView;
                    case "toString" -> "MissingMetadataEntityView";
                    default -> TestProxySupport.defaultValue(method.getReturnType());
                }
        );
    }

    private static final class EntityStub extends NettyEntity<Clearable> {
        private EntityStub(PlayerData owningPlayer, int entityID, UUID entityUUID) {
            super(owningPlayer, entityID, entityUUID);
        }

        private static EntityStub createSelf(
                PlayerData owningPlayer, int entityID, UUID playerUUID) {
            return new EntityStub(owningPlayer, entityID, playerUUID);
        }
    }
}
