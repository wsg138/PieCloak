package games.cubi.raycastedantiesp.core.players;

import games.cubi.raycastedantiesp.core.testsupport.TestEntityViewProxy;
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
import java.util.function.IntSupplier;

import static games.cubi.raycastedantiesp.core.tracked.NettyEntity.NO_LEASHER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientStateResetterTest {
    private final AtomicInteger blockClearCount = new AtomicInteger();

    @BeforeEach
    void initialiseViews() {
        ViewRegistry.reset();
        ViewRegistry.initialise(
                ignored -> blockView(),
                epochSupplier -> entityView(epochSupplier, false),
                epochSupplier -> entityView(epochSupplier, true)
        );
    }

    @AfterEach
    void resetRegistries() {
        PlayerRegistry.getInstance().clear();
        ViewRegistry.reset();
    }

    @Test
    void sameWorldRespawnAdvancesEpochAndRejectsPreRespawnWork() {
        UUID playerUUID = UUID.randomUUID();
        UUID worldUUID = UUID.randomUUID();
        PlayerData playerData = register(playerUUID);
        establishWorld(playerData, "world", worldUUID, -64);

        EntityStub hiddenEntity = new EntityStub(playerData, 20, UUID.randomUUID(), false);
        hiddenEntity.setClientVisible(false);
        EntityStub playerEntity = new EntityStub(playerData, 21, UUID.randomUUID(), true);
        entityView(playerData.entityView()).insertEntity(worldUUID, hiddenEntity);
        entityView(playerData.playerView()).insertEntity(worldUUID, playerEntity);
        playerData.nettyData().addUnresolvedLeash(30, 31);
        playerData.nettyData().setUnresolvedPassengers(32, new int[]{33});
        playerData.updateOwnLocation(worldUUID, 15, 70, -8);

        int previousEpoch = playerData.acquireWorldEpoch();
        AtomicInteger deferredRuns = new AtomicInteger();
        Runnable staleAfterSend = WorldEpochGuard.fence(
                playerData, previousEpoch, deferredRuns::incrementAndGet);

        assertTrue(ClientStateResetter.resetForRespawn(playerData, "world", worldUUID, -32));

        assertEquals(previousEpoch + 2, playerData.acquireWorldEpoch());
        assertEquals(0, playerData.entityView().size());
        assertEquals(0, playerData.playerView().size());
        assertEquals(1, blockClearCount.get());
        assertEquals(NO_LEASHER, playerData.nettyData().getUnresolvedHolderForLeashedEntity(31));
        assertNull(playerData.nettyData().getUnresolvedPassengers(32));
        assertNull(playerData.ownLocation().world());
        assertEquals("world", playerData.nettyData().getCurrentWorldName());
        assertEquals(-32, playerData.nettyData().getCurrentWorldMinHeight());
        assertTrue(playerData.nettyData().consumeExpectedWorldTransitionDestroyEntityID(20));
        assertTrue(playerData.nettyData().consumeExpectedWorldTransitionDestroyEntityID(21));

        assertFalse(playerData.entityView().recordDirectVisibility(
                hiddenEntity, true, 1, previousEpoch));
        staleAfterSend.run();
        assertEquals(0, deferredRuns.get());

        WorldEpochGuard.fence(playerData, playerData.acquireWorldEpoch(),
                deferredRuns::incrementAndGet).run();
        assertEquals(1, deferredRuns.get());
    }

    @Test
    void managedAndBypassViewersUseTheSameRespawnResetContract() {
        assertRespawnResetContract(false, 40);
        assertRespawnResetContract(true, 41);
    }

    private void assertRespawnResetContract(boolean bypass, int entityID) {
        UUID playerUUID = UUID.randomUUID();
        UUID worldUUID = UUID.randomUUID();
        PlayerData playerData = register(playerUUID);
        playerData.setBypassPermission(bypass);
        establishWorld(playerData, "world-" + bypass, worldUUID, -64);
        entityView(playerData.entityView()).insertEntity(
                worldUUID, new EntityStub(playerData, entityID, UUID.randomUUID(), true));

        int previousEpoch = playerData.acquireWorldEpoch();
        assertTrue(ClientStateResetter.resetForRespawn(
                playerData, "world-" + bypass, worldUUID, -64));
        assertEquals(previousEpoch + 2, playerData.acquireWorldEpoch());
        assertEquals(0, playerData.entityView().size());
        assertEquals(bypass, playerData.hasBypassPermission());
    }

    @Test
    void obsoleteCleanupCannotUnregisterANewerPlayerGeneration() {
        UUID playerUUID = UUID.randomUUID();
        PlayerData oldGeneration = register(playerUUID);
        PlayerData newGeneration = register(playerUUID);

        assertFalse(PlayerRegistry.getInstance().unregisterPlayer(playerUUID, oldGeneration));
        assertSame(newGeneration, PlayerRegistry.getInstance().getPlayerData(playerUUID));
        assertTrue(newGeneration.isConnected());
    }

    private PlayerData register(UUID playerUUID) {
        return PlayerRegistry.getInstance().registerAndGetPlayer(
                playerUUID, 0, 1, EntityStub::createSelf);
    }

    private static void establishWorld(PlayerData playerData, String worldName, UUID worldUUID,
            int minWorldHeight) {
        playerData.beginWorldTransition();
        playerData.nettyData().setCurrentWorldName(worldName).setCurrentWorldMinHeight(minWorldHeight);
        playerData.completeWorldTransition(worldUUID);
    }

    @SuppressWarnings("unchecked")
    private static EntityView<NettyEntity<?>> entityView(EntityView<?> view) {
        return (EntityView<NettyEntity<?>>) view;
    }

    private EntityView<?> entityView(IntSupplier epochSupplier, boolean playerView) {
        return TestEntityViewProxy.create(epochSupplier, playerView, "EntityStubView");
    }

    private BlockView blockView() {
        return (BlockView) Proxy.newProxyInstance(
                TestProxySupport.contextClassLoader(),
                new Class[]{BlockView.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "clear" -> {
                        blockClearCount.incrementAndGet();
                        yield null;
                    }
                    case "toString" -> "TestBlockView";
                    default -> TestProxySupport.defaultValue(method.getReturnType());
                }
        );
    }

    private static final class EntityStub extends NettyEntity<Clearable> {
        private EntityStub(PlayerData owningPlayer, int entityID, UUID entityUUID) {
            super(owningPlayer, entityID, entityUUID);
        }

        private EntityStub(PlayerData owningPlayer, int entityID, UUID entityUUID, boolean visible) {
            super(owningPlayer, 0, 0, 0, entityID, entityUUID, false, 0, visible);
        }

        private static EntityStub createSelf(PlayerData owningPlayer, int entityID, UUID playerUUID) {
            return new EntityStub(owningPlayer, entityID, playerUUID);
        }
    }
}
