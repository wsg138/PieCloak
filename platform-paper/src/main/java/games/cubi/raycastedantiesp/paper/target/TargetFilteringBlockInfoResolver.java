/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2025-2026 Cubicake and Contributors.
 * This file is part of PieCloak, a modified fork of RaycastedAntiESP.
 */

package games.cubi.raycastedantiesp.paper.target;

import games.cubi.raycastedantiesp.core.chunks.BlockInfoResolver;
import games.cubi.raycastedantiesp.packetevents.target.PacketEventsTargetFilter;

import java.util.Objects;
import java.util.function.IntPredicate;

/**
 * Keeps upstream occlusion data intact while limiting managed block entities to PieCloak's allowlist.
 */
public final class TargetFilteringBlockInfoResolver implements BlockInfoResolver {
    private final BlockInfoResolver delegate;
    private final IntPredicate shouldCullTileEntity;

    public TargetFilteringBlockInfoResolver(BlockInfoResolver delegate, PacketEventsTargetFilter targetFilter) {
        this(delegate, Objects.requireNonNull(targetFilter, "targetFilter")::shouldCullTileEntity);
    }

    TargetFilteringBlockInfoResolver(BlockInfoResolver delegate, IntPredicate shouldCullTileEntity) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
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
}
