package games.cubi.raycastedantiesp.packetevents.view;

import games.cubi.locatables.Locatable;
import games.cubi.logs.Logger;
import games.cubi.raycastedantiesp.core.view.EntityView;
import games.cubi.raycastedantiesp.core.view.EntityViewTransition;
import games.cubi.raycastedantiesp.packetevents.locatables.PacketEventsEntity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import static games.cubi.raycastedantiesp.core.locatables.NettyEntityLocatable.NO_VEHICLE;

public class PacketEventsEntityView implements EntityView<PacketEventsEntity> {
    private final Map<UUID, PacketEventsEntity> entitiesByUUID = new ConcurrentHashMap<>();
    private final Map<Integer, UUID> entityUUIDsByID = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<EntityViewTransition> transitions = new ConcurrentLinkedQueue<>();
    private final boolean isPlayerView;

    public PacketEventsEntityView(boolean isPlayerView) {
        this.isPlayerView = isPlayerView;
    }

    public static PacketEventsEntityView createPlayerView() {
        return new PacketEventsEntityView(true);
    }

    public static PacketEventsEntityView createEntityView() {
        return new PacketEventsEntityView(false);
    }

    @Override
    public void insertEntity(PacketEventsEntity entity) {
        if (entity == null || entity.entityUUID() == null) {
            Logger.error(new RuntimeException("Attempted to insert null entity or entity with null UUID into EntityView"), 2, PacketEventsEntityView.class);
            return;
        }
        entitiesByUUID.put(entity.entityUUID(), entity);
        entityUUIDsByID.put(entity.entityID(), entity.entityUUID());
    }

    @Override
    public void removeEntity(int entityID, int currentTick) {
        UUID entityUUID = entityUUIDsByID.remove(entityID);
        if (entityUUID == null) {
            return;
        }
        PacketEventsEntity removed = entitiesByUUID.remove(entityUUID);
        if (removed == null) {
            return;
        }
        removed.clear();
    }

    @Override
    public void removeEntity(UUID entityUUID, int currentTick) {
        int entityID = getEntityID(entityUUID);

        removeEntity(entityID, currentTick);
    }

    @Override
    public PacketEventsEntity getEntity(UUID entityUUID) {
        return entitiesByUUID.get(entityUUID);
    }

    @Override
    public PacketEventsEntity getEntity(int entityID) {
        return getTrackedEntity(entityID);
    }

    @Override
    public boolean exists(UUID entityUUID) {
        return entitiesByUUID.containsKey(entityUUID);
    }

    @Override
    public boolean exists(int entityID) {
        return entityUUIDsByID.containsKey(entityID);
    }

    @Override
    public boolean isVisible(int entityID) {
        PacketEventsEntity entity = getTrackedEntity(entityID);
        return entity == null || entity.visible();
    }

    @Override
    public Locatable getLocation(UUID entityUUID) {
        PacketEventsEntity entity = entitiesByUUID.get(entityUUID);
        if (entity == null) {
            return null;
        }
        return entity.clonePlainAndCentreIfBlockLocation().set(entity.x(), entity.y() + 0.5, entity.z(), entity.world());
    }

    @Override
    public int getEntityID(UUID entityUUID) {
        PacketEventsEntity entity = entitiesByUUID.get(entityUUID);
        return entity == null ? -1 : entity.entityID();
    }

    @Override
    public boolean isVisible(UUID entityUUID, int currentTick) {
        return isVisible(entityUUID);
    }

    @Override
    public boolean isVisible(UUID entityUUID) {
        PacketEventsEntity entity = entitiesByUUID.get(entityUUID);
        return entity == null || entity.visible();
    }

    @Override
    public void setVisibility(UUID entityUUID, boolean visible, int currentTick) {
        PacketEventsEntity existing = entitiesByUUID.get(entityUUID);
        if (existing == null) {
            Logger.debug("EntityView.setVisibility missing uuid=" + entityUUID
                    + " requestedVisible=" + visible
                    + " tick=" + currentTick);
            return;
        }
        if (existing.isSelfEntity()) return;

        List<PacketEventsEntity> mountGroup = getMountGroup(existing);
        if (!visible && !isCompleteMountGroup(mountGroup)) {
            visible = true;
        }

        boolean changed = false;
        for (PacketEventsEntity member : mountGroup) {
            if (member.visible() != visible) {
                changed = true;
            }
            member.setVisible(visible);
            member.setLastChecked(currentTick);
        }
        if (changed) {
            transitions.add(new EntityViewTransition(
                    visible ? EntityViewTransition.Type.SHOW : EntityViewTransition.Type.HIDE,
                    mountGroup.getFirst().entityUUID(),
                    mountGroup.getFirst().entityID()
            ));
        }
    }

    @Override
    public Collection<UUID> getKnownEntities() {
        return List.copyOf(entitiesByUUID.keySet());
    }

    @Override
    public Collection<UUID> getNeedingRecheck(int recheckTicks, int currentTick) {
        List<UUID> needingRecheck = new ArrayList<>();
        Set<Integer> checkedMountRoots = new HashSet<>();
        for (PacketEventsEntity state : entitiesByUUID.values()) {
            if (!state.cullTarget()) {
                continue;
            }
            List<PacketEventsEntity> mountGroup = getMountGroup(state);
            PacketEventsEntity root = mountGroup.getFirst();
            if (!checkedMountRoots.add(root.entityID())) {
                continue;
            }
            PacketEventsEntity raycastTarget = root.cullTarget()
                    ? root
                    : mountGroup.stream().filter(PacketEventsEntity::cullTarget).findFirst().orElse(state);
            if (raycastTarget.visible() && (currentTick - raycastTarget.lastChecked()) < recheckTicks) {
                continue;
            }
            needingRecheck.add(raycastTarget.entityUUID());
        }
        return needingRecheck;
    }

    @Override
    public boolean hasPendingTransitions() {
        return !transitions.isEmpty();
    }

    @Override
    public List<EntityViewTransition> drainTransitions() {
        List<EntityViewTransition> drained = new ArrayList<>();
        EntityViewTransition transition;
        while ((transition = transitions.poll()) != null) {
            drained.add(transition);
        }
        return drained;
    }

    public void requeueTransition(EntityViewTransition transition) {
        transitions.add(transition);
    }

    public List<PacketEventsEntity> getMountGroup(UUID entityUUID) {
        PacketEventsEntity entity = entitiesByUUID.get(entityUUID);
        return entity == null ? List.of() : getMountGroup(entity);
    }

    @Override
    public boolean isPlayerView() {
        return isPlayerView;
    }

    @Override
    public void clear() {
        entitiesByUUID.clear();
        entityUUIDsByID.clear();
        transitions.clear();
    }

    private PacketEventsEntity getTrackedEntity(int entityID) {
        UUID entityUUID = entityUUIDsByID.get(entityID);
        return entityUUID == null ? null : entitiesByUUID.get(entityUUID);
    }

    private List<PacketEventsEntity> getMountGroup(PacketEventsEntity entity) {
        PacketEventsEntity root = entity;
        Set<Integer> visited = new HashSet<>();
        while (root.vehicleID() != NO_VEHICLE && visited.add(root.entityID())) {
            PacketEventsEntity vehicle = getTrackedEntity(root.vehicleID());
            if (vehicle == null) {
                break;
            }
            root = vehicle;
        }

        List<PacketEventsEntity> group = new ArrayList<>();
        collectTrackedPassengers(root, group, new HashSet<>());
        return group;
    }

    private void collectTrackedPassengers(PacketEventsEntity entity, List<PacketEventsEntity> group, Set<Integer> visited) {
        if (!visited.add(entity.entityID())) {
            return;
        }
        group.add(entity);
        int[] passengerIDs = entity.passengerIDs();
        if (passengerIDs == null) {
            return;
        }
        for (int passengerID : passengerIDs) {
            PacketEventsEntity passenger = getTrackedEntity(passengerID);
            if (passenger != null) {
                collectTrackedPassengers(passenger, group, visited);
            }
        }
    }

    private boolean isCompleteMountGroup(List<PacketEventsEntity> mountGroup) {
        for (PacketEventsEntity member : mountGroup) {
            if (member.vehicleID() != NO_VEHICLE && getTrackedEntity(member.vehicleID()) == null) {
                return false;
            }
            int[] passengerIDs = member.passengerIDs();
            if (passengerIDs == null) {
                continue;
            }
            for (int passengerID : passengerIDs) {
                if (getTrackedEntity(passengerID) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    public String getStringDataForDebugging() {
        StringBuilder builder = new StringBuilder();
        builder.append("EntityView isPlayerView=").append(isPlayerView).append("\n");
        Set<Map.Entry<Integer, UUID>> entries = new HashSet<>(entityUUIDsByID.entrySet());
        for (Map.Entry<Integer, UUID> entry : entries) {
            PacketEventsEntity entity = entitiesByUUID.get(entry.getValue());
            builder.append("EntityID=").append(entry.getKey())
                    .append(" UUID=").append(entry.getValue())
                    .append(" Entity=").append(entity)
                    .append("\n");
        }
        return builder.toString();
    }
}
