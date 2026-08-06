package games.cubi.raycastedantiesp.core.tracked;

import games.cubi.raycastedantiesp.core.utils.Clearable;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NettyEntityTrackedStateTest {
    @Test
    void trackedFlagsUpdateIndependentlyAndReportChanges() {
        TestEntity entity = new TestEntity();

        assertTrue(entity.setSneaking(true));
        assertTrue(entity.sneaking());
        assertFalse(entity.glowing());
        assertEquals(NettyEntity.NEVER_CHECKED, entity.lastChecked());

        entity.setLastChecked(10);
        assertFalse(entity.setSneaking(true));
        assertEquals(10, entity.lastChecked());

        assertTrue(entity.setGlowing(true));
        assertTrue(entity.sneaking());
        assertTrue(entity.glowing());
        assertEquals(NettyEntity.NEVER_CHECKED, entity.lastChecked());

        entity.clear();

        assertFalse(entity.sneaking());
        assertFalse(entity.glowing());
    }

    @Test
    void directVehicleReferenceTracksAndClearsWithVehicleId() {
        TestEntity passenger = new TestEntity(1);
        TestEntity vehicle = new TestEntity(2);

        passenger.setVehicleEntity(vehicle);

        assertSame(vehicle, passenger.vehicleEntity());
        assertEquals(vehicle.entityID(), passenger.vehicleID());

        passenger.setVehicleID(NettyEntity.NO_VEHICLE);

        assertNull(passenger.vehicleEntity());
        assertEquals(NettyEntity.NO_VEHICLE, passenger.vehicleID());
    }

    @Test
    void concurrentUpdatesToDifferentFlagsDoNotOverwriteEachOther() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            TestEntity entity = new TestEntity();
            int iterations = 2_000;
            Phaser phases = new Phaser(3);
            AtomicBoolean lostUpdate = new AtomicBoolean();

            try (var executor = Executors.newFixedThreadPool(2)) {
                var sneakingTask = executor.submit(() -> {
                    for (int i = 0; i < iterations; i++) {
                        phases.arriveAndAwaitAdvance();
                        entity.setSneaking(true);
                        phases.arriveAndAwaitAdvance();
                    }
                });
                var glowingTask = executor.submit(() -> {
                    for (int i = 0; i < iterations; i++) {
                        phases.arriveAndAwaitAdvance();
                        entity.setGlowing(true);
                        phases.arriveAndAwaitAdvance();
                    }
                });

                for (int i = 0; i < iterations; i++) {
                    entity.clear();
                    phases.arriveAndAwaitAdvance();
                    phases.arriveAndAwaitAdvance();
                    if (!entity.sneaking() || !entity.glowing()) {
                        lostUpdate.set(true);
                    }
                }
                sneakingTask.get();
                glowingTask.get();
            }

            assertFalse(lostUpdate.get());
        });
    }

    private static final class TestEntity extends NettyEntity<Clearable> {
        private TestEntity() {
            this(1);
        }

        private TestEntity(int entityID) {
            super(null, 0, 0, 0, entityID, UUID.randomUUID(), false, 0, true);
        }
    }
}
