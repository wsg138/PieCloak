/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2026 Cubicake.
 * This file is part of RaycastedAntiESP.
 * RaycastedAntiESP is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License v3.0 only, which can be accessed at https://www.gnu.org/licenses/agpl-3.0.html.
 * See README.md for warranty disclaimer and further information.
 */

package games.cubi.raycastedantiesp.paper;

import de.oliver.fancynpcs.api.FancyNpcsPlugin;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.events.NpcModifyEvent;
import de.oliver.fancynpcs.api.events.NpcRemoveEvent;
import de.oliver.fancynpcs.api.events.NpcSpawnEvent;
import games.cubi.raycastedantiesp.core.entity.EntityBypassRegistry;
import games.cubi.raycastedantiesp.paper.utils.PaperListener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

final class FancyNpcsCompatibility extends PaperListener {
    private final EntityBypassRegistry.BypassScope bypassScope = EntityBypassRegistry.openFancyNpcScope();
    private final WeakIdentityRetirementGuard<Npc> retiredNpcs = new WeakIdentityRetirementGuard<>(bypassScope);
    private static final long MANAGER_SWEEP_PERIOD_TICKS = 40L;
    private FancyWeakOwnerSweeper weakOwnerSweeper;
    FancyNpcsCompatibility() {
        try {
            register();
            // PluginEnableEvent occurs AFTER FancyNpcs.onEnable; therefore
            // backfill NPCs that were constructed before this listener existed.
            var plugin = FancyNpcsPlugin.get();
            if (plugin != null && plugin.getNpcManager() != null) {
                // A registered but not yet created FancyNPC has no native
                // entity handle. In FancyNpcs 2.12.1, getEntityId() then
                // dereferences a null internal entity. Skip that NPC now;
                // its create/spawn callback will register the live ID later.
                for (var npc : plugin.getNpcManager().getAllNpcs()) {
                    if (npc != null) {
                        add(npc);
                    }
                }
            }
            weakOwnerSweeper = new FancyWeakOwnerSweeper(
                    () -> retiredNpcs.sweepManagedOwners(FancyNpcsCompatibility::managedOwner),
                    MANAGER_SWEEP_PERIOD_TICKS);
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

    // FancyNpcs calls NpcCreateEvent before npc.create(), when getEntityId() is invalid.
    // NpcSpawnEvent runs before viewer spawn packets and is the safe registration point.
    @EventHandler(priority = EventPriority.LOWEST)
    public void onNpcSpawn(NpcSpawnEvent event) {
        add(event.getNpc());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onNpcModify(NpcModifyEvent event) {
        add(event.getNpc());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNpcRemove(NpcRemoveEvent event) {
        // A deleted object must not claim a new incarnation through an
        // already queued async spawn. No native handle is needed to retire it.
        retiredNpcs.retireOwner(event.getNpc());
    }
    private void add(Npc npc) {
        // Remove and spawn callbacks may race across the main and async threads.
        // The owner-specific guard and its retirement are one atomic decision.
        retiredNpcs.registerManagedCurrent(npc, FancyNpcsCompatibility::nativeEntityId,
                FancyNpcsCompatibility::managedOwner);
    }

    private static Npc managedOwner(Npc npc) {
        var plugin = FancyNpcsPlugin.get();
        var manager = plugin == null ? null : plugin.getNpcManager();
        return manager == null ? null : manager.getNpcById(npc.getData().getId());
    }

    private static Integer nativeEntityId(Npc npc) {
        // FancyNpcs v2 adapters dereference their native entity directly.
        // Modify callbacks and startup backfill can see an NPC before
        // create(); restrict the exception handling to the external accessor.
        try {
            return npc.getEntityId();
        } catch (NullPointerException notYetCreated) {
            return null;
        }
    }
    @Override
    public void close() {
        try {
            super.close();
        } finally {
            try {
                if (weakOwnerSweeper != null) {
                    weakOwnerSweeper.close();
                }
            } finally {
                bypassScope.close();
            }
        }
    }
}
