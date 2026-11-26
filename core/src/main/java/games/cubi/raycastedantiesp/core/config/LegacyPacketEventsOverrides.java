package games.cubi.raycastedantiesp.core.config;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import java.util.List;

@ConfigSerializable
public record LegacyPacketEventsOverrides(List<Integer> tileEntityExemptedIds,
                                          List<Integer> tileEntityForceIncludedIds) {
    public static final LegacyPacketEventsOverrides DEFAULT = new LegacyPacketEventsOverrides(List.of(), List.of());
    public LegacyPacketEventsOverrides {
        tileEntityExemptedIds = List.copyOf(tileEntityExemptedIds);
        tileEntityForceIncludedIds = List.copyOf(tileEntityForceIncludedIds);
    }
}
