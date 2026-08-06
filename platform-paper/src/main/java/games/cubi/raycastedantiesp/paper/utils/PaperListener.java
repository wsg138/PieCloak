package games.cubi.raycastedantiesp.paper.utils;

import games.cubi.raycastedantiesp.paper.RaycastedAntiESP;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.util.concurrent.atomic.AtomicBoolean;

public abstract class PaperListener implements Listener, AutoCloseable {
    private final AtomicBoolean registered = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();

    @SuppressWarnings("unchecked")
    public final <T extends PaperListener> T register() {
        if (closed.get()) {
            throw new IllegalStateException("Cannot register a closed listener");
        }
        if (!registered.compareAndSet(false, true)) {
            throw new IllegalStateException("Listener is already registered");
        }
        try {
            Bukkit.getPluginManager().registerEvents(this, RaycastedAntiESP.get());
            return (T) this;
        } catch (RuntimeException | Error throwable) {
            HandlerList.unregisterAll(this);
            registered.set(false);
            closed.set(true);
            throw throwable;
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true) && registered.getAndSet(false)) {
            HandlerList.unregisterAll(this);
        }
    }
}
