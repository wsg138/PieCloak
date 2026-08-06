package games.cubi.raycastedantiesp.core.testsupport;

import java.util.Map;
import java.util.Objects;

public final class TestProxySupport {
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

    private TestProxySupport() {
    }

    public static ClassLoader contextClassLoader() {
        return Objects.requireNonNull(
                Thread.currentThread().getContextClassLoader(),
                "Tests require a context class loader"
        );
    }

    public static Object defaultValue(Class<?> returnType) {
        return returnType.isPrimitive() ? PRIMITIVE_DEFAULTS.get(returnType) : null;
    }
}
