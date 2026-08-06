package games.cubi.raycastedantiesp.core.view;

import java.util.Objects;
import java.util.function.IntSupplier;

public final class ViewRegistry {
    private static volatile EntityView.Factory entityViewFactory;
    private static volatile EntityView.Factory playerEntityViewFactory;
    private static volatile BlockView.Factory blockViewFactory;

    private ViewRegistry() {}

    public static synchronized void initialise(BlockView.Factory blockFactory, EntityView.Factory entityFactory,
                                               EntityView.Factory playerFactory) {
        if (isInitialised()) {
            throw new IllegalStateException("ViewRegistry is already initialised");
        }
        blockViewFactory = Objects.requireNonNull(blockFactory, "blockFactory");
        entityViewFactory = Objects.requireNonNull(entityFactory, "entityFactory");
        playerEntityViewFactory = Objects.requireNonNull(playerFactory, "playerFactory");
    }

    public static synchronized void reset() {
        blockViewFactory = null;
        entityViewFactory = null;
        playerEntityViewFactory = null;
    }

    public static boolean isInitialised() {
        return blockViewFactory != null && entityViewFactory != null && playerEntityViewFactory != null;
    }

    public static BlockView createBlockView(IntSupplier worldEpochSupplier) {
        BlockView.Factory factory = blockViewFactory;
        if (factory == null) {
            throw new IllegalStateException("Block view factory is null. Did you forget to initialise ViewRegistry?");
        }
        return factory.createBlockView(worldEpochSupplier);
    }

    public static EntityView<?> createEntityView(IntSupplier worldEpochSupplier) {
        EntityView.Factory factory = entityViewFactory;
        if (factory == null) {
            throw new IllegalStateException("Entity view factory is null. Did you forget to initialise ViewRegistry?");
        }
        return factory.createEntityView(worldEpochSupplier);
    }

    public static EntityView<?> createPlayerEntityView(IntSupplier worldEpochSupplier) {
        EntityView.Factory factory = playerEntityViewFactory;
        if (factory == null) {
            throw new IllegalStateException("Player entity view factory is null. Did you forget to initialise ViewRegistry?");
        }
        return factory.createEntityView(worldEpochSupplier);
    }
}
