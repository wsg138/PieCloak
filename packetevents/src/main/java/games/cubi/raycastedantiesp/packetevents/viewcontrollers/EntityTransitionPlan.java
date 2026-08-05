package games.cubi.raycastedantiesp.packetevents.viewcontrollers;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class EntityTransitionPlan<P> {
    enum Stage {
        SPAWN,
        POSITION,
        HEAD_LOOK,
        REPLAY,
        PASSENGERS,
        VEHICLE,
        LEASH,
        DESTROY
    }

    enum ClientVisibilityCommit {
        NONE,
        VISIBLE,
        HIDDEN
    }

    record Step<P>(Stage stage, P packet, ClientVisibilityCommit commit) {
        Step {
            Objects.requireNonNull(stage, "stage");
            Objects.requireNonNull(packet, "packet");
            Objects.requireNonNull(commit, "commit");
        }
    }

    private static final EntityTransitionPlan<?> EMPTY = new EntityTransitionPlan<>(List.of());

    private final List<Step<P>> steps;

    private EntityTransitionPlan(List<Step<P>> steps) {
        this.steps = List.copyOf(steps);
    }

    static <P> EntityTransitionPlan<P> show(
            P spawn,
            P position,
            P headLook,
            List<P> replayPackets,
            P passengerPacket,
            P vehiclePacket,
            List<P> leashPackets
    ) {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(headLook, "headLook");
        Objects.requireNonNull(replayPackets, "replayPackets");
        Objects.requireNonNull(leashPackets, "leashPackets");

        List<Step<P>> steps = new ArrayList<>(3 + replayPackets.size() + leashPackets.size());
        if (spawn != null) {
            steps.add(new Step<>(Stage.SPAWN, spawn, ClientVisibilityCommit.VISIBLE));
        }
        steps.add(new Step<>(Stage.POSITION, position, ClientVisibilityCommit.NONE));
        steps.add(new Step<>(Stage.HEAD_LOOK, headLook, ClientVisibilityCommit.NONE));
        for (P packet : replayPackets) {
            steps.add(new Step<>(Stage.REPLAY, packet, ClientVisibilityCommit.NONE));
        }
        if (passengerPacket != null) {
            steps.add(new Step<>(Stage.PASSENGERS, passengerPacket, ClientVisibilityCommit.NONE));
        }
        if (vehiclePacket != null) {
            steps.add(new Step<>(Stage.VEHICLE, vehiclePacket, ClientVisibilityCommit.NONE));
        }
        for (P packet : leashPackets) {
            steps.add(new Step<>(Stage.LEASH, packet, ClientVisibilityCommit.NONE));
        }
        return new EntityTransitionPlan<>(steps);
    }

    static <P> EntityTransitionPlan<P> hide(P destroyPacket) {
        return new EntityTransitionPlan<>(List.of(
                new Step<>(Stage.DESTROY, destroyPacket, ClientVisibilityCommit.HIDDEN)
        ));
    }

    @SuppressWarnings("unchecked")
    static <P> EntityTransitionPlan<P> empty() {
        return (EntityTransitionPlan<P>) EMPTY;
    }

    List<Step<P>> steps() {
        return steps;
    }

    boolean isEmpty() {
        return steps.isEmpty();
    }
}
