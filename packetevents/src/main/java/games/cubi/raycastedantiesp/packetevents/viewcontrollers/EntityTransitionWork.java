package games.cubi.raycastedantiesp.packetevents.viewcontrollers;

import games.cubi.raycastedantiesp.core.view.EntityView;
import games.cubi.raycastedantiesp.core.view.EntityViewTransition;
import games.cubi.raycastedantiesp.packetevents.tracked.PacketEventsEntity;

import java.util.Objects;
import java.util.UUID;

final class EntityTransitionWork<P> {
    static final int MAX_FAILURES = 8;
    private static final int MAX_BACKOFF_TICKS = 20;

    @FunctionalInterface
    interface PacketWriter<P> {
        void write(P packet);
    }

    @FunctionalInterface
    interface ClientVisibilitySink {
        void setClientVisible(boolean visible);
    }

    private final UUID viewerUUID;
    private final EntityView<PacketEventsEntity> view;
    private final EntityViewTransition.Type type;
    private final PacketEventsEntity entity;
    private final int worldEpoch;
    private final EntityTransitionPlan<P> plan;

    private int nextStep;
    private int failures;
    private int nextEligibleTick;
    private Boolean confirmedClientVisibility;

    EntityTransitionWork(
            UUID viewerUUID,
            EntityView<PacketEventsEntity> view,
            EntityViewTransition.Type type,
            PacketEventsEntity entity,
            int worldEpoch,
            EntityTransitionPlan<P> plan
    ) {
        this.viewerUUID = Objects.requireNonNull(viewerUUID, "viewerUUID");
        this.view = Objects.requireNonNull(view, "view");
        this.type = Objects.requireNonNull(type, "type");
        this.entity = Objects.requireNonNull(entity, "entity");
        this.worldEpoch = worldEpoch;
        this.plan = Objects.requireNonNull(plan, "plan");
    }

    void execute(PacketWriter<P> writer, ClientVisibilitySink visibilitySink) {
        applyConfirmedVisibility(visibilitySink);
        while (nextStep < plan.steps().size()) {
            EntityTransitionPlan.Step<P> step = plan.steps().get(nextStep);
            writer.write(step.packet());

            // The packet write is the external commit point. Advance the packet checkpoint before
            // touching local bookkeeping so a bookkeeping failure cannot repeat spawn or destroy.
            nextStep++;
            confirmedClientVisibility = switch (step.commit()) {
                case VISIBLE -> Boolean.TRUE;
                case HIDDEN -> Boolean.FALSE;
                case NONE -> confirmedClientVisibility;
            };
            applyConfirmedVisibility(visibilitySink);
        }
    }

    private void applyConfirmedVisibility(ClientVisibilitySink visibilitySink) {
        if (confirmedClientVisibility == null) {
            return;
        }
        visibilitySink.setClientVisible(confirmedClientVisibility);
        confirmedClientVisibility = null;
    }

    boolean recordFailure(int currentTick) {
        failures++;
        if (failures >= MAX_FAILURES) {
            return false;
        }
        int shift = Math.min(failures - 1, 4);
        int delay = Math.min(1 << shift, MAX_BACKOFF_TICKS);
        nextEligibleTick = currentTick + delay;
        return true;
    }

    boolean isDue(int currentTick) {
        return currentTick - nextEligibleTick >= 0;
    }

    boolean complete() {
        return nextStep >= plan.steps().size() && confirmedClientVisibility == null;
    }

    EntityTransitionPlan.Stage nextStage() {
        if (nextStep >= plan.steps().size()) {
            return null;
        }
        return plan.steps().get(nextStep).stage();
    }

    UUID viewerUUID() {
        return viewerUUID;
    }

    EntityView<PacketEventsEntity> view() {
        return view;
    }

    EntityViewTransition.Type type() {
        return type;
    }

    PacketEventsEntity entity() {
        return entity;
    }

    int worldEpoch() {
        return worldEpoch;
    }

    int failures() {
        return failures;
    }

    /**
     * Returns packet-confirmed client presence that has not yet been committed to local bookkeeping.
     * A newer opposite transition must inherit this state before deciding whether to spawn or destroy.
     */
    Boolean confirmedClientVisibility() {
        return confirmedClientVisibility;
    }
}
