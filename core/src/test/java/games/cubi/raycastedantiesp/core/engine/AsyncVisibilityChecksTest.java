package games.cubi.raycastedantiesp.core.engine;

import games.cubi.locatables.implementations.ImmutableLocatableImpl;
import games.cubi.raycastedantiesp.core.config.raycast.EntityConfig;
import games.cubi.raycastedantiesp.core.entity.EntityBypassRegistry;
import games.cubi.raycastedantiesp.core.players.PlayerData;
import games.cubi.raycastedantiesp.core.players.PlayerRegistry;
import games.cubi.raycastedantiesp.core.tracked.NettyEntity;
import games.cubi.raycastedantiesp.core.tracked.TrackedEntity;
import games.cubi.raycastedantiesp.core.utils.Clearable;
import games.cubi.raycastedantiesp.core.view.BlockView;
import games.cubi.raycastedantiesp.core.view.EntityView;
import games.cubi.raycastedantiesp.core.view.ViewRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.BasicConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertFalse;

class AsyncVisibilityChecksTest {
    private final List<UUID> registeredPlayers = new ArrayList<>();

    @BeforeEach
    void initialiseViews() {
        EntityBypassRegistry.reset();
        ViewRegistry.reset();
        ViewRegistry.initialise(
                ignored -> emptyBlockView(),
                ignored -> entityView(false),
                ignored -> entityView(true)
        );
    }

    @AfterEach
    void resetSharedState() {
        for (UUID player : registeredPlayers) {
            PlayerRegistry.getInstance().unregisterPlayer(player);
        }
        registeredPlayers.clear();
        EntityBypassRegistry.reset();
        ViewRegistry.reset();
    }

    @Test
    void bypassedVehicleDoesNotForceManagedPassengerVisible() throws SerializationException {
        UUID playerUUID = UUID.randomUUID();
        registeredPlayers.add(playerUUID);
        PlayerData playerData = PlayerRegistry.getInstance().registerAndGetPlayer(
                playerUUID,
                0,
                1,
                TestEntity::createSelf
        );
        UUID world = UUID.randomUUID();
        playerData.beginWorldTransition();
        playerData.completeWorldTransition(world);

        int bypassedVehicleID = 20;
        EntityBypassRegistry.addEntity(bypassedVehicleID);

        TestEntity target = new TestEntity(playerData, 100, 0, 0, 2, UUID.randomUUID(), true);
        target.setClientVisible(true);
        target.setVehicleID(bypassedVehicleID);
        @SuppressWarnings("unchecked")
        EntityView<NettyEntity<?>> view = (EntityView<NettyEntity<?>>) (EntityView<?>) playerData.entityView();
        view.insertEntity(world, target);

        new AsyncVisibilityChecks(null).processEntitySection(
                playerData,
                new ImmutableLocatableImpl(world, 0, 0, 0),
                playerData.blockView(),
                entityConfig(),
                false,
                5,
                playerData.acquireWorldEpoch(),
                TickTimingBatchNoOp.INSTANCE
        );

        assertFalse(target.visible(),
                "a bypassed vehicle must not turn its managed passenger into an always-visible target");
    }

    private static EntityConfig entityConfig() throws SerializationException {
        ConfigurationNode node = BasicConfigurationNode.root();
        node.node("enabled").set(true);
        node.node("hide-sounds-when-hidden").set(false);
        node.node("max-occluding-count").set(3);
        node.node("always-show-radius").set(24);
        node.node("raycast-radius").set(48);
        node.node("hide-on-spawn-distance").set(0);
        node.node("visible-recheck-interval-ticks").set(10);
        node.node("keep-client-entity-when-hidden").set(false);
        node.node("excluded-types").set(List.of());
        return EntityConfig.load(node, "checks.entity");
    }

    private static EntityView<?> entityView(boolean playerView) {
        Map<Integer, NettyEntity<?>> byID = new HashMap<>();
        Map<UUID, NettyEntity<?>> byUUID = new HashMap<>();
        return (EntityView<?>) Proxy.newProxyInstance(
                AsyncVisibilityChecksTest.class.getClassLoader(),
                new Class[]{EntityView.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "insertEntity" -> {
                        NettyEntity<?> entity = (NettyEntity<?>) args[1];
                        byID.put(entity.entityID(), entity);
                        byUUID.put(entity.entityUUID(), entity);
                        yield null;
                    }
                    case "getEntity" -> args[0] instanceof Integer
                            ? byID.get(args[0])
                            : byUUID.get(args[0]);
                    case "exists" -> args[0] instanceof Integer
                            ? byID.containsKey(args[0])
                            : byUUID.containsKey(args[0]);
                    case "size" -> byID.size();
                    case "getKnownEntities" -> List.copyOf(byUUID.keySet());
                    case "getKnownEntityIDs" -> byID.keySet().stream().mapToInt(Integer::intValue).toArray();
                    case "getPosition" -> byUUID.get(args[0]);
                    case "getEntityID" -> {
                        NettyEntity<?> entity = byUUID.get(args[0]);
                        yield entity == null ? -1 : entity.entityID();
                    }
                    case "isVisible" -> {
                        NettyEntity<?> entity = args[0] instanceof Integer
                                ? byID.get(args[0])
                                : byUUID.get(args[0]);
                        yield entity == null || entity.visible();
                    }
                    case "setVisibility" -> {
                        NettyEntity<?> entity = (NettyEntity<?>) args[0];
                        entity.setVisible((Boolean) args[1]);
                        entity.setLastChecked((Integer) args[2]);
                        yield null;
                    }
                    case "recordDirectVisibility" -> {
                        NettyEntity<?> entity = (NettyEntity<?>) args[0];
                        entity.setVisible((Boolean) args[1]);
                        entity.setLastChecked((Integer) args[2]);
                        yield true;
                    }
                    case "forEachNeedingRecheckEntity" -> {
                        @SuppressWarnings("unchecked")
                        Consumer<NettyEntity<?>> action = (Consumer<NettyEntity<?>>) args[4];
                        for (NettyEntity<?> entity : List.copyOf(byID.values())) {
                            action.accept(entity);
                        }
                        yield (Boolean) args[2] ? byID.size() : 0;
                    }
                    case "forEachNeedingRecheck" -> 0;
                    case "removeEntity", "flushPendingTransitions", "drainTransitions", "clear" -> null;
                    case "hasPendingTransitions" -> false;
                    case "isPlayerView" -> playerView;
                    case "getStringDataForDebugging" -> "test";
                    case "toString" -> "AsyncVisibilityChecksTestEntityView";
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static BlockView emptyBlockView() {
        return (BlockView) Proxy.newProxyInstance(
                AsyncVisibilityChecksTest.class.getClassLoader(),
                new Class[]{BlockView.class},
                (proxy, method, args) -> method.getName().equals("toString")
                        ? "AsyncVisibilityChecksTestBlockView"
                        : defaultValue(method.getReturnType())
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

        private TestEntity(
                PlayerData owningPlayer,
                double x,
                double y,
                double z,
                int entityID,
                UUID entityUUID,
                boolean visible) {
            super(owningPlayer, x, y, z, entityID, entityUUID, false, 0, visible);
        }

        private static TestEntity createSelf(PlayerData owningPlayer, int entityID, UUID playerUUID) {
            return new TestEntity(owningPlayer, entityID, playerUUID);
        }
    }
}
