package games.cubi.raycastedantiesp.core.engine;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Submits one tick's worker batches while holding a submission permit so completion cannot race
 * ahead of later scheduler rejection.
 */
final class AsyncTickWork {
    private AsyncTickWork() {
    }

    /**
     * @return the number of tasks whose scheduler submission returned successfully.
     */
    static int dispatch(
            List<? extends Runnable> tasks,
            AsyncRunner runner,
            BooleanSupplier stopRequested,
            Consumer<RuntimeException> submissionFailureHandler,
            Consumer<Throwable> workerFailureHandler,
            Runnable completion
    ) {
        Objects.requireNonNull(tasks, "tasks");
        Objects.requireNonNull(runner, "runner");
        Objects.requireNonNull(stopRequested, "stopRequested");
        Objects.requireNonNull(submissionFailureHandler, "submissionFailureHandler");
        Objects.requireNonNull(workerFailureHandler, "workerFailureHandler");
        Objects.requireNonNull(completion, "completion");

        CompletionGate gate = new CompletionGate(completion);
        int submitted = 0;
        try {
            for (Runnable task : tasks) {
                if (stopRequested.getAsBoolean()) {
                    break;
                }

                WorkerPermit permit = gate.register(task, workerFailureHandler);
                try {
                    runner.runNow(permit);
                    submitted++;
                } catch (RuntimeException exception) {
                    permit.cancel();
                    submissionFailureHandler.accept(exception);
                    break;
                }
            }
            return submitted;
        } finally {
            gate.closeSubmissions();
        }
    }

    private static final class CompletionGate {
        // The initial permit belongs to the submission loop. Workers cannot complete the tick until
        // this permit is released, even when a scheduler executes early submissions inline.
        private final AtomicInteger remaining = new AtomicInteger(1);
        private final AtomicBoolean submissionsClosed = new AtomicBoolean();
        private final AtomicBoolean completed = new AtomicBoolean();
        private final Runnable completion;

        private CompletionGate(Runnable completion) {
            this.completion = completion;
        }

        private WorkerPermit register(Runnable task, Consumer<Throwable> workerFailureHandler) {
            if (submissionsClosed.get()) {
                throw new IllegalStateException("Cannot register async tick work after submissions close");
            }
            remaining.incrementAndGet();
            return new WorkerPermit(this, task, workerFailureHandler);
        }

        private void closeSubmissions() {
            if (submissionsClosed.compareAndSet(false, true)) {
                release();
            }
        }

        private void release() {
            int remainingWork = remaining.decrementAndGet();
            if (remainingWork < 0) {
                throw new IllegalStateException("Async tick completion count went below zero");
            }
            if (remainingWork == 0 && completed.compareAndSet(false, true)) {
                completion.run();
            }
        }
    }

    private static final class WorkerPermit implements Runnable {
        private final CompletionGate gate;
        private final Runnable task;
        private final Consumer<Throwable> workerFailureHandler;
        private final AtomicBoolean released = new AtomicBoolean();

        private WorkerPermit(CompletionGate gate, Runnable task, Consumer<Throwable> workerFailureHandler) {
            this.gate = gate;
            this.task = Objects.requireNonNull(task, "task");
            this.workerFailureHandler = workerFailureHandler;
        }

        @Override
        public void run() {
            try {
                task.run();
            } catch (Throwable throwable) {
                workerFailureHandler.accept(throwable);
            } finally {
                release();
            }
        }

        private void cancel() {
            release();
        }

        private void release() {
            // Some schedulers can execute a task and then throw to the submitter. Both paths may
            // release the same permit, so release must be idempotent.
            if (released.compareAndSet(false, true)) {
                gate.release();
            }
        }
    }
}
