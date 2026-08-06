package games.cubi.raycastedantiesp.core.lifecycle;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * Owns startup resources and closes them once in reverse registration order. Cleanup continues
 * after individual failures so partially-started lifecycles are fully unwound.
 */
public final class LifecycleScope implements AutoCloseable {
    @FunctionalInterface
    public interface Cleanup {
        void run() throws Exception;
    }

    private final Deque<Cleanup> cleanups = new ArrayDeque<>();
    private boolean closed;

    public <T extends AutoCloseable> T own(T resource) {
        Objects.requireNonNull(resource, "resource");
        synchronized (this) {
            onCloseLocked(resource::close);
        }
        return resource;
    }

    public void onClose(Cleanup cleanup) {
        Objects.requireNonNull(cleanup, "cleanup");
        synchronized (this) {
            onCloseLocked(cleanup);
        }
    }

    private void onCloseLocked(Cleanup cleanup) {
        if (closed) {
            closeLateRegistration(cleanup);
            throw new IllegalStateException("Lifecycle scope is already closed");
        }
        cleanups.addLast(cleanup);
    }

    public boolean isClosed() {
        synchronized (this) {
            return closed;
        }
    }

    @Override
    public void close() {
        List<Cleanup> closing;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            closing = new ArrayList<>(cleanups);
            cleanups.clear();
        }

        RuntimeException failure = new RuntimeException("One or more lifecycle cleanup actions failed");
        boolean cleanupFailed = false;
        for (int index = closing.size() - 1; index >= 0; index--) {
            try {
                closing.get(index).run();
            } catch (Exception | Error throwable) {
                cleanupFailed = true;
                failure.addSuppressed(throwable);
            }
        }
        if (cleanupFailed) {
            throw failure;
        }
    }

    private static void closeLateRegistration(Cleanup cleanup) {
        try {
            cleanup.run();
        } catch (Exception exception) {
            throw new IllegalStateException("Lifecycle scope is closed and the late resource also failed to close", exception);
        }
    }
}
