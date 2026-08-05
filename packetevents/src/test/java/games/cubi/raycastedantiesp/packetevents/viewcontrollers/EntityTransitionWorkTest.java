package games.cubi.raycastedantiesp.packetevents.viewcontrollers;

import games.cubi.raycastedantiesp.core.view.EntityView;
import games.cubi.raycastedantiesp.core.view.EntityViewTransition;
import games.cubi.raycastedantiesp.packetevents.tracked.PacketEventsEntity;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityTransitionWorkTest {
    private static final List<String> SHOW_PACKETS = List.of(
            "spawn",
            "position",
            "head",
            "metadata",
            "equipment",
            "velocity",
            "effect",
            "attributes",
            "passengers",
            "vehicle",
            "leash"
    );

    @Test
    void showPlanPreservesCorrectionReplayAndRelationshipOrdering() {
        EntityTransitionPlan<String> plan = showPlan();

        assertEquals(SHOW_PACKETS, plan.steps().stream().map(EntityTransitionPlan.Step::packet).toList());
        assertEquals(List.of(
                EntityTransitionPlan.Stage.SPAWN,
                EntityTransitionPlan.Stage.POSITION,
                EntityTransitionPlan.Stage.HEAD_LOOK,
                EntityTransitionPlan.Stage.REPLAY,
                EntityTransitionPlan.Stage.REPLAY,
                EntityTransitionPlan.Stage.REPLAY,
                EntityTransitionPlan.Stage.REPLAY,
                EntityTransitionPlan.Stage.REPLAY,
                EntityTransitionPlan.Stage.PASSENGERS,
                EntityTransitionPlan.Stage.VEHICLE,
                EntityTransitionPlan.Stage.LEASH
        ), plan.steps().stream().map(EntityTransitionPlan.Step::stage).toList());
    }

    @Test
    void everyPostSpawnFailureResumesWithoutASecondSpawn() {
        for (int failureIndex = 1; failureIndex < SHOW_PACKETS.size(); failureIndex++) {
            EntityTransitionWork<String> work = work(showPlan());
            RecordingWriter writer = new RecordingWriter(SHOW_PACKETS.get(failureIndex), 1);
            MutableVisibility visibility = new MutableVisibility(false);

            assertThrows(TestFailure.class, () -> work.execute(writer::write, visibility::set));
            assertTrue(visibility.value);
            assertTrue(work.recordFailure(20));
            work.execute(writer::write, visibility::set);

            assertTrue(work.complete());
            assertEquals(1, writer.attempts("spawn"), "failure index " + failureIndex);
            assertEquals(2, writer.attempts(SHOW_PACKETS.get(failureIndex)), "failure index " + failureIndex);
            for (int prior = 0; prior < failureIndex; prior++) {
                assertEquals(1, writer.attempts(SHOW_PACKETS.get(prior)), "failure index " + failureIndex);
            }
        }
    }

    @Test
    void failedSpawnIsRetriedButNotCommittedEarly() {
        EntityTransitionWork<String> work = work(showPlan());
        RecordingWriter writer = new RecordingWriter("spawn", 1);
        MutableVisibility visibility = new MutableVisibility(false);

        assertThrows(TestFailure.class, () -> work.execute(writer::write, visibility::set));
        assertFalse(visibility.value);
        assertTrue(work.recordFailure(0));

        work.execute(writer::write, visibility::set);

        assertTrue(visibility.value);
        assertEquals(2, writer.attempts("spawn"));
        assertTrue(work.complete());
    }

    @Test
    void bookkeepingFailureAfterSpawnDoesNotRepeatSpawn() {
        EntityTransitionWork<String> work = work(showPlan());
        RecordingWriter writer = new RecordingWriter(null, 0);
        FailingVisibility visibility = new FailingVisibility();

        assertThrows(TestFailure.class, () -> work.execute(writer::write, visibility::set));
        assertEquals(Boolean.TRUE, work.confirmedClientVisibility());
        assertTrue(work.recordFailure(0));

        work.execute(writer::write, visibility::set);

        assertEquals(1, writer.attempts("spawn"));
        assertTrue(visibility.value);
        assertTrue(work.complete());
    }

    @Test
    void destroyCheckpointIsNotRepeatedAfterBookkeepingFailure() {
        EntityTransitionWork<String> work = work(EntityTransitionPlan.hide("destroy"));
        RecordingWriter writer = new RecordingWriter(null, 0);
        FailingVisibility visibility = new FailingVisibility();
        visibility.value = true;

        assertThrows(TestFailure.class, () -> work.execute(writer::write, visibility::set));
        assertEquals(Boolean.FALSE, work.confirmedClientVisibility());
        assertTrue(work.recordFailure(0));

        work.execute(writer::write, visibility::set);

        assertEquals(1, writer.attempts("destroy"));
        assertFalse(visibility.value);
        assertTrue(work.complete());
    }

    @Test
    void persistentFailureStopsAtDefinedBound() {
        EntityTransitionWork<String> work = work(showPlan());
        RecordingWriter writer = new RecordingWriter("position", Integer.MAX_VALUE);
        MutableVisibility visibility = new MutableVisibility(false);

        boolean retry;
        do {
            assertThrows(TestFailure.class, () -> work.execute(writer::write, visibility::set));
            retry = work.recordFailure(100);
        } while (retry);

        assertEquals(EntityTransitionWork.MAX_FAILURES, work.failures());
        assertEquals(1, writer.attempts("spawn"));
        assertTrue(visibility.value);
        assertFalse(work.complete());
    }

    private static EntityTransitionPlan<String> showPlan() {
        return EntityTransitionPlan.show(
                "spawn",
                "position",
                "head",
                List.of("metadata", "equipment", "velocity", "effect", "attributes"),
                "passengers",
                "vehicle",
                List.of("leash")
        );
    }

    private static EntityTransitionWork<String> work(EntityTransitionPlan<String> plan) {
        PacketEventsEntity entity = new PacketEventsEntity(
                null, 0, 0, 0, 17, UUID.randomUUID(), false, 0, true
        );
        return new EntityTransitionWork<>(
                UUID.randomUUID(),
                view(),
                plan.steps().size() == 1 && plan.steps().getFirst().stage() == EntityTransitionPlan.Stage.DESTROY
                        ? EntityViewTransition.Type.HIDE
                        : EntityViewTransition.Type.SHOW,
                entity,
                2,
                plan
        );
    }

    @SuppressWarnings("unchecked")
    private static EntityView<PacketEventsEntity> view() {
        return (EntityView<PacketEventsEntity>) Proxy.newProxyInstance(
                EntityTransitionWorkTest.class.getClassLoader(),
                new Class[]{EntityView.class},
                (proxy, method, args) -> method.getName().equals("isPlayerView")
                        ? false
                        : defaultValue(method.getReturnType())
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        if (type == double.class) {
            return 0D;
        }
        if (type == char.class) {
            return (char) 0;
        }
        return null;
    }

    private static final class RecordingWriter {
        private final String failingPacket;
        private int remainingFailures;
        private final Map<String, Integer> counts = new HashMap<>();

        private RecordingWriter(String failingPacket, int remainingFailures) {
            this.failingPacket = failingPacket;
            this.remainingFailures = remainingFailures;
        }

        void write(String packet) {
            counts.merge(packet, 1, Integer::sum);
            if (packet.equals(failingPacket) && remainingFailures > 0) {
                remainingFailures--;
                throw new TestFailure();
            }
        }

        int attempts(String packet) {
            return counts.getOrDefault(packet, 0);
        }
    }

    private static class MutableVisibility {
        boolean value;

        private MutableVisibility(boolean initial) {
            value = initial;
        }

        void set(boolean visible) {
            value = visible;
        }
    }

    private static final class FailingVisibility extends MutableVisibility {
        private boolean fail = true;

        private FailingVisibility() {
            super(false);
        }

        @Override
        void set(boolean visible) {
            if (fail) {
                fail = false;
                throw new TestFailure();
            }
            super.set(visible);
        }
    }

    private static final class TestFailure extends RuntimeException {
    }
}
