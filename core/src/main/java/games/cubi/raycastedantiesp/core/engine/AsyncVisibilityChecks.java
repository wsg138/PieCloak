/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2026 Cubicake.
 * This file is part of RaycastedAntiESP.
 * RaycastedAntiESP is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License v3.0 only, which can be accessed at https://www.gnu.org/licenses/agpl-3.0.html.
 * See README.md for warranty disclaimer and further information.
 */

package games.cubi.raycastedantiesp.core.engine;

import games.cubi.locatables.api.Locatable;
import games.cubi.raycastedantiesp.core.config.raycast.EntityConfig;
import games.cubi.raycastedantiesp.core.config.raycast.PlayerConfig;
import games.cubi.raycastedantiesp.core.config.raycast.RaycastConfig;
import games.cubi.raycastedantiesp.core.entity.EntityBypassRegistry;
import games.cubi.raycastedantiesp.core.players.PlayerData;
import games.cubi.raycastedantiesp.core.raycast.ParticleSpawner;
import games.cubi.raycastedantiesp.core.raycast.RaycastUtil;
import games.cubi.raycastedantiesp.core.tracked.NettyEntity;
import games.cubi.raycastedantiesp.core.utils.PrimitiveIntArrayList;
import games.cubi.raycastedantiesp.core.view.BlockView;
import games.cubi.raycastedantiesp.core.view.EntityView;

final class AsyncVisibilityChecks {
    private final ParticleSpawner particleSpawner;

    AsyncVisibilityChecks(ParticleSpawner particleSpawner) {
        this.particleSpawner = particleSpawner;
    }

    void processEntitySection(
            PlayerData playerData,
            Locatable playerLocation,
            BlockView blockView,
            EntityConfig entityConfig,
            boolean debugParticles,
            int currentTick,
            int worldEpoch,
            TickTimingBatch timings) {
        if (!entityConfig.enabled()) {
            return;
        }
        long sectionStartNanos = timings.startEntitySection();
        checkEntities(playerData, playerLocation, entityConfig, debugParticles,
                blockView, currentTick, worldEpoch, timings);
        timings.finishEntitySection(sectionStartNanos);
    }

    void processPlayerSection(
            PlayerData playerData,
            Locatable playerLocation,
            BlockView blockView,
            PlayerConfig playerConfig,
            boolean debugParticles,
            int currentTick,
            int worldEpoch,
            TickTimingBatch timings) {
        if (!playerConfig.enabled()) {
            return;
        }
        long sectionStartNanos = timings.startPlayerSection();
        checkPlayers(playerData, playerLocation, playerConfig, debugParticles,
                blockView, currentTick, worldEpoch, timings);
        timings.finishPlayerSection(sectionStartNanos);
    }

    private void checkEntities(
            PlayerData player,
            Locatable playerLocation,
            EntityConfig entityConfig,
            boolean debugParticles,
            BlockView blockView,
            int currentTick,
            int worldEpoch,
            TickTimingBatch timings) {
        EntityView<?> entityView = player.entityView();
        RaycastUtil.Settings raycastSettings = raycastSettings(entityConfig, debugParticles, blockView);
        int checked = entityView.forEachNeedingRecheckEntity(
                entityConfig.getVisibleRecheckIntervalTicks(), currentTick,
                !(timings instanceof TickTimingBatchNoOp), worldEpoch, entity -> {
                    if (EntityBypassRegistry.isRelationshipSupportEntity(entity.entityID())) {
                        if (PrimitiveIntArrayList.isEmpty(entity.passengerIDs())) {
                            entityView.setVisibility(entity, true, currentTick, worldEpoch);
                        }
                        return;
                    }
                    if (entity.glowing()) {
                        setEntityAndSupportVehicleVisibility(
                                entityView, entity, true, currentTick, worldEpoch);
                        return;
                    }
                    if (attachedToViewerOrSelf(
                            player, entityView, entity, currentTick, worldEpoch)) {
                        return;
                    }
                    timings.incrementEntityRaycasts();
                    boolean canSee = RaycastUtil.raycast(
                            playerLocation, entity, raycastSettings, entity.getYOffset());
                    setEntityAndSupportVehicleVisibility(
                            entityView, entity, canSee, currentTick, worldEpoch);
                });
        timings.addEntityChecked(checked);
    }

    private static void setEntityAndSupportVehicleVisibility(
            EntityView<?> entityView,
            NettyEntity<?> entity,
            boolean visible,
            int currentTick,
            int worldEpoch) {
        NettyEntity<?> vehicle = entity.vehicleEntity();
        if (vehicle != null && EntityBypassRegistry.isRelationshipSupportEntity(vehicle.entityID())) {
            entityView.setVisibility(vehicle, visible, currentTick, worldEpoch);
        }
        entityView.setVisibility(entity, visible, currentTick, worldEpoch);
    }

    private void checkPlayers(
            PlayerData player,
            Locatable playerLocation,
            PlayerConfig playerConfig,
            boolean debugParticles,
            BlockView blockView,
            int currentTick,
            int worldEpoch,
            TickTimingBatch timings) {
        EntityView<?> playerView = player.playerView();
        RaycastUtil.Settings raycastSettings = raycastSettings(playerConfig, debugParticles, blockView);
        int checked = playerView.forEachNeedingRecheckEntity(
                playerConfig.getVisibleRecheckIntervalTicks(), currentTick,
                !(timings instanceof TickTimingBatchNoOp), worldEpoch, otherPlayer -> {
                    if (otherPlayer.glowing()
                            || (playerConfig.onlyCheckSneaking() && !otherPlayer.sneaking())) {
                        playerView.setVisibility(otherPlayer, true, currentTick, worldEpoch);
                        return;
                    }
                    if (attachedToViewerOrSelf(
                            player, playerView, otherPlayer, currentTick, worldEpoch)) {
                        return;
                    }
                    timings.incrementPlayerRaycasts();
                    boolean canSee = RaycastUtil.raycast(
                            playerLocation, otherPlayer, raycastSettings, 1.5f);
                    playerView.setVisibility(otherPlayer, canSee, currentTick, worldEpoch);
                });
        timings.addPlayerChecked(checked);
    }

    private RaycastUtil.Settings raycastSettings(
            RaycastConfig config, boolean debugParticles, BlockView blockView) {
        return new RaycastUtil.Settings(
                config.getMaxOccludingCount(),
                config.getAlwaysShowRadius(),
                config.getRaycastRadius(),
                debugParticles,
                blockView,
                particleSpawner);
    }

    private static boolean attachedToViewerOrSelf(
            PlayerData player,
            EntityView<?> view,
            NettyEntity<?> entity,
            int currentTick,
            int worldEpoch) {
        int selfEntityID = player.nettyData().getSelfEntityID();
        if (!attachmentRequiresVisibility(
                entity.leashingEntity(), entity.vehicleID(), entity.passengerIDs(), selfEntityID)) {
            return false;
        }
        view.setVisibility(entity, true, currentTick, worldEpoch);
        return true;
    }

    static boolean attachmentRequiresVisibility(
            int leashingEntityID,
            int vehicleID,
            int[] passengerIDs,
            int selfEntityID) {
        return leashingEntityID == selfEntityID
                || vehicleID == selfEntityID
                || PrimitiveIntArrayList.contains(passengerIDs, selfEntityID);
    }
}
