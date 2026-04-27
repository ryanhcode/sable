package dev.ryanhcode.sable.sublevel.tracking_points;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.util.SableNBTUtils;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.Map;
import java.util.UUID;

public class SubLevelTrackingPointSavedData extends SavedData implements SubLevelObserver {
    public static final String FILE_ID = "sable_tracking_points";
    private final ServerLevel level;
    private final Map<UUID, TrackingPoint> trackingPoints = new Object2ObjectOpenHashMap<>();

    private SubLevelTrackingPointSavedData(final ServerLevel level) {
        this.level = level;
    }

    public static SubLevelTrackingPointSavedData getOrLoad(final ServerLevel level) {
        final SubLevelTrackingPointSavedData data = level.getDataStorage().computeIfAbsent(
                new Factory<>(
                        () -> {
                            return new SubLevelTrackingPointSavedData(level);
                        },
                        (tag, provider) -> SubLevelTrackingPointSavedData.load(level, tag),
                        null
                ),
                SubLevelTrackingPointSavedData.FILE_ID);

        return data;
    }

    private static SubLevelTrackingPointSavedData load(final ServerLevel level, final CompoundTag tag) {
        final SubLevelTrackingPointSavedData data = new SubLevelTrackingPointSavedData(level);

        final CompoundTag trackingPointsTag = tag.getCompound("tracking_points");

        for (final String key : trackingPointsTag.getAllKeys()) {
            final UUID uuid = UUID.fromString(key);
            final CompoundTag pointTag = trackingPointsTag.getCompound(key);

            final boolean inSubLevel = pointTag.getBoolean("InSubLevel");
            final GlobalSavedSubLevelPointer pointer = pointTag.contains("SubLevelPointer") ?
                    GlobalSavedSubLevelPointer.CODEC.parse(NbtOps.INSTANCE, pointTag.getCompound("SubLevelPointer")).getOrThrow() :
                    null;
            final Vector3d point = SableNBTUtils.readVector3d(pointTag.getCompound("Point"));

            Vector3d globalPlaceholder = null;

            if (pointTag.contains("GlobalPlaceholder")) {
                globalPlaceholder = SableNBTUtils.readVector3d(pointTag.getCompound("GlobalPlaceholder"));
            }

            UUID subLevelID = null;

            if (pointTag.contains("SubLevelID")) {
                subLevelID = pointTag.getUUID("SubLevelID");
            }

            final TrackingPoint trackingPoint = new TrackingPoint(inSubLevel, subLevelID, pointer, point, globalPlaceholder);
            data.trackingPoints.put(uuid, trackingPoint);
        }

        return data;
    }

    @Override
    public CompoundTag save(final CompoundTag compoundTag, final HolderLookup.Provider provider) {
        final SubLevelContainer container = SubLevelContainer.getContainer(this.level);
        assert container != null : "Sub-level container is null";

        final CompoundTag loginPointsTag = new CompoundTag();

        for (final Map.Entry<UUID, TrackingPoint> entry : this.trackingPoints.entrySet()) {
            final CompoundTag pointTag = new CompoundTag();

            final TrackingPoint trackingPoint = entry.getValue();
            pointTag.putBoolean("InSubLevel", trackingPoint.inSubLevel());
            if (trackingPoint.lastSavedSubLevelPointer() != null) {
                pointTag.put("SubLevelPointer", GlobalSavedSubLevelPointer.CODEC.encodeStart(NbtOps.INSTANCE, trackingPoint.lastSavedSubLevelPointer()).getOrThrow());
            }
            pointTag.put("Point", SableNBTUtils.writeVector3d(trackingPoint.point()));

            if (trackingPoint.globalPlaceholderPosition() != null) {
                pointTag.put("GlobalPlaceholder", SableNBTUtils.writeVector3d(trackingPoint.globalPlaceholderPosition()));
            }

            if (trackingPoint.subLevelID() != null) {
                pointTag.putUUID("SubLevelID", trackingPoint.subLevelID());
            }

            loginPointsTag.put(entry.getKey().toString(), pointTag);
        }

        compoundTag.put("tracking_points", loginPointsTag);

        return compoundTag;
    }

    /**
     * Generates a tracking point for a player to spawn at
     */
    public @Nullable UUID generateTrackingPoint(final ServerPlayer player) {
        final ServerSubLevel subLevel = (ServerSubLevel) Sable.HELPER.getTrackingSubLevel(player);

        return this.generateTrackingPoint(player, subLevel);
    }

    /**
     * Generates a tracking point for a player
     */
    public @Nullable UUID generateTrackingPoint(final ServerPlayer player, final ServerSubLevel subLevel) {
        if (subLevel == null) return null;

        final GlobalSavedSubLevelPointer pointer = subLevel.getLastSerializationPointer();
        final Vector3d globalPlaceholderPosition = pointer == null ? JOMLConversion.toJOML(player.position()) : null;
        final TrackingPoint trackingPoint = new TrackingPoint(true, subLevel.getUniqueId(), pointer, subLevel.logicalPose().transformPositionInverse(JOMLConversion.toJOML(player.position())), globalPlaceholderPosition);
        final UUID uuid = player.getUUID();

        this.trackingPoints.put(uuid, trackingPoint);
        this.setDirty(true);

        return uuid;
    }

    /**
     * Generates a tracking point for a player
     */
    public @Nullable UUID generateTrackingPoint(final Vec3 pos, final ServerSubLevel subLevel) {
        if (subLevel == null) return null;

        final Pose3d pose = subLevel.logicalPose();

        final GlobalSavedSubLevelPointer pointer = subLevel.getLastSerializationPointer();
        final Vector3d globalPlaceholderPosition = pointer == null ? pose.transformPosition(JOMLConversion.toJOML(pos)) : null;
        final TrackingPoint trackingPoint = new TrackingPoint(true, subLevel.getUniqueId(), pointer, JOMLConversion.toJOML(pos), globalPlaceholderPosition);
        final UUID uuid = UUID.randomUUID();

        this.trackingPoints.put(uuid, trackingPoint);
        this.setDirty(true);

        return uuid;
    }

    public record TakenLoginPoint(Vector3dc position, @Nullable UUID subLevelId, @Nullable Vector3d localAnchor) { }

    public TakenLoginPoint take(final UUID uuid, final boolean remove) {
        final TrackingPoint point = remove ? this.trackingPoints.remove(uuid) : this.trackingPoints.get(uuid);

        if (remove) {
            this.setDirty(true);
        }

        if (point == null) {
            return null;
        }

        if (point.inSubLevel()) {
            final SubLevel existingSubLevel = Sable.HELPER.getContaining(this.level, point.point());

            if (existingSubLevel != null) {
                return new TakenLoginPoint(existingSubLevel.logicalPose().transformPosition(new Vector3d(point.point())), existingSubLevel.getUniqueId(), new Vector3d(point.point()));
            } else {
                final ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(this.level);
                final SubLevelHoldingChunkMap holdingMap = container.getHoldingChunkMap();

                if (point.subLevelID() != null) {
                    final HoldingSubLevel holdingSubLevel = holdingMap.getHoldingSubLevel(point.subLevelID());

                    if (holdingSubLevel != null) {
                        final SubLevelData data = holdingSubLevel.data();
                        return new TakenLoginPoint(data.pose().transformPosition(new Vector3d(point.point())), data.uuid(), new Vector3d(point.point()));
                    }
                }

                final GlobalSavedSubLevelPointer pointer = point.lastSavedSubLevelPointer();

                if (pointer != null) {
                    Sable.LOGGER.info("Player logged in with tracking point in non-loaded sub-level. Attempting to load.");

                    final SubLevelData data = holdingMap.getStorage().attemptLoadSubLevel(pointer.chunkPos(), pointer.local());

                    if (data == null) {
                        Sable.LOGGER.warn("Failed to load sub-level at pointer {} for tracking point", point.lastSavedSubLevelPointer());
                        return null;
                    }
                    return new TakenLoginPoint(data.pose().transformPosition(new Vector3d(point.point())), data.uuid(), new Vector3d(point.point()));
                } else {
                    Sable.LOGGER.warn("Player logged in with tracking point in non-loaded sub-level without a pointer toward one. Placing them at their global placeholder.");

                    final Vector3d placeholder = point.globalPlaceholderPosition();

                    if (placeholder != null) {
                        return new TakenLoginPoint(placeholder, null, null);
                    } else {
                        Sable.LOGGER.error("Player logged in with tracking point in non-loaded sub-level without a pointer toward one, and without a placeholder. Something has gone wrong.");
                        return null;
                    }
                }
            }
        }

        return new TakenLoginPoint(point.point(), null, null);
    }

    public Iterable<Map.Entry<UUID, TrackingPoint>> getAllTrackingPoints() {
        return new ObjectArrayList<>(this.trackingPoints.entrySet());
    }


    public Iterable<Pair<UUID, TrackingPoint>> getAllTrackingPoints(final BoundingBox3ic bounds) {
        final ObjectArrayList<Pair<UUID, TrackingPoint>> keys = new ObjectArrayList<>();

        for (final Map.Entry<UUID, TrackingPoint> entry : this.trackingPoints.entrySet()) {
            final Vector3d point = entry.getValue().point();
            if (bounds.contains(point)) {
                keys.add(Pair.of(entry.getKey(), entry.getValue()));
            }
        }

        return keys;
    }

    public void setTrackingPoint(final UUID key, final TrackingPoint point) {
        this.trackingPoints.put(key, point);
        this.setDirty(true);
    }

    public void removeTrackingPoint(final UUID key) {
        this.trackingPoints.remove(key);
        this.setDirty(true);
    }

    @Nullable
    public TrackingPoint getTrackingPoint(final UUID uuid) {
        final TrackingPoint point = this.trackingPoints.get(uuid);
        return point;
    }
}