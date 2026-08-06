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

    private final UUID viewerId;
    private final EntityView<PacketEventsEntity> entityView;
    private final EntityViewTransition.Type transitionType;
    private final PacketEventsEntity targetEntity;
    private final int expectedWorldEpoch;
    private final EntityTransitionPlan<P> plan;

    private int nextStep;
    private int failureCount;
    private int nextEligibleTick;
    private Boolean pendingVisibility;

    EntityTransitionWork(
            UUID viewerUUID,
            EntityView<PacketEventsEntity> view,
            EntityViewTransition.Type type,
            PacketEventsEntity entity,
            int worldEpoch,
            EntityTransitionPlan<P> plan
    ) {
        this.viewerId = Objects.requireNonNull(viewerUUID, "viewerUUID");
        this.entityView = Objects.requireNonNull(view, "view");
        this.transitionType = Objects.requireNonNull(type, "type");
        this.targetEntity = Objects.requireNonNull(entity, "entity");
        this.expectedWorldEpoch = worldEpoch;
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
            pendingVisibility = switch (step.commit()) {
                case VISIBLE -> Boolean.TRUE;
                case HIDDEN -> Boolean.FALSE;
                case NONE -> pendingVisibility;
            };
            applyConfirmedVisibility(visibilitySink);
        }
    }

    @SuppressWarnings("PMD.NullAssignment") // Null marks that the packet-confirmed visibility checkpoint has been consumed.
    private void applyConfirmedVisibility(ClientVisibilitySink visibilitySink) {
        if (pendingVisibility == null) {
            return;
        }
        visibilitySink.setClientVisible(pendingVisibility);
        pendingVisibility = null;
    }

    boolean recordFailure(int currentTick) {
        failureCount++;
        if (failureCount >= MAX_FAILURES) {
            return false;
        }
        int shift = Math.min(failureCount - 1, 4);
        int delay = Math.min(1 << shift, MAX_BACKOFF_TICKS);
        nextEligibleTick = currentTick + delay;
        return true;
    }

    boolean isDue(int currentTick) {
        return currentTick - nextEligibleTick >= 0;
    }

    boolean complete() {
        return nextStep >= plan.steps().size() && pendingVisibility == null;
    }

    EntityTransitionPlan.Stage nextStage() {
        if (nextStep >= plan.steps().size()) {
            return null;
        }
        return plan.steps().get(nextStep).stage();
    }

    UUID viewerUUID() {
        return viewerId;
    }

    EntityView<PacketEventsEntity> view() {
        return entityView;
    }

    EntityViewTransition.Type type() {
        return transitionType;
    }

    PacketEventsEntity entity() {
        return targetEntity;
    }

    int worldEpoch() {
        return expectedWorldEpoch;
    }

    int failures() {
        return failureCount;
    }

    /**
     * Returns packet-confirmed client presence that has not yet been committed to local bookkeeping.
     * A newer opposite transition must inherit this state before deciding whether to spawn or destroy.
     */
    Boolean confirmedClientVisibility() {
        return pendingVisibility;
    }
}
