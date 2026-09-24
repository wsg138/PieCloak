package games.cubi.raycastedantiesp.core.engine;

import games.cubi.raycastedantiesp.core.chunks.BlockInfoResolver;
import games.cubi.raycastedantiesp.core.config.raycast.EntityConfig;
import games.cubi.raycastedantiesp.core.players.PlayerData;
import games.cubi.raycastedantiesp.core.players.PlayerRegistry;
import games.cubi.raycastedantiesp.core.view.EntityView;
import games.cubi.raycastedantiesp.core.view.EntityViewTransition;
import games.cubi.raycastedantiesp.core.view.ViewRegistry;
import games.cubi.raycastedantiesp.packetevents.tracked.PacketEventsEntity;
import games.cubi.raycastedantiesp.packetevents.view.PacketEventsBlockView;
import games.cubi.raycastedantiesp.packetevents.view.PacketEventsEntityView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.BasicConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManagedEntityRevealRegressionTest {
    private static final BlockInfoResolver EMPTY_RESOLVER = new BlockInfoResolver() {
        @Override public boolean isOccluding(int blockStateID) { return false; }
        @Override public boolean isTileEntity(int blockStateID) { return false; }
        @Override public boolean hasBlockEntityData(int blockStateID) { return false; }
    };

    @BeforeEach
    void initialiseViews() {
        ViewRegistry.reset();
        ViewRegistry.initialise(
                epochSupplier -> new PacketEventsBlockView(EMPTY_RESOLVER, true, epochSupplier),
                PacketEventsEntityView::createEntityView,
                PacketEventsEntityView::createPlayerView
        );
    }

    @AfterEach
    void resetState() {
        PlayerRegistry.getInstance().clear();
        ViewRegistry.reset();
    }

    @Test
    void initiallyHiddenManagedEntityInsideAlwaysShowRadiusPublishesShowTransition()
            throws SerializationException {
        UUID world = UUID.randomUUID();
        PlayerData playerData = registerViewer(world);
        PacketEventsEntity target = new PacketEventsEntity(
                playerData, 4, 64, 0, 22, UUID.randomUUID(), false, 0, false);
        target.setClientVisible(false);
        EntityView<PacketEventsEntity> entityView = entityView(playerData);
        entityView.insertEntity(world, target);
        int worldEpoch = playerData.acquireWorldEpoch();

        AsyncVisibilityChecks visibilityChecks = new AsyncVisibilityChecks(null);
        visibilityChecks.processEntitySection(
                playerData,
                playerData.ownLocation(),
                playerData.blockView(),
                entityConfig(),
                false,
                10,
                worldEpoch,
                TickTimingBatchNoOp.INSTANCE);

        assertTrue(target.visible(), "the first async recheck must reveal a nearby hidden managed entity");
        assertFalse(target.clientVisible(), "the packet thread has not reconciled the SHOW transition yet");

        entityView.flushPendingTransitions();
        AtomicReference<EntityViewTransition.Type> transitionType = new AtomicReference<>();
        AtomicReference<PacketEventsEntity> transitionedEntity = new AtomicReference<>();
        AtomicInteger transitionEpoch = new AtomicInteger();
        entityView.drainTransitions((type, entity, epoch) -> {
            transitionType.set(type);
            transitionedEntity.set((PacketEventsEntity) entity);
            transitionEpoch.set(epoch);
        });

        assertEquals(EntityViewTransition.Type.SHOW, transitionType.get());
        assertSame(target, transitionedEntity.get());
        assertEquals(worldEpoch, transitionEpoch.get());
    }

    @Test
    void staleWorldEpochCannotRevealHiddenManagedEntity() throws SerializationException {
        UUID world = UUID.randomUUID();
        PlayerData playerData = registerViewer(world);
        PacketEventsEntity target = new PacketEventsEntity(
                playerData, 4, 64, 0, 23, UUID.randomUUID(), false, 0, false);
        target.setClientVisible(false);
        EntityView<PacketEventsEntity> entityView = entityView(playerData);
        entityView.insertEntity(world, target);
        int staleEpoch = playerData.acquireWorldEpoch();
        playerData.beginWorldTransition();
        playerData.completeWorldTransition(world);

        new AsyncVisibilityChecks(null).processEntitySection(
                playerData,
                playerData.ownLocation(),
                playerData.blockView(),
                entityConfig(),
                false,
                11,
                staleEpoch,
                TickTimingBatchNoOp.INSTANCE);

        assertFalse(target.visible());
        entityView.flushPendingTransitions();
        assertFalse(entityView.hasPendingTransitions());
    }

    private static PlayerData registerViewer(UUID world) {
        PlayerData playerData = PlayerRegistry.getInstance().registerAndGetPlayer(
                UUID.randomUUID(), 0, 1, PacketEventsEntity::createSelfEntity);
        playerData.beginWorldTransition();
        playerData.completeWorldTransition(world);
        playerData.updateOwnLocation(world, 0, 64, 0);
        return playerData;
    }

    @SuppressWarnings("unchecked")
    private static EntityView<PacketEventsEntity> entityView(PlayerData playerData) {
        return (EntityView<PacketEventsEntity>) playerData.entityView();
    }

    private static EntityConfig entityConfig() throws SerializationException {
        ConfigurationNode node = BasicConfigurationNode.root();
        node.node("enabled").set(true);
        node.node("hide-sounds-when-hidden").set(true);
        node.node("max-occluding-count").set(3);
        node.node("always-show-radius").set(24);
        node.node("raycast-radius").set(48);
        node.node("hide-on-spawn-distance").set(0);
        node.node("visible-recheck-interval-ticks").set(10);
        node.node("keep-client-entity-when-hidden").set(false);
        return EntityConfig.load(node, "checks.entity");
    }
}
