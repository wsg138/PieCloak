/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2026 Cubicake.
 * This file is part of RaycastedAntiESP.
 * RaycastedAntiESP is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License v3.0 only, which can be accessed at https://www.gnu.org/licenses/agpl-3.0.html.
 * See README.md for warranty disclaimer and further information.
 */

package games.cubi.raycastedantiesp.paper;

import de.oliver.fancynpcs.api.events.NpcCreateEvent;
import de.oliver.fancynpcs.api.events.NpcModifyEvent;
import de.oliver.fancynpcs.api.events.NpcRemoveEvent;
import de.oliver.fancynpcs.api.events.NpcSpawnEvent;
import games.cubi.raycastedantiesp.core.entity.EntityBypassRegistry;
import games.cubi.raycastedantiesp.paper.utils.PaperListener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

final class FancyNpcsCompatibility extends PaperListener {
    FancyNpcsCompatibility() {
        register();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onNpcSpawn(NpcSpawnEvent event) {
        EntityBypassRegistry.addEntity(event.getNpc().getEntityId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onNpcCreate(NpcCreateEvent event) {
        EntityBypassRegistry.addEntity(event.getNpc().getEntityId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onNpcModify(NpcModifyEvent event) {
        EntityBypassRegistry.addEntity(event.getNpc().getEntityId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onNpcRemove(NpcRemoveEvent event) {
        EntityBypassRegistry.markEntityDespawned(event.getNpc().getEntityId());
    }
}
