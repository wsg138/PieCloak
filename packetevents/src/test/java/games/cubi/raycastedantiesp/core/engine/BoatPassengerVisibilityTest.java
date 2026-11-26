package games.cubi.raycastedantiesp.core.engine;

import games.cubi.raycastedantiesp.core.config.raycast.EntityConfig;
import games.cubi.raycastedantiesp.core.entity.EntityBypassRegistry;
import games.cubi.raycastedantiesp.core.players.PlayerData;
import games.cubi.raycastedantiesp.core.players.PlayerRegistry;
import games.cubi.raycastedantiesp.core.view.TransitionType;
import games.cubi.raycastedantiesp.core.view.ViewRegistry;
import games.cubi.raycastedantiesp.packetevents.tracked.PacketEventsEntity;
import games.cubi.raycastedantiesp.packetevents.view.PacketEventsEntityView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests the real core engine and PacketEvents view together without a server or client. */
class BoatPassengerVisibilityTest {
    private final UUID viewerUUID = UUID.randomUUID();

    @BeforeEach
    void initialiseViews() {
        ViewRegistry.reset();
        ViewRegistry.initialise(ignored -> null,
                PacketEventsEntityView::createEntityView, PacketEventsEntityView::createPlayerView);
    }

    @AfterEach
    void releaseState() {
        PlayerRegistry.get().unregisterPlayer(viewerUUID);
        ViewRegistry.reset();
        EntityBypassRegistry.reset();
    }

    @Test
    void movedPassengerAndSupportBoatPublishShowThenHideTransitions() throws Exception {
        UUID world = UUID.randomUUID();
        PlayerData player = PlayerRegistry.get().registerAndGetPlayer(
                viewerUUID, 0, 1, PacketEventsEntity::createSelfEntity);
        player.beginWorldTransition();
        player.completeWorldTransition(world);
        player.updateOwnLocation(world, 0, 64, 0);
        PacketEventsEntityView view = (PacketEventsEntityView) player.entityView();
        PacketEventsEntity boat = new PacketEventsEntity(player, 100, 64, 0,
                20, UUID.randomUUID(), false, 0, false);
        PacketEventsEntity passenger = new PacketEventsEntity(player, 100, 64, 0,
                2, UUID.randomUUID(), false, 0, false);
        view.insertEntity(world, boat);
        view.insertEntity(world, passenger);
        EntityBypassRegistry.addRelationshipSupportEntity(boat.entityID());
        // Controller relationship establishment is covered by PacketEntityViewControllerTest.
        boat.setPassengerIDs(new int[]{passenger.entityID()});
        passenger.setVehicleEntity(boat);
        EntityConfig config = entityConfig();
        AsyncVisibilityChecks checks = new AsyncVisibilityChecks(null);
        int epoch = player.acquireWorldEpoch();

        checks.processEntitySection(player, player.ownLocation(), null, config,
                false, 17, epoch, TickTimingBatchNoOp.INSTANCE);
        assertFalse(passenger.visible());
        assertFalse(boat.visible());
        assertEquals(Map.of(), drainTransitions(view, epoch));

        boat.add(-90, 0, 0);
        checks.processEntitySection(player, player.ownLocation(), null, config,
                false, 28, epoch, TickTimingBatchNoOp.INSTANCE);
        assertEquals(10, passenger.x());
        assertTrue(passenger.visible());
        assertTrue(boat.visible());
        assertEquals(Map.of(2, TransitionType.Entity.SHOW, 20, TransitionType.Entity.SHOW),
                drainTransitions(view, epoch));

        boat.setPosition(120, 70, 5);
        checks.processEntitySection(player, player.ownLocation(), null, config,
                false, 39, epoch, TickTimingBatchNoOp.INSTANCE);
        assertFalse(passenger.visible());
        assertFalse(boat.visible());
        assertEquals(Map.of(2, TransitionType.Entity.HIDE, 20, TransitionType.Entity.HIDE),
                drainTransitions(view, epoch));
    }

    private static Map<Integer, TransitionType.Entity> drainTransitions(
            PacketEventsEntityView view, int epoch) {
        Map<Integer, TransitionType.Entity> transitions = new ConcurrentHashMap<>();
        view.flushPendingTransitions();
        view.drainTransitions((type, entity, actualEpoch) -> {
            assertEquals(epoch, actualEpoch);
            assertFalse(transitions.containsKey(entity.entityID()), "duplicate visibility transition");
            transitions.put(entity.entityID(), type);
        });
        return transitions;
    }

    private static EntityConfig entityConfig() throws Exception {
        return new EntityConfig(new games.cubi.raycastedantiesp.core.config.raycast.RaycastSettings(true, (byte) 3, (short) 24, (short) 48, (short) 0, (short) 10), false, false, java.util.Set.of());
    }
}
