package games.cubi.raycastedantiesp.core.players;

import games.cubi.raycastedantiesp.core.tracked.NettyEntity;
import games.cubi.raycastedantiesp.core.tracked.TrackedEntity;
import games.cubi.raycastedantiesp.core.utils.Clearable;
import games.cubi.raycastedantiesp.core.view.BlockView;
import games.cubi.raycastedantiesp.core.view.EntityView;
import games.cubi.raycastedantiesp.core.view.ViewRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

        TestEntity hiddenEntity = new TestEntity(playerData, 20, UUID.randomUUID(), false);
        hiddenEntity.setClientVisible(false);
        TestEntity playerEntity = new TestEntity(playerData, 21, UUID.randomUUID(), true);
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
        for (boolean bypass : List.of(false, true)) {
            UUID playerUUID = UUID.randomUUID();
            UUID worldUUID = UUID.randomUUID();
            PlayerData playerData = register(playerUUID);
            playerData.setBypassPermission(bypass);
            establishWorld(playerData, "world-" + bypass, worldUUID, -64);
            entityView(playerData.entityView()).insertEntity(
                    worldUUID, new TestEntity(playerData, bypass ? 41 : 40, UUID.randomUUID(), true));

            int previousEpoch = playerData.acquireWorldEpoch();
            assertTrue(ClientStateResetter.resetForRespawn(
                    playerData, "world-" + bypass, worldUUID, -64));
            assertEquals(previousEpoch + 2, playerData.acquireWorldEpoch());
            assertEquals(0, playerData.entityView().size());
            assertTrue(playerData.hasBypassPermission() == bypass);
        }
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
                playerUUID, 0, 1, TestEntity::createSelf);
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
        Map<Integer, TrackedEntity<?>> entitiesByID = new HashMap<>();
        Map<UUID, TrackedEntity<?>> entitiesByUUID = new HashMap<>();
        return (EntityView<?>) Proxy.newProxyInstance(
                ClientStateResetterTest.class.getClassLoader(),
                new Class[]{EntityView.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "insertEntity" -> {
                        TrackedEntity<?> entity = (TrackedEntity<?>) args[1];
                        entitiesByID.put(entity.entityID(), entity);
                        entitiesByUUID.put(entity.entityUUID(), entity);
                        yield null;
                    }
                    case "removeEntity" -> {
                        TrackedEntity<?> removed = entitiesByID.remove((Integer) args[0]);
                        if (removed != null) {
                            entitiesByUUID.remove(removed.entityUUID());
                        }
                        yield null;
                    }
                    case "clear" -> {
                        entitiesByID.clear();
                        entitiesByUUID.clear();
                        yield null;
                    }
                    case "getEntity" -> args[0] instanceof Integer
                            ? entitiesByID.get(args[0])
                            : entitiesByUUID.get(args[0]);
                    case "exists" -> args[0] instanceof Integer
                            ? entitiesByID.containsKey(args[0])
                            : entitiesByUUID.containsKey(args[0]);
                    case "size" -> entitiesByID.size();
                    case "getKnownEntities" -> List.copyOf(entitiesByUUID.keySet());
                    case "getKnownEntityIDs" -> entitiesByID.keySet().stream()
                            .mapToInt(Integer::intValue).toArray();
                    case "isVisible" -> {
                        TrackedEntity<?> entity = args[0] instanceof Integer
                                ? entitiesByID.get(args[0])
                                : entitiesByUUID.get(args[0]);
                        yield entity == null || entity.visible();
                    }
                    case "recordDirectVisibility" -> {
                        NettyEntity<?> entity = (NettyEntity<?>) args[0];
                        int expectedEpoch = (Integer) args[3];
                        boolean current = expectedEpoch == epochSupplier.getAsInt()
                                && entitiesByUUID.get(entity.entityUUID()) == entity;
                        if (current) {
                            entity.setVisible((Boolean) args[1]);
                            entity.setLastChecked((Integer) args[2]);
                        }
                        yield current;
                    }
                    case "isPlayerView" -> playerView;
                    case "hasPendingTransitions" -> false;
                    case "toString" -> "TestEntityView";
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private BlockView blockView() {
        return (BlockView) Proxy.newProxyInstance(
                ClientStateResetterTest.class.getClassLoader(),
                new Class[]{BlockView.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "clear" -> {
                        blockClearCount.incrementAndGet();
                        yield null;
                    }
                    case "toString" -> "TestBlockView";
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0F;
        }
        if (returnType == double.class) {
            return 0D;
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == char.class) {
            return (char) 0;
        }
        return 0;
    }

    private static final class TestEntity extends NettyEntity<Clearable> {
        private TestEntity(PlayerData owningPlayer, int entityID, UUID entityUUID) {
            super(owningPlayer, entityID, entityUUID);
        }

        private TestEntity(PlayerData owningPlayer, int entityID, UUID entityUUID, boolean visible) {
            super(owningPlayer, 0, 0, 0, entityID, entityUUID, false, 0, visible);
        }

        private static TestEntity createSelf(PlayerData owningPlayer, int entityID, UUID playerUUID) {
            return new TestEntity(owningPlayer, entityID, playerUUID);
        }
    }
}
