package games.cubi.raycastedantiesp.core.debug;

import games.cubi.locatables.BlockLocatable;
import games.cubi.locatables.Locatable;
import games.cubi.raycastedantiesp.core.players.PlayerData;
import games.cubi.raycastedantiesp.core.raycast.RaycastUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public final class VisibilityTraceService {
    private static final VisibilityTraceService INSTANCE = new VisibilityTraceService();
    private static final int MAX_RECENT_LINES = 80;

    private final ConcurrentHashMap<UUID, TraceSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<String> recentLines = new ConcurrentLinkedDeque<>();
    private final Object fileLock = new Object();

    private volatile Path outputPath;

    private VisibilityTraceService() {
    }

    public static VisibilityTraceService get() {
        return INSTANCE;
    }

    public void setOutputPath(Path outputPath) {
        this.outputPath = outputPath;
        if (outputPath == null) {
            return;
        }
        try {
            Path parent = outputPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not initialise visibility trace output path " + outputPath, exception);
        }
    }

    public Path outputPath() {
        return outputPath;
    }

    public TraceSession startEntityTrace(UUID viewerUUID, String viewerName, UUID entityUUID, int entityID, String label) {
        TraceSession session = TraceSession.entity(viewerUUID, viewerName, entityUUID, entityID, label);
        sessions.put(viewerUUID, session);
        write("TRACE_START " + session.describe());
        return session;
    }

    public TraceSession startBlockTrace(UUID viewerUUID, String viewerName, UUID world, int x, int y, int z, int blockID, String label) {
        TraceSession session = TraceSession.block(viewerUUID, viewerName, world, x, y, z, blockID, label);
        sessions.put(viewerUUID, session);
        write("TRACE_START " + session.describe());
        return session;
    }

    public Optional<TraceSession> session(UUID viewerUUID) {
        return Optional.ofNullable(sessions.get(viewerUUID));
    }

    public Optional<TraceSession> stop(UUID viewerUUID) {
        TraceSession removed = sessions.remove(viewerUUID);
        if (removed != null) {
            write("TRACE_STOP " + removed.describe());
        }
        return Optional.ofNullable(removed);
    }

    public boolean isTracingEntity(UUID viewerUUID, UUID entityUUID) {
        TraceSession session = sessions.get(viewerUUID);
        return session != null && session.matchesEntity(entityUUID);
    }

    public boolean isTracingBlock(UUID viewerUUID, BlockLocatable location) {
        TraceSession session = sessions.get(viewerUUID);
        return session != null && session.matchesBlock(location);
    }

    public void recordEntityDecision(PlayerData playerData, UUID entityUUID, int entityID, Locatable targetLocation,
                                     boolean wasVisible, RaycastUtil.RaycastDetails raycast, int currentTick) {
        TraceSession session = sessions.get(playerData.getPlayerUUID());
        if (session == null || !session.matchesEntity(entityUUID)) {
            return;
        }
        write("ENTITY_CHECK "
                + baseDecisionFields(playerData, targetLocation, wasVisible, raycast.canSee(), currentTick)
                + " entityUUID=" + entityUUID
                + " entityID=" + entityID
                + " raycast=" + raycast.describe());
    }

    public void recordBlockDecision(PlayerData playerData, BlockLocatable targetLocation, int blockID,
                                    boolean wasVisible, RaycastUtil.RaycastDetails raycast, int currentTick) {
        TraceSession session = sessions.get(playerData.getPlayerUUID());
        if (session == null || !session.matchesBlock(targetLocation)) {
            return;
        }
        write("BLOCK_ENTITY_CHECK "
                + baseDecisionFields(playerData, targetLocation, wasVisible, raycast.canSee(), currentTick)
                + " blockID=" + blockID
                + " block=" + blockString(targetLocation)
                + " raycast=" + raycast.describe());
    }

    public void recordBlockPacket(UUID viewerUUID, BlockLocatable location, String action, int blockID, String extra) {
        if (viewerUUID == null) {
            return;
        }
        TraceSession session = sessions.get(viewerUUID);
        if (session == null || !session.matchesBlock(location)) {
            return;
        }
        write("BLOCK_PACKET viewer=" + viewerUUID
                + " action=" + action
                + " blockID=" + blockID
                + " block=" + blockString(location)
                + (extra == null || extra.isBlank() ? "" : " " + extra));
    }

    public List<String> recentLines() {
        return List.copyOf(recentLines);
    }

    public void clear() {
        recentLines.clear();
        Path path = outputPath;
        if (path == null) {
            return;
        }
        synchronized (fileLock) {
            try {
                Files.writeString(path, "", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException exception) {
                write("TRACE_FILE_CLEAR_FAILED path=" + path + " error=" + exception.getMessage());
            }
        }
    }

    private String baseDecisionFields(PlayerData playerData, Locatable targetLocation, boolean wasVisible,
                                      boolean nowVisible, int currentTick) {
        Locatable playerLocation = playerData.ownLocation();
        double distance = playerLocation == null ? -1 : playerLocation.distance(targetLocation);
        LookInfo look = lookInfo(playerData, playerLocation, targetLocation);
        return "viewer=" + playerData.getPlayerUUID()
                + " tick=" + currentTick
                + " visibleBefore=" + wasVisible
                + " visibleAfter=" + nowVisible
                + " changed=" + (wasVisible != nowVisible)
                + " player=" + locString(playerLocation)
                + " target=" + locString(targetLocation)
                + " distance=" + format(distance)
                + " lookAngleDeg=" + look.angleDegrees()
                + " lookDot=" + look.dot()
                + " lookingAtTarget=" + look.lookingAtTarget();
    }

    private LookInfo lookInfo(PlayerData playerData, Locatable playerLocation, Locatable targetLocation) {
        if (playerLocation == null || targetLocation == null) {
            return LookInfo.unknown();
        }
        double dx = targetLocation.x() - playerLocation.x();
        double dy = targetLocation.y() - playerLocation.y();
        double dz = targetLocation.z() - playerLocation.z();
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length <= 0.000001) {
            return new LookInfo("0.000", "1.000", true);
        }
        double dot = ((dx / length) * playerData.lookX()) + ((dy / length) * playerData.lookY()) + ((dz / length) * playerData.lookZ());
        dot = Math.max(-1, Math.min(1, dot));
        double angle = Math.toDegrees(Math.acos(dot));
        return new LookInfo(format(angle), format(dot), angle <= 10.0);
    }

    private void write(String message) {
        String line = Instant.now() + " " + message;
        recentLines.addLast(line);
        while (recentLines.size() > MAX_RECENT_LINES) {
            recentLines.pollFirst();
        }

        Path path = outputPath;
        if (path == null) {
            return;
        }
        synchronized (fileLock) {
            try {
                Path parent = path.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(path, line + System.lineSeparator(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException exception) {
                recentLines.addLast(Instant.now() + " TRACE_FILE_WRITE_FAILED path=" + path + " error=" + exception.getMessage());
            }
        }
    }

    private String locString(Locatable location) {
        if (location == null) {
            return "null";
        }
        return "world=" + location.world()
                + ",x=" + format(location.x())
                + ",y=" + format(location.y())
                + ",z=" + format(location.z())
                + ",block=" + location.blockX() + "," + location.blockY() + "," + location.blockZ();
    }

    private String blockString(BlockLocatable location) {
        if (location == null) {
            return "null";
        }
        return "world=" + location.world() + ",x=" + location.blockX() + ",y=" + location.blockY() + ",z=" + location.blockZ();
    }

    private String format(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return String.valueOf(value);
        }
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private record LookInfo(String angleDegrees, String dot, boolean lookingAtTarget) {
        static LookInfo unknown() {
            return new LookInfo("unknown", "unknown", false);
        }
    }

    public record TraceSession(
            TargetType targetType,
            UUID viewerUUID,
            String viewerName,
            UUID entityUUID,
            int entityID,
            UUID world,
            int x,
            int y,
            int z,
            int blockID,
            String label,
            Instant startedAt
    ) {
        private static TraceSession entity(UUID viewerUUID, String viewerName, UUID entityUUID, int entityID, String label) {
            return new TraceSession(TargetType.ENTITY, viewerUUID, viewerName, entityUUID, entityID,
                    null, 0, 0, 0, -1, label, Instant.now());
        }

        private static TraceSession block(UUID viewerUUID, String viewerName, UUID world, int x, int y, int z, int blockID, String label) {
            return new TraceSession(TargetType.BLOCK_ENTITY, viewerUUID, viewerName, null, -1,
                    world, x, y, z, blockID, label, Instant.now());
        }

        public boolean matchesEntity(UUID candidate) {
            return targetType == TargetType.ENTITY && entityUUID != null && entityUUID.equals(candidate);
        }

        public boolean matchesBlock(BlockLocatable location) {
            return targetType == TargetType.BLOCK_ENTITY
                    && location != null
                    && world != null
                    && world.equals(location.world())
                    && x == location.blockX()
                    && y == location.blockY()
                    && z == location.blockZ();
        }

        public String describe() {
            List<String> fields = new ArrayList<>();
            fields.add("viewer=" + viewerName + "(" + viewerUUID + ")");
            fields.add("targetType=" + targetType);
            fields.add("label=" + label);
            fields.add("startedAt=" + startedAt);
            if (targetType == TargetType.ENTITY) {
                fields.add("entityUUID=" + entityUUID);
                fields.add("entityID=" + entityID);
            } else {
                fields.add("world=" + world);
                fields.add("x=" + x);
                fields.add("y=" + y);
                fields.add("z=" + z);
                fields.add("blockID=" + blockID);
            }
            return String.join(" ", fields);
        }
    }

    public enum TargetType {
        ENTITY,
        BLOCK_ENTITY
    }
}
