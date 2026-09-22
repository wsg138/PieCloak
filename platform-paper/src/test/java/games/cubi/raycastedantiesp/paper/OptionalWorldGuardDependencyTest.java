package games.cubi.raycastedantiesp.paper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OptionalWorldGuardDependencyTest {
    @Test
    void mainPluginClassLoadsWhenWorldGuardIsAbsent() throws Exception {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.sk89q.worldguard.WorldGuard", false, loader));
        assertNotNull(Class.forName(
                "games.cubi.raycastedantiesp.paper.RaycastedAntiESP", false, loader));
    }
}
