package dev.ryanhcode.sable.sublevel.storage.serialization;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.SableConfig;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.ryanhcode.sable.util.SableNBTUtils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Quaterniondc;

import java.util.List;
import java.util.UUID;

/**
 * Serializes and saves sub-levels to disk.
 */
public class SubLevelSerializer {

    /**
     * For when absolutely everything is broken, and I need to step through the state management.
     */
    public static final boolean SUPER_DEBUG_MODE = false;

    private static @NotNull CompoundTag serialize(final ServerSubLevel subLevel, final List<UUID> dependencies) {
        final CompoundTag tag = new CompoundTag();
        final ServerLevelPlot plot = subLevel.getPlot();
        final ListTag dependencyTags = new ListTag();

        for (final UUID dependency : dependencies) {
            dependencyTags.add(NbtUtils.createUUID(dependency));
        }

        final Pose3d serializedPose = new Pose3d(subLevel.logicalPose());
        final Vector3dc selfCenterOfMass = subLevel.getSelfMassTracker().getCenterOfMass();
        serializedPose.position().set(subLevel.logicalPose().transformPosition(new Vector3d(selfCenterOfMass)));
        serializedPose.rotationPoint().set(selfCenterOfMass);

        tag.putUUID("uuid", subLevel.getUniqueId());
        tag.put("plot", plot.save());
        tag.put("pose", SableNBTUtils.writePose3d(serializedPose));
        tag.put("world_bounds", SableNBTUtils.writeBoundingBox(subLevel.boundingBox()));

        final RigidBodyHandle handle = RigidBodyHandle.of(subLevel);
        if (handle != null) {
            final Vector3d linearVelocity = handle.getLinearVelocity(new Vector3d());
            final Vector3d angularVelocity = handle.getAngularVelocity(new Vector3d());

            if (linearVelocity.lengthSquared() > 0.1 * 0.1) {
                tag.put("linear_velocity", SableNBTUtils.writeVector3d(linearVelocity));
            }

            if (angularVelocity.lengthSquared() > Math.toRadians(1.0)) {
                tag.put("angular_velocity", SableNBTUtils.writeVector3d(angularVelocity));
            }
        }

        if (subLevel.getName() != null) {
            tag.putString("display_name", subLevel.getName());
        }

        if (!dependencies.isEmpty()) {
            tag.put("loading_dependencies", dependencyTags);
        }

        final CompoundTag userDataTag = subLevel.getUserDataTag();
        if (userDataTag != null) {
            tag.put("user_data", userDataTag);
        }

        return tag;
    }

    /**
     * Loads a sub-level from the level data folder with a name
     *
     * @param tag tag to load from
     * @return the half loaded sub-level, or null if the loading fails
     */
    @Nullable
    public static SubLevelData fromData(final CompoundTag tag) {
        final UUID uuid = tag.getUUID("uuid");

        List<UUID> dependencies = List.of();
        if (tag.contains("loading_dependencies")) {
            final ListTag dependencyUUIDS = tag.getList("loading_dependencies", Tag.TAG_INT_ARRAY);

            dependencies = new ObjectArrayList<>();

            for (final Tag dependencyUUIDTag : dependencyUUIDS) {
                final UUID dependencyUUID = NbtUtils.loadUUID(dependencyUUIDTag);

                dependencies.add(dependencyUUID);
            }
        }

        final Pose3d pose = SableNBTUtils.readPose3d(tag.getCompound("pose"));
        final BoundingBox3d worldBounds = SableNBTUtils.readBoundingBox(tag.getCompound("world_bounds"));

        return new SubLevelData(
                uuid,
                worldBounds,
                pose,
                dependencies,
                tag
        );
    }

    /**
     * Fully loads a sub-level, adding it to the level
     *
     * @param level              the level to load the sub-level into
     * @param halfLoadedSubLevel the half loaded sub-level to fully load
     */
    public static ServerSubLevel fullyLoad(final ServerLevel level, final SubLevelData halfLoadedSubLevel) {
        return fullyLoad(level, halfLoadedSubLevel, null, null);
    }

    /**
     * Loads a serialized sub-level into the first free plot while applying a dimension-transfer transform.
     * Velocities are retained exactly and rotated into the destination coordinate system.
     *
     * @param level              the destination level
     * @param halfLoadedSubLevel the serialized source data
     * @param destinationPose    the destination pose
     * @param velocityRotation   rotation from source-world vectors to destination-world vectors
     * @return the replacement sub-level, or {@code null} when allocation fails
     */
    public static ServerSubLevel fullyLoadForTransfer(
            final ServerLevel level,
            final SubLevelData halfLoadedSubLevel,
            final Pose3dc destinationPose,
            final Quaterniondc velocityRotation) {
        return fullyLoad(level, halfLoadedSubLevel, destinationPose, velocityRotation);
    }

    private static ServerSubLevel fullyLoad(
            final ServerLevel level,
            final SubLevelData halfLoadedSubLevel,
            @Nullable final Pose3dc destinationPose,
            @Nullable final Quaterniondc velocityRotation) {
        final CompoundTag tag = halfLoadedSubLevel.fullTag();
        final CompoundTag plotTag = tag.getCompound("plot");

        final int plotX = plotTag.getInt("plot_x");
        final int plotZ = plotTag.getInt("plot_z");

        final Pose3d pose = destinationPose == null
                ? SableNBTUtils.readPose3d(tag.getCompound("pose"))
                : new Pose3d(destinationPose);

        final Vector3d position = pose.position();
        final Vector3d cor = pose.rotationPoint();
        if (Double.isNaN(position.x) ||
                Double.isNaN(position.y) ||
                Double.isNaN(position.z) ||
                Double.isNaN(cor.x) ||
                Double.isNaN(cor.y) ||
                Double.isNaN(cor.z)
        ) {
            Sable.LOGGER.error("Failed to load sub-level, invalid pose: {}", pose);
            return null;
        }

        final ServerSubLevelContainer plotContainer = SubLevelContainer.getContainer(level);

        final ServerSubLevel subLevel;
        try {
            subLevel = destinationPose == null
                    ? (ServerSubLevel) plotContainer.allocateSubLevel(halfLoadedSubLevel.uuid(), plotX, plotZ, pose)
                    : (ServerSubLevel) plotContainer.allocateNewSubLevel(halfLoadedSubLevel.uuid(), pose);
        } catch (final IllegalArgumentException e) {
            Sable.LOGGER.error("Failed to load sub-level {}, skipping", halfLoadedSubLevel, e);
            return null;
        } catch (final IllegalStateException e) {
            Sable.LOGGER.error("Failed to allocate a plot for sub-level {}, skipping", halfLoadedSubLevel, e);
            return null;
        }

        final ServerLevelPlot plot = subLevel.getPlot();
        if (destinationPose != null) {
            final BlockPos relocationOffset = plot.getRelocationOffset(plotTag);
            pose.rotationPoint().add(
                    relocationOffset.getX(),
                    relocationOffset.getY(),
                    relocationOffset.getZ());
        }
        try {
            plot.load(plotTag);
        } catch (final RuntimeException e) {
            plotContainer.removeSubLevel(subLevel, SubLevelRemovalReason.REMOVED);
            throw e;
        }

        if (plot.getBoundingBox() == BoundingBox3i.EMPTY || plot.getBoundingBox().volume() <= 0) {
            Sable.LOGGER.error("Failed to load sub-level, invalid plot bounds: {}", plot.getBoundingBox() == BoundingBox3i.EMPTY ? "EMPTY" : plot.getBoundingBox());
            plotContainer.removeSubLevel(subLevel, SubLevelRemovalReason.REMOVED);
            return null;
        }

        final SubLevelPhysicsSystem physicsSystem = plotContainer.physicsSystem();
        subLevel.logicalPose().set(pose);
        physicsSystem.getPipeline().teleport(subLevel, position, pose.orientation());
        subLevel.updateLastPose();

        final double velocityMultiplier = velocityRotation == null
                ? SableConfig.VELOCITY_RETAINED_ON_LOAD.getAsDouble()
                : 1.0;
        Vector3d linearVelocity = new Vector3d();
        Vector3d angularVelocity = new Vector3d();

        if (tag.contains("linear_velocity")) {
            linearVelocity = SableNBTUtils.readVector3d(tag.getCompound("linear_velocity"))
                    .mul(velocityMultiplier);
        }

        if (tag.contains("angular_velocity")) {
            angularVelocity = SableNBTUtils.readVector3d(tag.getCompound("angular_velocity"))
                    .mul(velocityMultiplier);
        }

        if (velocityRotation != null) {
            velocityRotation.transform(linearVelocity);
            velocityRotation.transform(angularVelocity);
        }

        physicsSystem.getPipeline().addLinearAndAngularVelocity(subLevel, linearVelocity, angularVelocity);

        if (tag.contains("display_name")) {
            subLevel.setName(tag.getString("display_name"));
        }

        if (tag.contains("user_data")) {
            subLevel.setUserDataTag(tag.getCompound("user_data"));
        }

        subLevel.updateBoundingBox();
        subLevel.forceUpdateGlobalBounds();
        return subLevel;
    }

    /**
     * Serializes a sub-level and converts it to a {@link SubLevelData}
     *
     * @param subLevel the sub-level to serialize
     */
    public static SubLevelData toData(final ServerSubLevel subLevel, final @NotNull List<UUID> dependencies) {
        final List<UUID> filteredDependencies = new ObjectArrayList<>(dependencies);
        filteredDependencies.remove(subLevel.getUniqueId());

        final CompoundTag tag = serialize(subLevel, filteredDependencies);

        return new SubLevelData(
                subLevel.getUniqueId(),
                new BoundingBox3d(subLevel.boundingBox()),
                new Pose3d(subLevel.logicalPose()),
                filteredDependencies,
                tag
        );
    }
}
