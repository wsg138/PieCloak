package games.cubi.raycastedantiesp.core.view;

import games.cubi.raycastedantiesp.core.tracked.TrackedTileEntity;
import games.cubi.raycastedantiesp.core.utils.Clearable;
import games.cubi.utils.IntrusiveSPSCQueue;

import java.util.Objects;

/**
 * Queues tile-entity visibility transitions without allocating a record for every transition.
 *
 * <p>Exactly one engine thread at a time owns the pending batch and publishes it to the intrusive SPSC queue. The
 * producer role may migrate between engine threads, while the player's Netty thread exclusively drains published
 * entries. Producer and consumer methods must not be called from the opposite role.</p>
 */
public final class PackedBlockTransitionQueue {
    private static final int BATCH_MIN_SIZE = 3;
    private static final int BATCH_SIZE = 8;

    private final IntrusiveSPSCQueue<QueueEntry> publishedEntries = new IntrusiveSPSCQueue<>();
    private final PendingBatch pending = new PendingBatch();

    public void add(BlockViewTransition.Type type, TrackedTileEntity<?> tileEntity,
            long modeToken, int worldEpoch) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(tileEntity, "tileEntity");

        if (pending.count != 0
                && (pending.modeToken != modeToken || pending.worldEpoch != worldEpoch)) {
            flushPending();
        }
        if (pending.count == 0) {
            pending.modeToken = modeToken;
            pending.worldEpoch = worldEpoch;
        }
        pending.add(type, tileEntity);
        if (pending.count == BATCH_SIZE) {
            flushPending();
        }
    }

    /** Publishes the producer-owned partial batch. Only the engine producer may call this method. */
    public void flushPendingTransitions() {
        flushPending();
    }

    public boolean hasPendingTransitions() {
        return !publishedEntries.isEmpty();
    }

    /**
     * Drains every published transition in FIFO order. Runtime failures from individual callbacks are isolated so
     * unrelated transitions in the same packed entry and later entries still run. After the queue is fully drained,
     * the first callback failure is rethrown with later failures suppressed.
     */
    public void drainTransitions(BlockView.TransitionConsumer consumer) {
        Objects.requireNonNull(consumer, "consumer");
        RuntimeException firstFailure = null;
        QueueEntry entry;
        while ((entry = publishedEntries.poll()) != null) {
            firstFailure = entry.drain(consumer, firstFailure);
        }
        if (firstFailure != null) {
            throw firstFailure;
        }
    }

    /**
     * Discards transitions already published to the consumer. The producer-owned pending batch is intentionally left
     * untouched: a structural clear can race an engine flush, and that in-flight batch must retain its old epoch and
     * mode token so the consumer can reject it normally after it is published.
     */
    public void clearPublishedTransitions() {
        publishedEntries.clear();
    }

    private void flushPending() {
        switch (pending.count) {
            case 0 -> {
                return;
            }
            case 1 -> publishedEntries.offer(
                    new SingleEntry(pending.t1, pending.e1, pending.modeToken, pending.worldEpoch));
            case 2 -> {
                publishedEntries.offer(
                        new SingleEntry(pending.t1, pending.e1, pending.modeToken, pending.worldEpoch));
                publishedEntries.offer(
                        new SingleEntry(pending.t2, pending.e2, pending.modeToken, pending.worldEpoch));
            }
            case BATCH_MIN_SIZE, 4, 5, 6, 7, BATCH_SIZE ->
                    publishedEntries.offer(new BatchEntry(pending));
            default -> throw new IllegalStateException("Tile transition batch exceeded eight entries");
        }
        pending.clear();
    }

    private static RuntimeException accept(BlockView.TransitionConsumer consumer,
            BlockViewTransition.Type type, TrackedTileEntity<?> tileEntity,
            long modeToken, int worldEpoch, RuntimeException firstFailure) {
        try {
            consumer.accept(type, tileEntity, modeToken, worldEpoch);
        } catch (RuntimeException exception) {
            if (firstFailure == null) {
                return exception;
            }
            if (firstFailure != exception) {
                firstFailure.addSuppressed(exception);
            }
        }
        return firstFailure;
    }

    private abstract static class QueueEntry extends IntrusiveSPSCQueue.Node {
        abstract RuntimeException drain(BlockView.TransitionConsumer consumer,
                                        RuntimeException firstFailure);
    }

    private static final class SingleEntry extends QueueEntry {
        private final BlockViewTransition.Type type;
        private final TrackedTileEntity<?> tileEntity;
        private final long modeToken;
        private final int worldEpoch;

        private SingleEntry(BlockViewTransition.Type type, TrackedTileEntity<?> tileEntity,
                long modeToken, int worldEpoch) {
            this.type = type;
            this.tileEntity = tileEntity;
            this.modeToken = modeToken;
            this.worldEpoch = worldEpoch;
        }

        @Override
        RuntimeException drain(BlockView.TransitionConsumer consumer,
                RuntimeException firstFailure) {
            return accept(consumer, type, tileEntity, modeToken, worldEpoch, firstFailure);
        }
    }

    private static final class PendingBatch implements Clearable {
        private BlockViewTransition.Type t1;
        private BlockViewTransition.Type t2;
        private BlockViewTransition.Type t3;
        private BlockViewTransition.Type t4;
        private BlockViewTransition.Type t5;
        private BlockViewTransition.Type t6;
        private BlockViewTransition.Type t7;
        private BlockViewTransition.Type t8;
        private TrackedTileEntity<?> e1;
        private TrackedTileEntity<?> e2;
        private TrackedTileEntity<?> e3;
        private TrackedTileEntity<?> e4;
        private TrackedTileEntity<?> e5;
        private TrackedTileEntity<?> e6;
        private TrackedTileEntity<?> e7;
        private TrackedTileEntity<?> e8;
        private long modeToken;
        private int worldEpoch;
        private int count;

        private void add(BlockViewTransition.Type type, TrackedTileEntity<?> tileEntity) {
            switch (++count) {
                case 1 -> {
                    t1 = type;
                    e1 = tileEntity;
                }
                case 2 -> {
                    t2 = type;
                    e2 = tileEntity;
                }
                case 3 -> {
                    t3 = type;
                    e3 = tileEntity;
                }
                case 4 -> {
                    t4 = type;
                    e4 = tileEntity;
                }
                case 5 -> {
                    t5 = type;
                    e5 = tileEntity;
                }
                case 6 -> {
                    t6 = type;
                    e6 = tileEntity;
                }
                case 7 -> {
                    t7 = type;
                    e7 = tileEntity;
                }
                case 8 -> {
                    t8 = type;
                    e8 = tileEntity;
                }
                default -> throw new IllegalStateException("Tile transition batch exceeded eight entries");
            }
        }

        @Override
        public void clear() {
            t1 = null;
            t2 = null;
            t3 = null;
            t4 = null;
            t5 = null;
            t6 = null;
            t7 = null;
            t8 = null;
            e1 = null;
            e2 = null;
            e3 = null;
            e4 = null;
            e5 = null;
            e6 = null;
            e7 = null;
            e8 = null;
            modeToken = 0L;
            worldEpoch = 0;
            count = 0;
        }
    }

    private static final class BatchEntry extends QueueEntry {
        private final BlockViewTransition.Type t1;
        private final BlockViewTransition.Type t2;
        private final BlockViewTransition.Type t3;
        private final BlockViewTransition.Type t4;
        private final BlockViewTransition.Type t5;
        private final BlockViewTransition.Type t6;
        private final BlockViewTransition.Type t7;
        private final BlockViewTransition.Type t8;
        private final TrackedTileEntity<?> e1;
        private final TrackedTileEntity<?> e2;
        private final TrackedTileEntity<?> e3;
        private final TrackedTileEntity<?> e4;
        private final TrackedTileEntity<?> e5;
        private final TrackedTileEntity<?> e6;
        private final TrackedTileEntity<?> e7;
        private final TrackedTileEntity<?> e8;
        private final long modeToken;
        private final int worldEpoch;
        private final int count;

        private BatchEntry(PendingBatch pending) {
            t1 = pending.t1;
            t2 = pending.t2;
            t3 = pending.t3;
            t4 = pending.t4;
            t5 = pending.t5;
            t6 = pending.t6;
            t7 = pending.t7;
            t8 = pending.t8;
            e1 = pending.e1;
            e2 = pending.e2;
            e3 = pending.e3;
            e4 = pending.e4;
            e5 = pending.e5;
            e6 = pending.e6;
            e7 = pending.e7;
            e8 = pending.e8;
            modeToken = pending.modeToken;
            worldEpoch = pending.worldEpoch;
            count = pending.count;
        }

        @Override
        RuntimeException drain(BlockView.TransitionConsumer consumer,
                RuntimeException firstFailure) {
            RuntimeException failure =
                    accept(consumer, t1, e1, modeToken, worldEpoch, firstFailure);
            if (count >= 2) {
                failure = accept(consumer, t2, e2, modeToken, worldEpoch, failure);
            }
            if (count >= 3) {
                failure = accept(consumer, t3, e3, modeToken, worldEpoch, failure);
            }
            if (count >= 4) {
                failure = accept(consumer, t4, e4, modeToken, worldEpoch, failure);
            }
            if (count >= 5) {
                failure = accept(consumer, t5, e5, modeToken, worldEpoch, failure);
            }
            if (count >= 6) {
                failure = accept(consumer, t6, e6, modeToken, worldEpoch, failure);
            }
            if (count >= 7) {
                failure = accept(consumer, t7, e7, modeToken, worldEpoch, failure);
            }
            if (count >= 8) {
                failure = accept(consumer, t8, e8, modeToken, worldEpoch, failure);
            }
            return failure;
        }
    }
}
