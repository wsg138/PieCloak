package games.cubi.raycastedantiesp.core.raycast;

import games.cubi.locatables.api.Locatable;
import games.cubi.locatables.api.Spatial;

import java.util.UUID;

public interface ParticleSpawner {

    enum Colour {
        RED, GREEN, BLUE,
    }

    void spawnParticleAt(Locatable locatable, Colour colour);

    void spawnParticleAt(UUID world, Spatial spatial, Colour colour);
}
