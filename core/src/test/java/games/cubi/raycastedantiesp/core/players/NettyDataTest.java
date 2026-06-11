package games.cubi.raycastedantiesp.core.players;

import org.junit.jupiter.api.Test;

import static games.cubi.raycastedantiesp.core.locatables.NettyEntityLocatable.NO_VEHICLE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NettyDataTest {
    @Test
    void replacingPassengerListClearsRemovedReverseLinks() {
        NettyData data = new NettyData();
        data.setUnresolvedPassengers(10, new int[]{20, 21});

        data.setUnresolvedPassengers(10, new int[]{21, 22});

        assertEquals(NO_VEHICLE, data.getUnresolvedVehicleForPassenger(20));
        assertEquals(10, data.getUnresolvedVehicleForPassenger(21));
        assertEquals(10, data.getUnresolvedVehicleForPassenger(22));
        assertArrayEquals(new int[]{21, 22}, data.getUnresolvedPassengers(10));
    }

    @Test
    void emptyPassengerListClearsDismountedState() {
        NettyData data = new NettyData();
        data.setUnresolvedPassengers(10, new int[]{20});

        data.setUnresolvedPassengers(10, new int[0]);

        assertNull(data.getUnresolvedPassengers(10));
        assertEquals(NO_VEHICLE, data.getUnresolvedVehicleForPassenger(20));
    }

    @Test
    void passengerReassignmentRemovesOldVehicleEntry() {
        NettyData data = new NettyData();
        data.setUnresolvedPassengers(10, new int[]{20});

        data.setUnresolvedPassengers(11, new int[]{20});

        assertNull(data.getUnresolvedPassengers(10));
        assertArrayEquals(new int[]{20}, data.getUnresolvedPassengers(11));
        assertEquals(11, data.getUnresolvedVehicleForPassenger(20));
    }
}
