package games.cubi.raycastedantiesp.packetevents.replaydata;

import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.protocol.potion.PotionType;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityVelocity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerRemoveEntityEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAttributes;
import games.cubi.raycastedantiesp.core.utils.Clearable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public sealed interface PacketEventsEntityReplayData extends Clearable permits PacketEventsEntityReplayData.Impl {
    int MAX_REPLAY_ENTRIES = 256;

    void addPacket(PacketWrapper<?> packet);

    List<PacketWrapper<?>> snapshotPackets(int entityID);

    int size();

    static PacketEventsEntityReplayData create() {
        return new Impl();
    }

    final class Impl implements PacketEventsEntityReplayData {
        private final Map<Integer, EntityData<?>> metadata = new LinkedHashMap<>(16, 0.75f, true);
        private final Map<EquipmentSlot, Equipment> equipment = new LinkedHashMap<>(8, 0.75f, true);
        private final Map<String, WrapperPlayServerUpdateAttributes.Property> attributes = new LinkedHashMap<>(8, 0.75f, true);
        private final Map<PotionType, WrapperPlayServerEntityEffect> effects = new LinkedHashMap<>(8, 0.75f, true);
        private Vector3d velocity;

        @Override
        public synchronized void addPacket(PacketWrapper<?> packet) {
            if (packet instanceof WrapperPlayServerEntityMetadata wrapper) {
                addMetadata(wrapper.getEntityMetadata());
            } else if (packet instanceof WrapperPlayServerEntityEquipment wrapper) {
                for (Equipment entry : wrapper.getEquipment()) {
                    equipment.put(entry.getSlot(), new Equipment(entry.getSlot(), entry.getItem().copy()));
                }
            } else if (packet instanceof WrapperPlayServerEntityVelocity wrapper) {
                addVelocity(wrapper.getVelocity());
            } else if (packet instanceof WrapperPlayServerEntityEffect wrapper) {
                effects.put(wrapper.getPotionType(), copyEffect(wrapper, wrapper.getEntityId()));
            } else if (packet instanceof WrapperPlayServerRemoveEntityEffect wrapper) {
                effects.remove(wrapper.getPotionType());
            } else if (packet instanceof WrapperPlayServerUpdateAttributes wrapper) {
                for (WrapperPlayServerUpdateAttributes.Property property : wrapper.getProperties()) {
                    attributes.put(attributeKey(property), copyProperty(property));
                }
            }
            trimToSafetyCap();
        }

        synchronized void addMetadata(List<EntityData<?>> entries) {
            for (EntityData<?> data : entries) {
                metadata.put(data.getIndex(), copyEntityData(data));
            }
            trimToSafetyCap();
        }

        synchronized void addVelocity(Vector3d updatedVelocity) {
            velocity = new Vector3d(updatedVelocity.getX(), updatedVelocity.getY(), updatedVelocity.getZ());
        }

        synchronized List<EntityData<?>> metadataSnapshot() {
            return List.copyOf(metadata.values());
        }

        synchronized Vector3d velocitySnapshot() {
            return velocity == null ? null : new Vector3d(velocity.getX(), velocity.getY(), velocity.getZ());
        }

        @Override
        public synchronized List<PacketWrapper<?>> snapshotPackets(int entityID) {
            List<PacketWrapper<?>> packets = new ArrayList<>(5 + effects.size());
            if (!metadata.isEmpty()) {
                packets.add(new WrapperPlayServerEntityMetadata(
                        entityID,
                        metadata.values().stream().map(this::copyEntityData).toList()
                ));
            }
            if (!equipment.isEmpty()) {
                packets.add(new WrapperPlayServerEntityEquipment(
                        entityID,
                        equipment.values().stream()
                                .map(entry -> new Equipment(entry.getSlot(), entry.getItem().copy()))
                                .toList()
                ));
            }
            if (velocity != null) {
                packets.add(new WrapperPlayServerEntityVelocity(
                        entityID,
                        new Vector3d(velocity.getX(), velocity.getY(), velocity.getZ())
                ));
            }
            for (WrapperPlayServerEntityEffect effect : effects.values()) {
                packets.add(copyEffect(effect, entityID));
            }
            if (!attributes.isEmpty()) {
                packets.add(new WrapperPlayServerUpdateAttributes(
                        entityID,
                        attributes.values().stream().map(this::copyProperty).toList()
                ));
            }
            return List.copyOf(packets);
        }

        @Override
        public synchronized int size() {
            return metadata.size() + equipment.size() + attributes.size() + effects.size() + (velocity == null ? 0 : 1);
        }

        @Override
        public synchronized void clear() {
            metadata.clear();
            equipment.clear();
            attributes.clear();
            effects.clear();
            velocity = null;
        }

        private void trimToSafetyCap() {
            while (size() > MAX_REPLAY_ENTRIES) {
                if (removeEldest(attributes) || removeEldest(metadata) || removeEldest(effects) || removeEldest(equipment)) {
                    continue;
                }
                velocity = null;
            }
        }

        private boolean removeEldest(Map<?, ?> map) {
            Iterator<?> iterator = map.keySet().iterator();
            if (!iterator.hasNext()) {
                return false;
            }
            iterator.next();
            iterator.remove();
            return true;
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private EntityData<?> copyEntityData(EntityData<?> data) {
            return new EntityData(data.getIndex(), data.getType(), data.getValue());
        }

        private WrapperPlayServerEntityEffect copyEffect(WrapperPlayServerEntityEffect effect, int entityID) {
            WrapperPlayServerEntityEffect copy = new WrapperPlayServerEntityEffect(
                    entityID,
                    effect.getPotionType(),
                    effect.getEffectAmplifier(),
                    effect.getEffectDurationTicks(),
                    buildEffectFlags(effect.isAmbient(), effect.isVisible(), effect.isShowIcon())
            );
            copy.setFactorData(effect.getFactorData());
            return copy;
        }

        private WrapperPlayServerUpdateAttributes.Property copyProperty(WrapperPlayServerUpdateAttributes.Property property) {
            if (property.getAttribute() != null) {
                return new WrapperPlayServerUpdateAttributes.Property(
                        property.getAttribute(),
                        property.getValue(),
                        List.copyOf(property.getModifiers())
                );
            }
            return new WrapperPlayServerUpdateAttributes.Property(
                    property.getKey(),
                    property.getValue(),
                    List.copyOf(property.getModifiers())
            );
        }

        private String attributeKey(WrapperPlayServerUpdateAttributes.Property property) {
            return property.getKey();
        }

        private byte buildEffectFlags(boolean ambient, boolean visible, boolean showIcon) {
            byte flags = 0;
            if (ambient) {
                flags |= 1;
            }
            if (visible) {
                flags |= 2;
            }
            if (showIcon) {
                flags |= 4;
            }
            return flags;
        }
    }
}
