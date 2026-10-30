/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2026 Cubicake.
 * This file is part of RaycastedAntiESP.
 * RaycastedAntiESP is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License v3.0 only, which can be accessed at https://www.gnu.org/licenses/agpl-3.0.html.
 * See README.md for warranty disclaimer and further information.
 */

package games.cubi.raycastedantiesp.paper;

import de.oliver.fancyholograms.api.FancyHologramsPlugin;
import de.oliver.fancyholograms.api.events.HologramCreateEvent;
import de.oliver.fancyholograms.api.events.HologramDeleteEvent;
import de.oliver.fancyholograms.api.events.HologramShowEvent;
import de.oliver.fancyholograms.api.events.HologramsLoadedEvent;
import de.oliver.fancyholograms.api.hologram.Hologram;
import games.cubi.raycastedantiesp.core.entity.EntityBypassRegistry;
import games.cubi.raycastedantiesp.paper.utils.PaperListener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

final class FancyHologramsCompatibility extends PaperListener {
    private final EntityBypassRegistry.BypassScope bypassScope = EntityBypassRegistry.openFancyHologramScope();
    private final WeakIdentityRetirementGuard<Hologram> retiredHolograms = new WeakIdentityRetirementGuard<>(bypassScope);
    FancyHologramsCompatibility() {
        try {
            register();
            // Holograms may already have loaded during FancyHolograms.onEnable.
            var plugin = FancyHologramsPlugin.get();
            if (plugin != null && plugin.getHologramManager() != null) {
                plugin.getHologramManager().getHolograms().forEach(this::add);
            }
        } catch (RuntimeException | Error failure) {
            try {
                close();
            } catch (RuntimeException | Error cleanupFailure) {
                if (cleanupFailure != failure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            throw failure;
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHologramCreate(HologramCreateEvent event) {
        add(event.getHologram());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHologramShow(HologramShowEvent event) {
        add(event.getHologram());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onHologramsLoaded(HologramsLoadedEvent event) {
        event.getManager().forEach(this::add);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHologramDelete(HologramDeleteEvent event) {
        int entityId = event.getHologram().getEntityId();
        retiredHolograms.retire(event.getHologram(), entityId);
    }

    private void add(Hologram hologram) {
        int entityId = hologram.getEntityId();
        retiredHolograms.registerIfActive(hologram, entityId);
    }
    @Override
    public void close() {
        try {
            super.close();
        } finally {
            bypassScope.close();
        }
    }
}
