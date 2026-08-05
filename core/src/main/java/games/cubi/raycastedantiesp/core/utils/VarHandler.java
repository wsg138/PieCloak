package games.cubi.raycastedantiesp.core.utils;

import ca.spottedleaf.concurrentutil.util.ConcurrentUtil;

import java.lang.invoke.VarHandle;

/** Just a shorter alias of useful methods from ConcurrentUtil**/
public final class VarHandler {
    public static final VarHandle BYTE_ARRAY_HANDLE = ConcurrentUtil.getArrayHandle(byte[].class);
    public static final VarHandle CHAR_ARRAY_HANDLE = ConcurrentUtil.getArrayHandle(char[].class);
    public static final VarHandle LONG_ARRAY_HANDLE = ConcurrentUtil.getArrayHandle(long[].class);

    private VarHandler() {}

    public static VarHandle get(Class<?> classOf, String fieldName, Class<?> fieldClass) {
        return ConcurrentUtil.getVarHandle(classOf, fieldName, fieldClass);
    }

    public static VarHandle $tatic(Class<?> classOf, String fieldName, Class<?> fieldClass) {
        return ConcurrentUtil.getStaticVarHandle(classOf, fieldName, fieldClass);
    }
}
