package games.cubi.raycastedantiesp.packetevents.view;

import games.cubi.raycastedantiesp.core.view.EntityViewTransition;
import games.cubi.raycastedantiesp.packetevents.locatables.PacketEventsEntity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketEventsEntityViewTest {
    @Test
    void mountedTargetAndMinecartUseOneVisibilityTransition() {
        PacketEventsEntityView view = PacketEventsEntityView.createEntityView();
        PacketEventsEntity minecart = entity(1, false);
        PacketEventsEntity villager = entity(2, true);
        minecart.setPassengerIDs(new int[]{villager.entityID()});
        villager.setVehicleID(minecart.entityID());
        view.insertEntity(minecart);
        view.insertEntity(villager);

        view.setVisibility(villager.entityUUID(), false, 10);

        assertFalse(minecart.visible());
        assertFalse(villager.visible());
        List<EntityViewTransition> transitions = view.drainTransitions();
        assertEquals(1, transitions.size());
        assertEquals(EntityViewTransition.Type.HIDE, transitions.getFirst().type());
        assertEquals(minecart.entityUUID(), transitions.getFirst().targetUUID());
    }

    @Test
    void managedPassengerOnUntrackedVehicleFailsOpen() {
        PacketEventsEntityView view = PacketEventsEntityView.createEntityView();
        PacketEventsEntity villager = entity(2, true);
        villager.setVehicleID(999);
        view.insertEntity(villager);

        view.setVisibility(villager.entityUUID(), false, 10);

        assertTrue(villager.visible());
        assertTrue(view.drainTransitions().isEmpty());
    }

    @Test
    void failedTransitionCanBeRequeuedWithoutDroppingOtherTransitions() {
        PacketEventsEntityView view = PacketEventsEntityView.createEntityView();
        PacketEventsEntity first = entity(1, true);
        PacketEventsEntity second = entity(2, true);
        view.insertEntity(first);
        view.insertEntity(second);
        view.setVisibility(first.entityUUID(), false, 10);
        view.setVisibility(second.entityUUID(), false, 10);

        List<EntityViewTransition> drained = view.drainTransitions();
        view.requeueTransition(drained.getFirst());

        assertEquals(1, view.drainTransitions().size());
    }

    private PacketEventsEntity entity(int entityID, boolean cullTarget) {
        PacketEventsEntity entity = new PacketEventsEntity(
                null,
                UUID.randomUUID(),
                0,
                64,
                0,
                entityID,
                UUID.randomUUID(),
                false,
                null,
                true
        );
        entity.setCullTarget(cullTarget);
        entity.setClientVisible(true);
        return entity;
    }
}
