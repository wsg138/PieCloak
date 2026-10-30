package games.cubi.raycastedantiesp.paper;

import games.cubi.raycastedantiesp.core.entity.EntityBypassRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.ref.Reference;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WeakIdentityRetirementSweepTest {
    @AfterEach
    void reset() {
        EntityBypassRegistry.reset();
    }

    @Test
    void sweepReleasesStaleOwnerDuringIdleWithoutAnotherRegistration() throws Exception {
        var scope = EntityBypassRegistry.openFancyNpcScope();
        var guard = new WeakIdentityRetirementGuard<Object>(scope);
        Object orphan = new Object();
        assertTrue(guard.registerIfActive(orphan, 8181));

        var ownersField = WeakIdentityRetirementGuard.class.getDeclaredField("owners");
        ownersField.setAccessible(true);
        Map<?, ?> owners = (Map<?, ?>) ownersField.get(guard);
        Reference<?> key = (Reference<?>) owners.keySet().iterator().next();
        key.clear();
        assertTrue(key.enqueue());
        assertTrue(EntityBypassRegistry.isBypassed(8181));

        guard.sweepCollected();
        assertFalse(EntityBypassRegistry.isBypassed(8181),
                "periodic sweep must not require another Fancy callback");
    }

    @Test
    void sweepReleasesOnlyCollectedOwnerWhilePreservingSharedNumericId() throws Exception {
        var scope = EntityBypassRegistry.openFancyHologramScope();
        var guard = new WeakIdentityRetirementGuard<Object>(scope);
        Object orphan = new Object();
        Object replacement = new Object();
        assertTrue(guard.registerIfActive(orphan, 8182));
        assertTrue(guard.registerIfActive(replacement, 8182));
        var ownersField = WeakIdentityRetirementGuard.class.getDeclaredField("owners");
        ownersField.setAccessible(true);
        Map<?, ?> owners = (Map<?, ?>) ownersField.get(guard);
        Reference<?> key = owners.keySet().stream()
                .map(it -> (Reference<?>) it)
                .filter(it -> it.get() == orphan).findFirst().orElseThrow();
        key.clear();
        assertTrue(key.enqueue());

        guard.sweepCollected();
        assertTrue(EntityBypassRegistry.isBypassed(8182),
                "replacement owner must retain its exemption");
        guard.retire(replacement, 8182);
        assertFalse(EntityBypassRegistry.isBypassed(8182));
        guard.sweepCollected(); // idempotent after the last owner retires
        assertFalse(EntityBypassRegistry.isBypassed(8182));
    }

    @Test
    void sweepAfterScopeCloseDoesNotReactivateAnyBypass() throws Exception {
        var scope = EntityBypassRegistry.openFancyNpcScope();
        var guard = new WeakIdentityRetirementGuard<Object>(scope);
        Object owner = new Object();
        assertTrue(guard.registerIfActive(owner, 8183));
        scope.close();
        guard.sweepCollected();
        assertFalse(EntityBypassRegistry.isBypassed(8183));
    }
}