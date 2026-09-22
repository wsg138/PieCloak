package games.cubi.raycastedantiesp.core.view.controller;

import games.cubi.raycastedantiesp.core.entity.EntityBypassRegistry;
import games.cubi.raycastedantiesp.core.players.PlayerData;
import games.cubi.raycastedantiesp.core.players.PlayerRegistry;
import games.cubi.raycastedantiesp.core.tracked.NettyEntity;
import games.cubi.raycastedantiesp.core.tracked.TrackedEntity;
import games.cubi.raycastedantiesp.core.utils.Clearable;
import games.cubi.raycastedantiesp.core.view.BlockView;
import games.cubi.raycastedantiesp.core.view.EntityView;
import games.cubi.raycastedantiesp.core.view.ViewRegistry;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketEntityViewControllerTest {
    private static final HarnessController CONTROLLER = new HarnessController();
    private static final Map<Class<?>, Object> PRIMITIVE_DEFAULTS = Map.of(
            boolean.class, false,
            byte.class, (byte) 0,
            short.class, (short) 0,
            int.class, 0,
            long.class, 0L,
            float.class, 0F,
            double.class, 0D,
            char.class, (char) 0
    );
    private final List<UUID> registeredPlayers = new ArrayList<>();

    @BeforeAll
    static void initialiseViews() {
        ViewRegistry.initialise(
                ignored -> emptyBlockView(),
                ignored -> entityView(false),
                ignored -> entityView(true)
        );
    }

    @BeforeEach
    void clearControllerState() {
        CONTROLLER.directlyShownEntityIDs.clear();
        CONTROLLER.replacementPassengerPackets.clear();
    }

    @AfterEach
    void unregisterPlayers() {
        for (UUID player : registeredPlayers) {
            PlayerRegistry.getInstance().unregisterPlayer(player);
        }
        registeredPlayers.clear();
        EntityBypassRegistry.reset();
    }

    @Test
    void directlyShownSelfVehiclePassengersRemainMountedInReplacementPacket() {
        UUID world = UUID.randomUUID();
        PlayerData playerData = registerPlayer(world);

        HarnessEntity firstPassenger = insertPassenger(playerData, world, 2, false, false);
        HarnessEntity secondPassenger = insertPassenger(playerData, world, 3, false, false);

        HarnessEntity self = (HarnessEntity) playerData.nettyData().getSelfEntity();
        boolean cancelled = CONTROLLER.handleEntityPassengersNow(self, new int[]{2, 3}, playerData, 17);

        assertTrue(cancelled, "the original passenger packet must be suppressed");
        assertEquals(List.of(2, 3), CONTROLLER.directlyShownEntityIDs);
        assertEquals(1, CONTROLLER.replacementPassengerPackets.size());
        assertArrayEquals(new int[]{2, 3}, CONTROLLER.replacementPassengerPackets.get(0));
        for (HarnessEntity passenger : List.of(firstPassenger, secondPassenger)) {
            assertTrue(passenger.visible(), "direct SHOW must make the passenger engine-visible");
            assertTrue(passenger.clientVisible(), "direct SHOW must make the passenger client-visible");
            assertEquals(1, passenger.vehicleID(), "the passenger must remain attached to the self vehicle");
        }
        assertArrayEquals(new int[]{2, 3}, self.passengerIDs());
    }

    @Test
    void bypassedVehicleFiltersHiddenManagedPassengerWithoutReveal() {
        UUID world = UUID.randomUUID();
        PlayerData playerData = registerPlayer(world);
        HarnessEntity passenger = insertPassenger(playerData, world, 2, false, false);
        EntityBypassRegistry.addEntity(20);

        boolean cancelled = CONTROLLER.handleEntityPassengers(20, new int[]{2}, playerData, 17);

        assertTrue(cancelled, "the original relationship packet must not expose the hidden passenger");
        assertFalse(passenger.visible());
        assertFalse(passenger.clientVisible());
        assertEquals(20, passenger.vehicleID());
        assertArrayEquals(new int[]{2}, playerData.nettyData().getUnresolvedPassengers(20));
        assertEquals(1, CONTROLLER.replacementPassengerPackets.size());
        assertArrayEquals(new int[0], CONTROLLER.replacementPassengerPackets.get(0));
        assertTrue(CONTROLLER.directlyShownEntityIDs.isEmpty());
    }

    @Test
    void passengerResolvedAfterBypassedRelationshipDoesNotReveal() {
        UUID world = UUID.randomUUID();
        PlayerData playerData = registerPlayer(world);
        EntityBypassRegistry.addEntity(20);

        assertTrue(CONTROLLER.handleEntityPassengers(20, new int[]{2}, playerData, 17));
        HarnessEntity passenger = insertPassenger(playerData, world, 2, false, false);
        CONTROLLER.reconcileUnresolvedPassengers(passenger, playerData);

        assertFalse(passenger.visible());
        assertFalse(passenger.clientVisible());
        assertEquals(20, passenger.vehicleID());
        assertTrue(CONTROLLER.directlyShownEntityIDs.isEmpty());
    }

    @Test
    void vehicleIdentifiedAsBypassedAfterRelationshipDoesNotRevealPassenger() {
        UUID world = UUID.randomUUID();
        PlayerData playerData = registerPlayer(world);
        HarnessEntity passenger = insertPassenger(playerData, world, 2, false, false);

        CONTROLLER.handleEntityPassengers(20, new int[]{2}, playerData, 17);
        EntityBypassRegistry.addEntity(20);
        CONTROLLER.handleBypassedEntitySpawn(20, playerData, 18);

        assertFalse(passenger.visible());
        assertFalse(passenger.clientVisible());
        assertEquals(20, passenger.vehicleID());
        assertTrue(CONTROLLER.directlyShownEntityIDs.isEmpty());
    }

    private PlayerData registerPlayer(UUID world) {
        UUID playerUUID = UUID.randomUUID();
        registeredPlayers.add(playerUUID);
        PlayerData playerData = PlayerRegistry.getInstance().registerAndGetPlayer(
                playerUUID,
                0,
                1,
                HarnessEntity::createSelf
        );
        playerData.beginWorldTransition();
        playerData.completeWorldTransition(world);
        return playerData;
    }

    private static HarnessEntity insertPassenger(
            PlayerData playerData, UUID world, int entityID, boolean visible, boolean clientVisible) {
        HarnessEntity passenger = new HarnessEntity(playerData, entityID, UUID.randomUUID(), visible);
        passenger.setClientVisible(clientVisible);
        @SuppressWarnings("unchecked")
        EntityView<NettyEntity<?>> entityView =
                (EntityView<NettyEntity<?>>) (EntityView<?>) playerData.entityView();
        entityView.insertEntity(world, passenger);
        return passenger;
    }

    private static EntityView<?> entityView(boolean playerView) {
        return (EntityView<?>) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class[]{EntityView.class},
                new EntityViewHarness(playerView)
        );
    }

    private static BlockView emptyBlockView() {
        return (BlockView) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class[]{BlockView.class},
                (proxy, method, args) -> "toString".equals(method.getName())
                        ? "TestBlockView"
                        : defaultValue(method.getReturnType())
        );
    }

    private static Object defaultValue(Class<?> returnType) {
        return returnType.isPrimitive() ? PRIMITIVE_DEFAULTS.get(returnType) : null;
    }

    @FunctionalInterface
    private interface MethodHandler {
        Object invoke(Object[] args);
    }

    private static final class EntityViewHarness implements InvocationHandler {
        private final Map<Integer, TrackedEntity<?>> entitiesByID = new ConcurrentHashMap<>();
        private final Map<UUID, TrackedEntity<?>> entitiesByUUID = new ConcurrentHashMap<>();
        private final Map<String, MethodHandler> handlers = new ConcurrentHashMap<>();

        private EntityViewHarness(boolean playerView) {
            handlers.put("insertEntity", this::insertEntity);
            handlers.put("removeEntity", this::removeEntity);
            handlers.put("getEntity", this::getEntity);
            handlers.put("exists", this::exists);
            handlers.put("size", ignored -> entitiesByID.size());
            handlers.put("getKnownEntities", ignored -> List.copyOf(entitiesByUUID.keySet()));
            handlers.put("getKnownEntityIDs", ignored -> entitiesByID.keySet().stream().mapToInt(Integer::intValue).toArray());
            handlers.put("getEntityID", this::getEntityID);
            handlers.put("getPosition", args -> entitiesByUUID.get(args[0]));
            handlers.put("isVisible", this::isVisible);
            handlers.put("recordDirectVisibility", this::recordDirectVisibility);
            handlers.put("setVisibility", this::setVisibility);
            handlers.put("forEachNeedingRecheck", ignored -> 0);
            handlers.put("forEachNeedingRecheckEntity", ignored -> 0);
            handlers.put("hasPendingTransitions", ignored -> false);
            handlers.put("flushPendingTransitions", ignored -> null);
            handlers.put("drainTransitions", ignored -> null);
            handlers.put("clear", ignored -> null);
            handlers.put("isPlayerView", ignored -> playerView);
            handlers.put("getStringDataForDebugging", ignored -> "test");
            handlers.put("toString", ignored -> "TestEntityView");
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            MethodHandler handler = handlers.get(method.getName());
            return handler == null ? defaultValue(method.getReturnType()) : handler.invoke(args);
        }

        private Object insertEntity(Object[] args) {
            TrackedEntity<?> entity = (TrackedEntity<?>) args[1];
            entitiesByID.put(entity.entityID(), entity);
            entitiesByUUID.put(entity.entityUUID(), entity);
            return null;
        }

        private Object removeEntity(Object[] args) {
            TrackedEntity<?> removed = entitiesByID.remove((Integer) args[0]);
            if (removed != null) {
                entitiesByUUID.remove(removed.entityUUID());
            }
            return null;
        }

        private Object getEntity(Object[] args) {
            Object key = args[0];
            return key instanceof Integer ? entitiesByID.get(key) : entitiesByUUID.get(key);
        }

        private Object exists(Object[] args) {
            Object key = args[0];
            return key instanceof Integer ? entitiesByID.containsKey(key) : entitiesByUUID.containsKey(key);
        }

        private Object getEntityID(Object[] args) {
            TrackedEntity<?> entity = entitiesByUUID.get(args[0]);
            return entity == null ? -1 : entity.entityID();
        }

        private Object isVisible(Object[] args) {
            TrackedEntity<?> entity = trackedEntity(args[0]);
            return entity == null || entity.visible();
        }

        private Object recordDirectVisibility(Object[] args) {
            return updateVisibility(args);
        }

        private Object setVisibility(Object[] args) {
            updateVisibility(args);
            return null;
        }

        private boolean updateVisibility(Object[] args) {
            NettyEntity<?> entity = (NettyEntity<?>) args[0];
            boolean current = entitiesByUUID.get(entity.entityUUID()) == entity && !entity.isSelfEntity();
            if (current) {
                entity.setVisible((Boolean) args[1]);
                entity.setLastChecked((Integer) args[2]);
            }
            return current;
        }

        private TrackedEntity<?> trackedEntity(Object key) {
            return key instanceof Integer ? entitiesByID.get(key) : entitiesByUUID.get(key);
        }
    }

    private static final class HarnessController extends PacketEntityViewController<Void> {
        private final List<Integer> directlyShownEntityIDs = new ArrayList<>();
        private final List<int[]> replacementPassengerPackets = new ArrayList<>();

        @Override
        protected NettyEntity<?> createSelfEntity(PlayerData ownData, int entityID, UUID playerUUID) {
            return HarnessEntity.createSelf(ownData, entityID, playerUUID);
        }

        @Override
        protected NettyEntity<?> processEntitySpawn(PlayerData playerData, Void packet, UUID world, int currentTick) {
            return null;
        }

        @Override
        protected void processDirectEntityShow(PlayerData playerData, EntityView<?> view, NettyEntity<?> entity, int worldEpoch) {
            directlyShownEntityIDs.add(entity.entityID());
            entity.setClientVisible(true);
        }

        @Override
        protected void sendEntityPassengerPacket(int vehicle, IntArrayList passengers, PlayerData playerData) {
            replacementPassengerPackets.add(passengers.toIntArray());
        }

        @Override
        protected int processRelativeMovePacket(Void packet, PlayerData playerData, int currentTick) {
            return -1;
        }

        @Override
        protected int processRelativeMoveAndRotationPacket(Void packet, PlayerData playerData, int currentTick) {
            return -1;
        }

        @Override
        protected int processTeleportPacket(Void packet, PlayerData playerData, int currentTick) {
            return -1;
        }

        @Override
        protected int processPositionSyncPacket(Void packet, PlayerData playerData, int currentTick) {
            return -1;
        }

        @Override
        protected void processTrackedMetadata(Void packet, NettyEntity<?> entity) {}

        @Override
        protected void cachePacket(Void packet, int entityID, PlayerData playerData, int currentTick) {}

        @Override
        protected int processRotationPacket(Void packet, PlayerData playerData, int currentTick) {
            return -1;
        }

        @Override
        protected int processHeadLookPacket(Void packet, PlayerData playerData, int currentTick) {
            return -1;
        }

        @Override
        protected int processEntityVelocityPacket(Void packet, PlayerData playerData, int currentTick) {
            return -1;
        }

        @Override
        protected void insertEntityToPlayerView(NettyEntity<?> entity, PlayerData playerData, UUID world) {}

        @Override
        protected void insertEntityToEntityView(NettyEntity<?> entity, PlayerData playerData, UUID world) {}
    }

    private static final class HarnessEntity extends NettyEntity<Clearable> {
        private HarnessEntity(PlayerData owningPlayer, int entityID, UUID entityUUID) {
            super(owningPlayer, entityID, entityUUID);
        }

        private HarnessEntity(PlayerData owningPlayer, int entityID, UUID entityUUID, boolean visible) {
            super(owningPlayer, 0, 0, 0, entityID, entityUUID, false, 0, visible);
        }

        private static HarnessEntity createSelf(PlayerData owningPlayer, int entityID, UUID playerUUID) {
            return new HarnessEntity(owningPlayer, entityID, playerUUID);
        }
    }
}
