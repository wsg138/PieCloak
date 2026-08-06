/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2026 Cubicake.
 * This file is part of RaycastedAntiESP.
 * RaycastedAntiESP is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License v3.0 only, which can be accessed at https://www.gnu.org/licenses/agpl-3.0.html.
 * See README.md for warranty disclaimer and further information.
 */

package games.cubi.raycastedantiesp.paper;

import de.oliver.fancyholograms.api.events.HologramCreateEvent;
import de.oliver.fancyholograms.api.events.HologramShowEvent;
import de.oliver.fancyholograms.api.events.HologramsLoadedEvent;
import de.oliver.fancyholograms.api.hologram.Hologram;
import de.oliver.fancynpcs.api.events.NpcCreateEvent;
import de.oliver.fancynpcs.api.events.NpcModifyEvent;
import de.oliver.fancynpcs.api.events.NpcSpawnEvent;
import games.cubi.logs.Logger;
import games.cubi.raycastedantiesp.core.entity.EntityBypassRegistry;
import games.cubi.raycastedantiesp.paper.utils.PaperListener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

public final class FancyCompatibility implements AutoCloseable {
    private final NPCs npcs;
    private final Holograms holograms;

    FancyCompatibility() {
        Logger.info("Attempting to initiate FancyNPCs and FancyHolograms compatibility layer. If you are not using those plugins, you will see an error. This is safe to ignore.", 5);
        NPCs startedNpcs = new NPCs().register();
        try {
            holograms = new Holograms().register();
            npcs = startedNpcs;
        } catch (Throwable throwable) {
            startedNpcs.close();
            throw throwable;
        }
    }

    @Override
    public void close() {
        holograms.close();
        npcs.close();
    }

    static class NPCs extends PaperListener {
        @EventHandler(priority = EventPriority.LOWEST)
        public void npcLoad(NpcSpawnEvent event) {
            EntityBypassRegistry.addEntity(event.getNpc().getEntityId());
        }

        @EventHandler(priority = EventPriority.LOWEST)
        public void npcLoad(NpcCreateEvent event) {
            EntityBypassRegistry.addEntity(event.getNpc().getEntityId());
        }

        @EventHandler(priority = EventPriority.LOWEST)
        public void npcLoad(NpcModifyEvent event) {
            EntityBypassRegistry.addEntity(event.getNpc().getEntityId());
        }
    }

    static class Holograms extends PaperListener {
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

        private void add(Hologram hologram) {
            EntityBypassRegistry.addEntity(hologram.getEntityId());
        }
    }
}
