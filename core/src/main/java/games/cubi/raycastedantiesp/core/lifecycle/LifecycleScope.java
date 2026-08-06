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

    public synchronized <T extends AutoCloseable> T own(T resource) {
        Objects.requireNonNull(resource, "resource");
        onClose(resource::close);
        return resource;
    }

    public synchronized void onClose(Cleanup cleanup) {
        Objects.requireNonNull(cleanup, "cleanup");
        if (closed) {
            closeLateRegistration(cleanup);
            throw new IllegalStateException("Lifecycle scope is already closed");
        }
        cleanups.addLast(cleanup);
    }

    public synchronized boolean isClosed() {
        return closed;
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

        RuntimeException failure = null;
        for (int index = closing.size() - 1; index >= 0; index--) {
            try {
                closing.get(index).run();
            } catch (Throwable throwable) {
                if (failure == null) {
                    failure = new RuntimeException("One or more lifecycle cleanup actions failed");
                }
                failure.addSuppressed(throwable);
            }
        }
        if (failure != null) {
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
