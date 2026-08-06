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

class RespawnEpochBoundaryTest {
    private final AtomicInteger blockViewClears = new AtomicInteger();

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
    void preRespawnShowRelationshipAndBlockCallbacksCannotCrossSameWorldBoundary() {
        UUID playerUUID = UUID.randomUUID();
        UUID worldUUID = UUID.randomUUID();
        PlayerData playerData = PlayerRegistry.getInstance().registerAndGetPlayer(
                playerUUID, 0, 1, TestEntity::createSelf);
        establishWorld(playerData, "world", worldUUID);

        int reusedEntityID = 77;
        TestEntity preRespawnEntity = new TestEntity(
                playerData, reusedEntityID, UUID.randomUUID(), false);
        preRespawnEntity.setClientVisible(false);
        entityView(playerData.entityView()).insertEntity(worldUUID, preRespawnEntity);
        playerData.nettyData().addUnresolvedLeash(80, 81);
        playerData.nettyData().setUnresolvedPassengers(82, new int[]{83});

        int preRespawnEpoch = playerData.acquireWorldEpoch();
        AtomicInteger staleShowWrites = new AtomicInteger();
        AtomicInteger staleRelationshipWrites = new AtomicInteger();
        AtomicInteger staleBlockRepairWrites = new AtomicInteger();
        TestEntity replacement = new TestEntity(
                playerData, reusedEntityID, UUID.randomUUID(), false);
        replacement.setClientVisible(false);

        Runnable queuedShow = WorldEpochGuard.fence(playerData, preRespawnEpoch, () -> {
            staleShowWrites.incrementAndGet();
            replacement.setClientVisible(true);
        });
        Runnable queuedRelationshipReplay = WorldEpochGuard.fence(
                playerData, preRespawnEpoch, staleRelationshipWrites::incrementAndGet);
        Runnable queuedBlockRepair = WorldEpochGuard.fence(
                playerData, preRespawnEpoch, staleBlockRepairWrites::incrementAndGet);

        assertTrue(ClientStateResetter.resetForRespawn(
                playerData, "world", worldUUID, -64));
        entityView(playerData.entityView()).insertEntity(worldUUID, replacement);

        queuedShow.run();
        queuedRelationshipReplay.run();
        queuedBlockRepair.run();

        assertEquals(preRespawnEpoch + 2, playerData.acquireWorldEpoch());
        assertEquals(0, staleShowWrites.get());
        assertEquals(0, staleRelationshipWrites.get());
        assertEquals(0, staleBlockRepairWrites.get());
        assertFalse(replacement.clientVisible());
        assertSame(replacement, playerData.entityView().getEntity(reusedEntityID));
        assertNull(playerData.entityView().getEntity(preRespawnEntity.entityUUID()));
        assertEquals(NO_LEASHER,
                playerData.nettyData().getUnresolvedHolderForLeashedEntity(81));
        assertNull(playerData.nettyData().getUnresolvedPassengers(82));
        assertEquals(1, blockViewClears.get());
    }

    private static void establishWorld(PlayerData playerData, String worldName, UUID worldUUID) {
        playerData.beginWorldTransition();
        playerData.nettyData().setCurrentWorldName(worldName).setCurrentWorldMinHeight(-64);
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
                RespawnEpochBoundaryTest.class.getClassLoader(),
                new Class[]{EntityView.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "insertEntity" -> {
                        TrackedEntity<?> entity = (TrackedEntity<?>) args[1];
                        entitiesByID.put(entity.entityID(), entity);
                        entitiesByUUID.put(entity.entityUUID(), entity);
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
                    case "size" -> entitiesByID.size();
                    case "getKnownEntities" -> List.copyOf(entitiesByUUID.keySet());
                    case "getKnownEntityIDs" -> entitiesByID.keySet().stream()
                            .mapToInt(Integer::intValue).toArray();
                    case "recordDirectVisibility" -> {
                        NettyEntity<?> entity = (NettyEntity<?>) args[0];
                        boolean current = (Integer) args[3] == epochSupplier.getAsInt()
                                && entitiesByUUID.get(entity.entityUUID()) == entity;
                        if (current) {
                            entity.setVisible((Boolean) args[1]);
                            entity.setLastChecked((Integer) args[2]);
                        }
                        yield current;
                    }
                    case "isPlayerView" -> playerView;
                    case "hasPendingTransitions" -> false;
                    case "toString" -> "RespawnEpochEntityView";
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private BlockView blockView() {
        return (BlockView) Proxy.newProxyInstance(
                RespawnEpochBoundaryTest.class.getClassLoader(),
                new Class[]{BlockView.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "clear" -> {
                        blockViewClears.incrementAndGet();
                        yield null;
                    }
                    case "toString" -> "RespawnEpochBlockView";
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

        private TestEntity(PlayerData owningPlayer, int entityID, UUID entityUUID,
                boolean visible) {
            super(owningPlayer, 0, 0, 0, entityID, entityUUID, false, 0, visible);
        }

        private static TestEntity createSelf(PlayerData owningPlayer, int entityID,
                UUID playerUUID) {
            return new TestEntity(owningPlayer, entityID, playerUUID);
        }
    }
}
