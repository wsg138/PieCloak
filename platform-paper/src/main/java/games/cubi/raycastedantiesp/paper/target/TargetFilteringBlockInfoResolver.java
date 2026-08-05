/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2025-2026 Cubicake and Contributors.
 * This file is part of PieCloak, a modified fork of RaycastedAntiESP.
 */

package games.cubi.raycastedantiesp.paper.target;

import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.world.blockentity.BlockEntityType;
import games.cubi.raycastedantiesp.core.chunks.BlockInfoResolver;
import games.cubi.raycastedantiesp.packetevents.target.PacketEventsTargetFilter;

import java.util.Objects;
import java.util.function.IntPredicate;

/**
 * Keeps upstream occlusion data intact while limiting managed block entities to PieCloak's allowlist.
 * Also exposes the resolved PacketEvents target classifier to the block packet adapter.
 */
public final class TargetFilteringBlockInfoResolver implements BlockInfoResolver, PacketEventsTargetFilter {
    private final BlockInfoResolver delegate;
    private final PacketEventsTargetFilter targetFilter;
    private final IntPredicate shouldCullTileEntity;

    public TargetFilteringBlockInfoResolver(BlockInfoResolver delegate, PacketEventsTargetFilter targetFilter) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.targetFilter = Objects.requireNonNull(targetFilter, "targetFilter");
        this.shouldCullTileEntity = targetFilter::shouldCullTileEntity;
    }

    TargetFilteringBlockInfoResolver(BlockInfoResolver delegate, IntPredicate shouldCullTileEntity) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.targetFilter = PacketEventsTargetFilter.DISABLED;
        this.shouldCullTileEntity = Objects.requireNonNull(shouldCullTileEntity, "shouldCullTileEntity");
    }

    @Override
    public boolean isOccluding(int blockStateID) {
        return delegate.isOccluding(blockStateID);
    }

    @Override
    public boolean isTileEntity(int blockStateID) {
        return delegate.isTileEntity(blockStateID) && shouldCullTileEntity.test(blockStateID);
    }

    @Override
    public boolean hasBlockEntityData(int blockStateID) {
        return delegate.hasBlockEntityData(blockStateID);
    }

    @Override
    public boolean shouldCullEntity(EntityType entityType, boolean isPlayer) {
        return targetFilter.shouldCullEntity(entityType, isPlayer);
    }

    @Override
    public boolean shouldCullBlockState(int blockStateId) {
        return targetFilter.shouldCullBlockState(blockStateId);
    }

    @Override
    public boolean shouldCullBlockEntity(BlockEntityType blockEntityType) {
        return targetFilter.shouldCullBlockEntity(blockEntityType);
    }

    @Override
    public boolean shouldCullTileEntity(int blockStateId) {
        return shouldCullTileEntity.test(blockStateId);
    }
}
