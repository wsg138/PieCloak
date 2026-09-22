from pathlib import Path


def replace_once(path, old, new):
    p = Path(path)
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected one match, found {count}\nOLD:\n{old}")
    p.write_text(text.replace(old, new, 1))

# Avoid repeated WorldGuard queries for sub-block movement; WG region boundaries are block-based.
entity = "core/src/main/java/games/cubi/raycastedantiesp/core/tracked/NettyEntity.java"
replace_once(entity,
    '    private volatile boolean visibilityExempt;\n',
    '    private volatile boolean visibilityExempt;\n    private int visibilityPolicyBlockX;\n    private int visibilityPolicyBlockY;\n    private int visibilityPolicyBlockZ;\n    private boolean visibilityPolicyPositionKnown;\n')
replace_once(entity,
    '    @Override\n    public TrackedEntity<?> setVisibilityExempt(boolean visibilityExempt) {\n        this.visibilityExempt = visibilityExempt;\n        return this;\n    }\n\n    public boolean setGlowing(boolean glowing) {',
    '''    @Override
    public TrackedEntity<?> setVisibilityExempt(boolean visibilityExempt) {
        this.visibilityExempt = visibilityExempt;
        return this;
    }

    /**
     * Packet-thread helper. Returns true when this is the first policy check or the entity entered a new block.
     */
    public boolean enterVisibilityPolicyBlock() {
        int blockX = floorBlock(x());
        int blockY = floorBlock(y());
        int blockZ = floorBlock(z());
        if (visibilityPolicyPositionKnown
                && blockX == visibilityPolicyBlockX
                && blockY == visibilityPolicyBlockY
                && blockZ == visibilityPolicyBlockZ) {
            return false;
        }
        visibilityPolicyBlockX = blockX;
        visibilityPolicyBlockY = blockY;
        visibilityPolicyBlockZ = blockZ;
        visibilityPolicyPositionKnown = true;
        return true;
    }

    private static int floorBlock(double coordinate) {
        return (int) Math.floor(coordinate);
    }

    public boolean setGlowing(boolean glowing) {''')
replace_once(entity,
    '        TRACKED_FLAGS.setOpaque(this, (byte) 0);\n',
    '        TRACKED_FLAGS.setOpaque(this, (byte) 0);\n        visibilityExempt = false;\n        visibilityPolicyPositionKnown = false;\n')

controller = "core/src/main/java/games/cubi/raycastedantiesp/core/view/controller/PacketEntityViewController.java"
replace_once(controller,
    '        entity.setVisibilityExempt(exempt);\n        if (exempt) {',
    '        entity.setVisibilityExempt(exempt);\n        entity.enterVisibilityPolicyBlock();\n        if (exempt) {')
replace_once(controller,
    '''        Locatable viewerLocation = playerData.ownLocation();
        UUID world = viewerLocation == null ? null : viewerLocation.world();
        boolean wasExempt = entity.visibilityExempt();
        boolean exempt = world != null
                && visibilityExemptionPolicy.isExempt(world, entity.x(), entity.y(), entity.z());
        entity.setVisibilityExempt(exempt);

        if (exempt) {
            if (!entity.visible() || !entity.clientVisible()) {
                applyDirectVisibility(playerData, entity, true, currentTick);
            }
            return false;
        }''',
    '''        if (!visibilityExemptionPolicy.isActive() || !entity.enterVisibilityPolicyBlock()) {
            return cancelIfEnabledAndHidden(entityID, playerData);
        }
        Locatable viewerLocation = playerData.ownLocation();
        UUID world = viewerLocation == null ? null : viewerLocation.world();
        boolean wasExempt = entity.visibilityExempt();
        boolean exempt = world != null
                && visibilityExemptionPolicy.isExempt(world, entity.x(), entity.y(), entity.z());
        entity.setVisibilityExempt(exempt);

        if (exempt) {
            if (!entity.visible() || !entity.clientVisible()) {
                applyDirectVisibility(playerData, entity, true, currentTick);
                // The SHOW replay already contains this movement. Suppress the original packet to avoid double motion.
                return true;
            }
            return false;
        }''')

# Block/chunk policy wiring.
block_controller = "packetevents/src/main/java/games/cubi/raycastedantiesp/packetevents/viewcontrollers/PacketEventsBlockViewController.java"
replace_once(block_controller,
    'import games.cubi.raycastedantiesp.core.players.PlayerRegistry;\n',
    'import games.cubi.raycastedantiesp.core.players.PlayerRegistry;\nimport games.cubi.raycastedantiesp.core.policy.VisibilityExemptionPolicy;\n')
replace_once(block_controller,
    '    private final IntSupplier currentTickSupplier;\n',
    '    private final IntSupplier currentTickSupplier;\n    private final VisibilityExemptionPolicy visibilityExemptionPolicy;\n')
replace_once(block_controller,
    '''    protected PacketEventsBlockViewController(BlockInfoResolver blockInfoResolver, boolean trackAllBlocks,
            IntSupplier currentTickSupplier) {
        this.blockInfoResolver = blockInfoResolver;''',
    '''    protected PacketEventsBlockViewController(BlockInfoResolver blockInfoResolver, boolean trackAllBlocks,
            IntSupplier currentTickSupplier) {
        this(blockInfoResolver, trackAllBlocks, currentTickSupplier, VisibilityExemptionPolicy.DISABLED);
    }

    protected PacketEventsBlockViewController(BlockInfoResolver blockInfoResolver, boolean trackAllBlocks,
            IntSupplier currentTickSupplier, VisibilityExemptionPolicy visibilityExemptionPolicy) {
        this.blockInfoResolver = blockInfoResolver;
        this.visibilityExemptionPolicy = visibilityExemptionPolicy;''')
replace_once(block_controller,
    '''        if (trackAllBlocks) {
            mutatingChunkParser = new BlockChunkParser(blockInfoResolver, this::getHiddenBlockId);
            nonMutatingChunkParser = new NonMutatingBlockChunkParser(blockInfoResolver, this::getHiddenBlockId);
        } else {
            mutatingChunkParser = new OcclusionChunkParser(blockInfoResolver, this::getHiddenBlockId);
            nonMutatingChunkParser = new NonMutatingOcclusionChunkParser(blockInfoResolver, this::getHiddenBlockId);
        }''',
    '''        if (trackAllBlocks) {
            mutatingChunkParser = new BlockChunkParser(
                    blockInfoResolver, this::getHiddenBlockId, visibilityExemptionPolicy);
            nonMutatingChunkParser = new NonMutatingBlockChunkParser(
                    blockInfoResolver, this::getHiddenBlockId, visibilityExemptionPolicy);
        } else {
            mutatingChunkParser = new OcclusionChunkParser(
                    blockInfoResolver, this::getHiddenBlockId, visibilityExemptionPolicy);
            nonMutatingChunkParser = new NonMutatingOcclusionChunkParser(
                    blockInfoResolver, this::getHiddenBlockId, visibilityExemptionPolicy);
        }''')

replace_once(block_controller,
    '''        TrackedTileEntity<PacketEventsTileEntityReplayData> tileEntity =
                getTrackedTileEntity(blockView, world, position);
        if (tileEntity == null) {
            handleUnknownBlockEntity(event, viewer, blockView, world, position, packet, tileChecksEnabled);
            return;
        }

        ensureTileReplayData(tileEntity).setBlockEntityData(packet.getBlockEntityType(), packet.getNBT());
        if (tileChecksEnabled && !blockView.isVisible(world, position, currentTick)) {''',
    '''        TrackedTileEntity<PacketEventsTileEntityReplayData> tileEntity =
                getTrackedTileEntity(blockView, world, position);
        boolean exempt = isVisibilityExempt(world, position);
        if (tileEntity == null) {
            if (!exempt) {
                handleUnknownBlockEntity(event, viewer, blockView, world, position, packet, tileChecksEnabled);
            }
            return;
        }

        boolean wasExempt = tileEntity.visibilityExempt();
        tileEntity.setVisibilityExempt(exempt);
        ensureTileReplayData(tileEntity).setBlockEntityData(packet.getBlockEntityType(), packet.getNBT());
        if (tileChecksEnabled && exempt && !tileEntity.visible()) {
            tileEntity.setVisible(true).setLastChecked(currentTick);
            event.setCancelled(true);
            processInitialTileEntityOperationSafely(playerData, viewer, Operation.SHOW, tileEntity,
                    blockView.tileEntityCheckModeToken(), currentTick, Stage.BLOCK,
                    playerData.acquireWorldEpoch());
            return;
        }
        if (tileChecksEnabled && wasExempt && !exempt && tileEntity.visible()) {
            tileEntity.setVisible(false).setLastChecked(TrackedTileEntity.NEVER_CHECKED);
            event.setCancelled(true);
            processInitialTileEntityOperationSafely(playerData, viewer, Operation.HIDE, tileEntity,
                    blockView.tileEntityCheckModeToken(), currentTick, Stage.BLOCK,
                    playerData.acquireWorldEpoch());
            return;
        }
        if (tileChecksEnabled && !blockView.isVisible(world, position, currentTick)) {''')

replace_once(block_controller,
    '''            if (tileEntity) {
                boolean visibleIfNew = !tileChecksEnabled || visibleIfNew(key, playerLocation, world);
                TrackedTileEntity<?> state =
                        blockView.updateOrInsertTileEntity(world, key, blockID, visibleIfNew);
                if (!tileChecksEnabled) {
                    blockView.recordOutboundTileEntityVisibility(state, true);
                } else if (state != null && !state.visible()) {''',
    '''            if (tileEntity) {
                boolean exempt = isVisibilityExempt(world, key);
                boolean visibleIfNew = !tileChecksEnabled || exempt || visibleIfNew(key, playerLocation, world);
                TrackedTileEntity<?> state =
                        blockView.updateOrInsertTileEntity(world, key, blockID, visibleIfNew);
                boolean wasExempt = state != null && state.visibilityExempt();
                if (state != null) {
                    state.setVisibilityExempt(exempt);
                    if (tileChecksEnabled && wasExempt && !exempt) {
                        state.setVisible(false).setLastChecked(TrackedTileEntity.NEVER_CHECKED);
                    }
                }
                if (!tileChecksEnabled || exempt) {
                    blockView.recordOutboundTileEntityVisibility(state, true);
                } else if (state != null && !state.visible()) {''')

replace_once(block_controller,
    '''        if (tileEntity) {
            boolean visibleIfNew = !tileChecksEnabled || visibleIfNew(location, playerData.ownLocation(), world);
            TrackedTileEntity<?> state =
                    blockView.updateOrInsertTileEntity(world, location, blockID, visibleIfNew);
            if (!tileChecksEnabled) {
                blockView.recordOutboundTileEntityVisibility(state, true);
            } else if (state != null && !state.visible()) {''',
    '''        if (tileEntity) {
            boolean exempt = isVisibilityExempt(world, location);
            boolean visibleIfNew = !tileChecksEnabled || exempt
                    || visibleIfNew(location, playerData.ownLocation(), world);
            TrackedTileEntity<?> state =
                    blockView.updateOrInsertTileEntity(world, location, blockID, visibleIfNew);
            boolean wasExempt = state != null && state.visibilityExempt();
            if (state != null) {
                state.setVisibilityExempt(exempt);
                if (tileChecksEnabled && wasExempt && !exempt) {
                    state.setVisible(false).setLastChecked(TrackedTileEntity.NEVER_CHECKED);
                }
            }
            if (!tileChecksEnabled || exempt) {
                blockView.recordOutboundTileEntityVisibility(state, true);
            } else if (state != null && !state.visible()) {''')
replace_once(block_controller,
    '    private boolean visibleIfNew(BlockSpatial location, Locatable playerLocation, UUID packetWorld) {',
    '''    private boolean isVisibilityExempt(UUID world, BlockSpatial location) {
        return visibilityExemptionPolicy.isExempt(
                world, location.blockX() + 0.5, location.blockY() + 0.5, location.blockZ() + 0.5);
    }

    private boolean visibleIfNew(BlockSpatial location, Locatable playerLocation, UUID packetWorld) {''')

# Chunk parser: query only after a block is already classified as a managed tile candidate.
parser = "packetevents/src/main/java/games/cubi/raycastedantiesp/packetevents/viewcontrollers/chunkparser/AbstractChunkParser.java"
replace_once(parser,
    'import games.cubi.raycastedantiesp.core.chunks.OccludingChunkDataImpl;\n',
    'import games.cubi.raycastedantiesp.core.chunks.OccludingChunkDataImpl;\nimport games.cubi.raycastedantiesp.core.policy.VisibilityExemptionPolicy;\n')
replace_once(parser,
    '    private final ClientVersion clientVersion;\n',
    '    private final ClientVersion clientVersion;\n    private final VisibilityExemptionPolicy visibilityExemptionPolicy;\n')
replace_once(parser,
    '''    protected AbstractChunkParser(BlockInfoResolver blockInfoResolver, boolean mutatePackets,
            IntUnaryOperator hiddenBlockID) {
        this.blockInfoResolver = blockInfoResolver;
        this.mutatePackets = mutatePackets;
        this.hiddenBlockID = hiddenBlockID;''',
    '''    protected AbstractChunkParser(BlockInfoResolver blockInfoResolver, boolean mutatePackets,
            IntUnaryOperator hiddenBlockID) {
        this(blockInfoResolver, mutatePackets, hiddenBlockID, VisibilityExemptionPolicy.DISABLED);
    }

    protected AbstractChunkParser(BlockInfoResolver blockInfoResolver, boolean mutatePackets,
            IntUnaryOperator hiddenBlockID, VisibilityExemptionPolicy visibilityExemptionPolicy) {
        this.blockInfoResolver = blockInfoResolver;
        this.mutatePackets = mutatePackets;
        this.hiddenBlockID = hiddenBlockID;
        this.visibilityExemptionPolicy = visibilityExemptionPolicy;''')
replace_once(parser,
    '''                            TrackedTileEntity<?> state = blockView.updateOrInsertTileEntity(
                                    world, key, blockID, !mutatePackets);
                            if (!mutatePackets) {
                                blockView.recordOutboundTileEntityVisibility(state, true);
                            } else if (state != null && !state.visible()) {''',
    '''                            boolean exempt = visibilityExemptionPolicy.isExempt(
                                    world, blockX + 0.5, blockY + 0.5, blockZ + 0.5);
                            TrackedTileEntity<?> state = blockView.updateOrInsertTileEntity(
                                    world, key, blockID, !mutatePackets || exempt);
                            boolean wasExempt = state != null && state.visibilityExempt();
                            if (state != null) {
                                state.setVisibilityExempt(exempt);
                                if (mutatePackets && wasExempt && !exempt) {
                                    state.setVisible(false).setLastChecked(TrackedTileEntity.NEVER_CHECKED);
                                }
                            }
                            if (!mutatePackets || exempt) {
                                blockView.recordOutboundTileEntityVisibility(state, true);
                            } else if (state != null && !state.visible()) {''')

# Constructor propagation for concrete parsers.
for path, cls in [
    ("packetevents/src/main/java/games/cubi/raycastedantiesp/packetevents/viewcontrollers/chunkparser/BlockChunkParser.java", "BlockChunkParser"),
    ("packetevents/src/main/java/games/cubi/raycastedantiesp/packetevents/viewcontrollers/chunkparser/OcclusionChunkParser.java", "OcclusionChunkParser"),
]:
    replace_once(path,
        'import games.cubi.raycastedantiesp.core.chunks.ChunkData;\n' if cls == 'BlockChunkParser' else 'import games.cubi.raycastedantiesp.core.chunks.OccludingChunkDataImpl;\n',
        ('import games.cubi.raycastedantiesp.core.chunks.ChunkData;\n' if cls == 'BlockChunkParser' else 'import games.cubi.raycastedantiesp.core.chunks.OccludingChunkDataImpl;\n')
        + 'import games.cubi.raycastedantiesp.core.policy.VisibilityExemptionPolicy;\n')
    replace_once(path,
        f'''    public {cls}(BlockInfoResolver blockInfoResolver, IntUnaryOperator hiddenBlockID) {{
        this(blockInfoResolver, true, hiddenBlockID);
    }}

    protected {cls}(BlockInfoResolver blockInfoResolver, boolean mutatePackets, IntUnaryOperator hiddenBlockID) {{
        super(blockInfoResolver, mutatePackets, hiddenBlockID);
    }}''',
        f'''    public {cls}(BlockInfoResolver blockInfoResolver, IntUnaryOperator hiddenBlockID) {{
        this(blockInfoResolver, hiddenBlockID, VisibilityExemptionPolicy.DISABLED);
    }}

    public {cls}(BlockInfoResolver blockInfoResolver, IntUnaryOperator hiddenBlockID,
            VisibilityExemptionPolicy visibilityExemptionPolicy) {{
        this(blockInfoResolver, true, hiddenBlockID, visibilityExemptionPolicy);
    }}

    protected {cls}(BlockInfoResolver blockInfoResolver, boolean mutatePackets, IntUnaryOperator hiddenBlockID,
            VisibilityExemptionPolicy visibilityExemptionPolicy) {{
        super(blockInfoResolver, mutatePackets, hiddenBlockID, visibilityExemptionPolicy);
    }}''')

for path, cls, parent in [
    ("packetevents/src/main/java/games/cubi/raycastedantiesp/packetevents/viewcontrollers/chunkparser/NonMutatingBlockChunkParser.java", "NonMutatingBlockChunkParser", "BlockChunkParser"),
    ("packetevents/src/main/java/games/cubi/raycastedantiesp/packetevents/viewcontrollers/chunkparser/NonMutatingOcclusionChunkParser.java", "NonMutatingOcclusionChunkParser", "OcclusionChunkParser"),
]:
    replace_once(path,
        'import games.cubi.raycastedantiesp.core.chunks.BlockInfoResolver;\n',
        'import games.cubi.raycastedantiesp.core.chunks.BlockInfoResolver;\nimport games.cubi.raycastedantiesp.core.policy.VisibilityExemptionPolicy;\n')
    replace_once(path,
        f'''    public {cls}(BlockInfoResolver blockInfoResolver, IntUnaryOperator hiddenBlockID) {{
        super(blockInfoResolver, false, hiddenBlockID);
    }}''',
        f'''    public {cls}(BlockInfoResolver blockInfoResolver, IntUnaryOperator hiddenBlockID) {{
        this(blockInfoResolver, hiddenBlockID, VisibilityExemptionPolicy.DISABLED);
    }}

    public {cls}(BlockInfoResolver blockInfoResolver, IntUnaryOperator hiddenBlockID,
            VisibilityExemptionPolicy visibilityExemptionPolicy) {{
        super(blockInfoResolver, false, hiddenBlockID, visibilityExemptionPolicy);
    }}''')

# Paper block-controller and bootstrap propagation.
paper_block = "platform-paper/src/main/java/games/cubi/raycastedantiesp/paper/packets/PaperPacketEventsBlockViewController.java"
replace_once(paper_block,
    'import games.cubi.raycastedantiesp.core.chunks.BlockInfoResolver;\n',
    'import games.cubi.raycastedantiesp.core.chunks.BlockInfoResolver;\nimport games.cubi.raycastedantiesp.core.policy.VisibilityExemptionPolicy;\n')
replace_once(paper_block,
    '''    public PaperPacketEventsBlockViewController(BlockInfoResolver blockInfoResolver, boolean trackAllBlocks, IntSupplier currentTickSupplier) {
        super(blockInfoResolver, trackAllBlocks, currentTickSupplier);''',
    '''    public PaperPacketEventsBlockViewController(BlockInfoResolver blockInfoResolver, boolean trackAllBlocks,
            IntSupplier currentTickSupplier, VisibilityExemptionPolicy visibilityExemptionPolicy) {
        super(blockInfoResolver, trackAllBlocks, currentTickSupplier, visibilityExemptionPolicy);''')
main = "platform-paper/src/main/java/games/cubi/raycastedantiesp/paper/RaycastedAntiESP.java"
replace_once(main,
    '                    new PaperPacketEventsBlockViewController(blockInfoResolver, trackAllBlocks, currentTickSupplier));',
    '                    new PaperPacketEventsBlockViewController(\n                            blockInfoResolver, trackAllBlocks, currentTickSupplier, visibilityExemptionPolicy));')

# Re-enable refreshes world cache before listener registration.
wg = "platform-paper/src/main/java/games/cubi/raycastedantiesp/paper/integrations/WorldGuardVisibilityExemption.java"
replace_once(wg,
    '    public void enable(JavaPlugin plugin) {\n        for (org.bukkit.World world : Bukkit.getWorlds()) {',
    '    public void enable(JavaPlugin plugin) {\n        worlds.clear();\n        for (org.bukkit.World world : Bukkit.getWorlds()) {')

# Focused entity boundary tests using the existing controller harness.
test = "core/src/test/java/games/cubi/raycastedantiesp/core/view/controller/PacketEntityViewControllerTest.java"
replace_once(test,
    'import games.cubi.raycastedantiesp.core.players.PlayerRegistry;\n',
    'import games.cubi.raycastedantiesp.core.players.PlayerRegistry;\nimport games.cubi.raycastedantiesp.core.policy.VisibilityExemptionPolicy;\n')
replace_once(test,
    '        CONTROLLER.directlyShownEntityIDs.clear();\n        CONTROLLER.replacementPassengerPackets.clear();',
    '        CONTROLLER.directlyShownEntityIDs.clear();\n        CONTROLLER.directlyHiddenEntityIDs.clear();\n        CONTROLLER.replacementPassengerPackets.clear();\n        CONTROLLER.clearMovement();')
insert_tests = '''
    @Test
    void hiddenEntityEnteringExemptBlockIsShownAndBoundaryMovementIsSuppressed() {
        UUID world = UUID.randomUUID();
        PlayerData playerData = registerPlayer(world);
        HarnessEntity entity = insertPassenger(playerData, world, 2, false, false);
        entity.enterVisibilityPolicyBlock();
        CONTROLLER.moveNext(2, 20, 0, 0);

        boolean cancelled = CONTROLLER.handleRelativeMove(null, playerData, 20);

        assertTrue(cancelled, "SHOW replay owns the boundary movement packet");
        assertTrue(entity.visibilityExempt());
        assertTrue(entity.visible());
        assertTrue(entity.clientVisible());
        assertEquals(List.of(2), CONTROLLER.directlyShownEntityIDs);
    }

    @Test
    void exemptEntityLeavingRegionIsHiddenBeforeBoundaryMovementCanLeak() {
        UUID world = UUID.randomUUID();
        PlayerData playerData = registerPlayer(world);
        HarnessEntity entity = insertPassenger(playerData, world, 2, true, true);
        entity.setPosition(20, 0, 0);
        entity.setVisibilityExempt(true);
        entity.enterVisibilityPolicyBlock();
        CONTROLLER.moveNext(2, 0, 0, 0);

        boolean cancelled = CONTROLLER.handleRelativeMove(null, playerData, 21);

        assertTrue(cancelled);
        assertFalse(entity.visibilityExempt());
        assertFalse(entity.visible());
        assertFalse(entity.clientVisible());
        assertEquals(List.of(2), CONTROLLER.directlyHiddenEntityIDs);
    }

'''
replace_once(test, '    private PlayerData registerPlayer(UUID world) {', insert_tests + '    private PlayerData registerPlayer(UUID world) {')
replace_once(test,
    '    private static final class HarnessController extends PacketEntityViewController<Void> {\n        private final List<Integer> directlyShownEntityIDs = new ArrayList<>();\n        private final List<int[]> replacementPassengerPackets = new ArrayList<>();',
    '''    private static final class HarnessController extends PacketEntityViewController<Void> {
        private final List<Integer> directlyShownEntityIDs = new ArrayList<>();
        private final List<Integer> directlyHiddenEntityIDs = new ArrayList<>();
        private final List<int[]> replacementPassengerPackets = new ArrayList<>();
        private int movementEntityID = -1;
        private double movementX;
        private double movementY;
        private double movementZ;

        private HarnessController() {
            super((world, x, y, z) -> x >= 10);
        }

        private void moveNext(int entityID, double x, double y, double z) {
            movementEntityID = entityID;
            movementX = x;
            movementY = y;
            movementZ = z;
        }

        private void clearMovement() {
            movementEntityID = -1;
        }

        private int processMovement(PlayerData playerData) {
            int entityID = movementEntityID;
            NettyEntity<?> entity = playerData.entityFromID(entityID);
            if (entity != null) {
                entity.setPosition(movementX, movementY, movementZ);
            }
            return entityID;
        }''')
replace_once(test,
    '        protected void processDirectEntityHide(PlayerData playerData, EntityView<?> view, NettyEntity<?> entity, int worldEpoch) {\n            entity.setClientVisible(false);\n        }',
    '        protected void processDirectEntityHide(PlayerData playerData, EntityView<?> view, NettyEntity<?> entity, int worldEpoch) {\n            directlyHiddenEntityIDs.add(entity.entityID());\n            entity.setClientVisible(false);\n        }')
for method in ['processRelativeMovePacket', 'processRelativeMoveAndRotationPacket', 'processTeleportPacket', 'processPositionSyncPacket']:
    old = f'''        protected int {method}(Void packet, PlayerData playerData, int currentTick) {{
            return -1;
        }}'''
    new = f'''        protected int {method}(Void packet, PlayerData playerData, int currentTick) {{
            return processMovement(playerData);
        }}'''
    replace_once(test, old, new)

# Chunk regression: an exempt managed tile must remain real/visible in a mutating parser.
chunk_test = "packetevents/src/test/java/games/cubi/raycastedantiesp/packetevents/viewcontrollers/chunkparser/ChunkParserTest.java"
chunk_case = '''
    @Test
    void mutatingParserLeavesExemptManagedTileVisible() {
        UUID world = UUID.randomUUID();
        Chunk_v1_18 section = airSection();
        section.set(3, 2, 1, 99);
        TileEntity tileEntity = new TileEntity((byte) (3 << 4 | 1), (short) 2, 0, null);
        Column column = new Column(0, 0, true, new BaseChunk[]{section}, new TileEntity[]{tileEntity});
        PacketEventsBlockView view = new PacketEventsBlockView(RESOLVER, true, STABLE_WORLD_EPOCH);
        view.applyTileEntityCheckMode(true, 0, unused -> {});

        Column replacement = new BlockChunkParser(
                RESOLVER, ignored -> 1,
                (worldId, x, y, z) -> world.equals(worldId) && x == 3.5 && y == 2.5 && z == 1.5)
                .parse(view, world, column, 0);

        assertNull(replacement);
        assertEquals(99, section.getBlockId(3, 2, 1));
        assertEquals(1, column.getTileEntities().length);
        TrackedTileEntity<?> tracked = view.getTrackedTileEntity(
                world, new ImmutableBlockSpatialImpl(3, 2, 1));
        assertNotNull(tracked);
        assertTrue(tracked.visible());
        assertTrue(tracked.visibilityExempt());
    }

'''
replace_once(chunk_test, '    @Test\n    void packetEventsAndCorePackingKeepYAndZDistinct() {', chunk_case + '    @Test\n    void packetEventsAndCorePackingKeepYAndZDistinct() {')
