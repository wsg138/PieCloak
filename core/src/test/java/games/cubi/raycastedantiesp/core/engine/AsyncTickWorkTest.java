package games.cubi.raycastedantiesp.core.engine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncTickWorkTest {
    private static final int REJECTED_SUBMISSION_INDEX = 1;

    @Test
    void rejectionBeforeFirstSubmissionStillCompletesExactlyOnce() {
        AtomicInteger completions = new AtomicInteger();
        AtomicInteger submissionFailures = new AtomicInteger();

        int submitted = AsyncTickWork.dispatch(
                List.of(() -> { }),
                task -> { throw new IllegalStateException("rejected"); },
                () -> false,
                exception -> submissionFailures.incrementAndGet(),
                throwable -> { },
                completions::incrementAndGet
        );

        assertEquals(0, submitted);
        assertEquals(1, submissionFailures.get());
        assertEquals(1, completions.get());
    }

    @Test
    void rejectionAfterSomeSubmissionsWaitsForAcceptedWorkers() {
        List<Runnable> queued = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger completions = new AtomicInteger();

        int submitted = AsyncTickWork.dispatch(
                List.of(() -> { }, () -> { }, () -> { }),
                task -> {
                    if (calls.getAndIncrement() == REJECTED_SUBMISSION_INDEX) {
                        throw new IllegalStateException("rejected");
                    }
                    queued.add(task);
                },
                () -> false,
                exception -> { },
                throwable -> { },
                completions::incrementAndGet
        );

        assertEquals(1, submitted);
        assertEquals(1, queued.size());
        assertEquals(0, completions.get());

        queued.getFirst().run();
        assertEquals(1, completions.get());
    }

    @Test
    void fastWorkerCannotCompleteBeforeLaterSubmissionFails() {
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger completions = new AtomicInteger();

        AsyncTickWork.dispatch(
                List.of(() -> { }, () -> { }),
                task -> {
                    if (calls.getAndIncrement() == 0) {
                        task.run();
                    } else {
                        assertEquals(0, completions.get());
                        throw new IllegalStateException("later rejection");
                    }
                },
                () -> false,
                exception -> { },
                throwable -> { },
                completions::incrementAndGet
        );

        assertEquals(1, completions.get());
    }

    @Test
    void workerExceptionIsReportedAndPermitIsReleased() {
        AtomicInteger workerFailures = new AtomicInteger();
        AtomicInteger completions = new AtomicInteger();

        AsyncTickWork.dispatch(
                List.of(() -> { throw new IllegalArgumentException("worker failed"); }),
                Runnable::run,
                () -> false,
                exception -> { },
                throwable -> workerFailures.incrementAndGet(),
                completions::incrementAndGet
        );

        assertEquals(1, workerFailures.get());
        assertEquals(1, completions.get());
    }

    @Test
    void shutdownDuringSchedulingStopsLaterSubmissions() {
        AtomicBoolean shutdown = new AtomicBoolean();
        AtomicInteger submissions = new AtomicInteger();
        AtomicInteger completions = new AtomicInteger();

        int submitted = AsyncTickWork.dispatch(
                List.of(() -> { }, () -> { }),
                task -> {
                    submissions.incrementAndGet();
                    task.run();
                    shutdown.set(true);
                },
                shutdown::get,
                exception -> { },
                throwable -> { },
                completions::incrementAndGet
        );

        assertEquals(1, submitted);
        assertEquals(1, submissions.get());
        assertEquals(1, completions.get());
    }

    @Test
    void shutdownWhileWorkersAreQueuedStillDrainsAcceptedWork() {
        List<Runnable> queued = new ArrayList<>();
        AtomicBoolean shutdown = new AtomicBoolean();
        AtomicInteger completions = new AtomicInteger();

        AsyncTickWork.dispatch(
                List.of(() -> { }, () -> { }),
                queued::add,
                shutdown::get,
                exception -> { },
                throwable -> { },
                completions::incrementAndGet
        );
        shutdown.set(true);

        assertEquals(2, queued.size());
        assertEquals(0, completions.get());
        queued.getFirst().run();
        assertEquals(0, completions.get());
        queued.getLast().run();
        assertEquals(1, completions.get());
    }

    @Test
    void schedulerThatRunsThenThrowsCannotDoubleReleaseWorker() {
        AtomicInteger completions = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();

        int submitted = AsyncTickWork.dispatch(
                List.of(() -> { }),
                task -> {
                    task.run();
                    throw new IllegalStateException("reported after execution");
                },
                () -> false,
                exception -> failures.incrementAndGet(),
                throwable -> { },
                completions::incrementAndGet
        );

        assertEquals(0, submitted);
        assertEquals(1, failures.get());
        assertEquals(1, completions.get());
    }

    @Test
    void stopRequestedBeforeFirstSubmissionCompletesWithoutDispatching() {
        AtomicInteger completions = new AtomicInteger();
        AtomicBoolean runnerCalled = new AtomicBoolean();

        int submitted = AsyncTickWork.dispatch(
                List.of(() -> { }),
                task -> runnerCalled.set(true),
                () -> true,
                exception -> { },
                throwable -> { },
                completions::incrementAndGet
        );

        assertEquals(0, submitted);
        assertFalse(runnerCalled.get());
        assertTrue(completions.get() == 1);
    }
}
