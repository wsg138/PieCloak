package games.cubi.raycastedantiesp.core.testsupport;

import games.cubi.raycastedantiesp.core.tracked.NettyEntity;
import games.cubi.raycastedantiesp.core.tracked.TrackedEntity;
import games.cubi.raycastedantiesp.core.view.EntityView;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;

public final class TestEntityViewProxy implements InvocationHandler {
    private final Map<Integer, TrackedEntity<?>> entitiesByID = new ConcurrentHashMap<>();
    private final Map<UUID, TrackedEntity<?>> entitiesByUUID = new ConcurrentHashMap<>();
    private final IntSupplier epochSupplier;
    private final boolean playerView;
    private final String description;

    private TestEntityViewProxy(
            IntSupplier epochSupplier,
            boolean playerView,
            String description) {
        this.epochSupplier = epochSupplier;
        this.playerView = playerView;
        this.description = description;
    }

    public static EntityView<?> create(
            IntSupplier epochSupplier,
            boolean playerView,
            String description) {
        TestEntityViewProxy handler = new TestEntityViewProxy(
                epochSupplier, playerView, description);
        return (EntityView<?>) Proxy.newProxyInstance(
                TestProxySupport.contextClassLoader(),
                new Class[]{EntityView.class},
                handler
        );
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        String name = method.getName();
        if (name.length() <= 6) {
            return invokeShort(name, method, args);
        }
        if (name.length() <= 12) {
            return invokeMedium(name, method, args);
        }
        return invokeLong(name, method, args);
    }

    private Object invokeShort(String name, Method method, Object[] args) {
        return switch (name) {
            case "clear" -> clear();
            case "exists" -> exists(args[0]);
            case "size" -> entitiesByID.size();
            default -> TestProxySupport.defaultValue(method.getReturnType());
        };
    }

    private Object invokeMedium(String name, Method method, Object[] args) {
        return switch (name) {
            case "getEntity" -> getEntity(args[0]);
            case "insertEntity" -> insertEntity(args);
            case "isPlayerView" -> playerView;
            case "isVisible" -> isVisible(args[0]);
            case "removeEntity" -> removeEntity(args[0]);
            case "toString" -> description;
            default -> TestProxySupport.defaultValue(method.getReturnType());
        };
    }

    private Object invokeLong(String name, Method method, Object[] args) {
        return switch (name) {
            case "getKnownEntities" -> List.copyOf(entitiesByUUID.keySet());
            case "getKnownEntityIDs" -> knownEntityIDs();
            case "hasPendingTransitions" -> false;
            case "recordDirectVisibility" -> recordDirectVisibility(args);
            default -> TestProxySupport.defaultValue(method.getReturnType());
        };
    }

    private Object clear() {
        entitiesByID.clear();
        entitiesByUUID.clear();
        return null;
    }

    private boolean exists(Object key) {
        return key instanceof Integer
                ? entitiesByID.containsKey(key)
                : entitiesByUUID.containsKey(key);
    }

    private TrackedEntity<?> getEntity(Object key) {
        return key instanceof Integer ? entitiesByID.get(key) : entitiesByUUID.get(key);
    }

    private Object insertEntity(Object[] args) {
        TrackedEntity<?> entity = (TrackedEntity<?>) args[1];
        entitiesByID.put(entity.entityID(), entity);
        entitiesByUUID.put(entity.entityUUID(), entity);
        return null;
    }

    private boolean isVisible(Object key) {
        TrackedEntity<?> entity = getEntity(key);
        return entity == null || entity.visible();
    }

    private Object removeEntity(Object key) {
        TrackedEntity<?> removed = key instanceof Integer
                ? entitiesByID.remove(key)
                : entitiesByUUID.remove(key);
        if (removed != null) {
            entitiesByID.remove(removed.entityID(), removed);
            entitiesByUUID.remove(removed.entityUUID(), removed);
        }
        return null;
    }

    private int[] knownEntityIDs() {
        return entitiesByID.keySet().stream().mapToInt(Integer::intValue).toArray();
    }

    private boolean recordDirectVisibility(Object[] args) {
        NettyEntity<?> entity = (NettyEntity<?>) args[0];
        int expectedEpoch = (Integer) args[3];
        boolean current = expectedEpoch == epochSupplier.getAsInt()
                && entitiesByUUID.get(entity.entityUUID()) == entity; // NOPMD - identity proves the same tracked generation.
        if (current) {
            entity.setVisible((Boolean) args[1]);
            entity.setLastChecked((Integer) args[2]);
        }
        return current;
    }
}
