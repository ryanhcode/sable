package dev.ryanhcode.sable;

import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.LevelPoseProviderExtension;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.mixinterface.plot.SubLevelContainerHolder;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.ryanhcode.sable.util.SableDistUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * The default Sable companion for when Sable is loaded
 */
public class ActiveSableCompanion implements SableCompanion {

    @Override
    public Iterable<SubLevel> getAllIntersecting(final Level level, final BoundingBox3dc bounds) {
        if (!(level instanceof final SubLevelContainerHolder holder)) {
            return List.of();
        }

        final SubLevelContainer plotContainer = holder.sable$getPlotContainer();

        if (plotContainer instanceof final ServerSubLevelContainer serverContainer) {
            final SubLevelPhysicsSystem physicsSystem = serverContainer.physicsSystem();

            return physicsSystem.queryIntersecting(bounds);
        } else {
            return plotContainer.queryIntersecting(bounds);
        }
    }

    @Override
    public @Nullable SubLevel getContaining(final Level level, final int chunkX, final int chunkZ) {
        if (!(level instanceof SubLevelContainerHolder)) {
            return null;
        }

        final SubLevelContainer container = ((SubLevelContainerHolder) level).sable$getPlotContainer();
        if (container == null) {
            return null;
        }

        final LevelPlot plot = container.getPlot(chunkX, chunkZ);
        return plot != null ? plot.getSubLevel() : null;
    }

    @Override
    public @Nullable SubLevel getContaining(final Level level, final ChunkPos chunkPos) {
        return this.getContaining(level, chunkPos.x, chunkPos.z);
    }

    @Override
    public @Nullable SubLevel getContaining(final Level level, final SectionPos pos) {
        return this.getContaining(level, pos.getX(), pos.getZ());
    }

    @Override
    public @Nullable SubLevel getContaining(final Level level, final Vec3i pos) {
        return this.getContaining(level, pos.getX() >> SectionPos.SECTION_BITS, pos.getZ() >> SectionPos.SECTION_BITS);
    }

    @Override
    public @Nullable SubLevel getContaining(final Level level, final Position pos) {
        return this.getContaining(level, Mth.floor(pos.x()) >> SectionPos.SECTION_BITS, Mth.floor(pos.z()) >> SectionPos.SECTION_BITS);
    }

    @Override
    public @Nullable SubLevel getContaining(final Level level, final Vector3dc pos) {
        return this.getContaining(level, Mth.floor(pos.x()) >> SectionPos.SECTION_BITS, Mth.floor(pos.z()) >> SectionPos.SECTION_BITS);
    }

    @Override
    public @Nullable SubLevel getContaining(final Level level, final double blockX, final double blockZ) {
        return this.getContaining(level, Mth.floor(blockX) >> SectionPos.SECTION_BITS, Mth.floor(blockZ) >> SectionPos.SECTION_BITS);
    }

    @Override
    public @Nullable SubLevel getContaining(final Entity entity) {
        final ChunkPos chunkPos = entity.chunkPosition();
        return this.getContaining(entity.level(), chunkPos.x, chunkPos.z);
    }

    @Override
    public @Nullable SubLevel getContaining(final BlockEntity blockEntity) {
        final BlockPos pos = blockEntity.getBlockPos();
        return this.getContaining(blockEntity.getLevel(), pos.getX() >> SectionPos.SECTION_BITS, pos.getZ() >> SectionPos.SECTION_BITS);
    }

    @Override
    public @Nullable ClientSubLevel getContainingClient(final int chunkX, final int chunkZ) {
        return (ClientSubLevel) this.getContaining(SableDistUtil.getClientLevel(), chunkX, chunkZ);
    }

    @Override
    public @Nullable ClientSubLevel getContainingClient(final ChunkPos chunkPos) {
        return (ClientSubLevel) this.getContaining(SableDistUtil.getClientLevel(), chunkPos.x, chunkPos.z);
    }

    @Override
    public @Nullable ClientSubLevel getContainingClient(final Position pos) {
        return (ClientSubLevel) this.getContaining(SableDistUtil.getClientLevel(), Mth.floor(pos.x()) >> SectionPos.SECTION_BITS, Mth.floor(pos.z()) >> SectionPos.SECTION_BITS);
    }

    @Override
    public @Nullable ClientSubLevel getContainingClient(final Vector3dc pos) {
        return (ClientSubLevel) this.getContaining(SableDistUtil.getClientLevel(), Mth.floor(pos.x()) >> SectionPos.SECTION_BITS, Mth.floor(pos.z()) >> SectionPos.SECTION_BITS);
    }

    @Override
    public @Nullable ClientSubLevel getContainingClient(final SectionPos pos) {
        return (ClientSubLevel) this.getContaining(SableDistUtil.getClientLevel(), pos.x(), pos.z());
    }

    @Override
    public @Nullable ClientSubLevel getContainingClient(final Vec3i pos) {
        return (ClientSubLevel) this.getContaining(SableDistUtil.getClientLevel(), pos.getX() >> SectionPos.SECTION_BITS, pos.getZ() >> SectionPos.SECTION_BITS);
    }

    @Override
    public @Nullable ClientSubLevel getContainingClient(final double blockX, final double blockZ) {
        return (ClientSubLevel) this.getContaining(SableDistUtil.getClientLevel(), Mth.floor(blockX) >> SectionPos.SECTION_BITS, Mth.floor(blockZ) >> SectionPos.SECTION_BITS);
    }

    @Override
    public @Nullable ClientSubLevel getContainingClient(final Entity entity) {
        final ChunkPos chunkPos = entity.chunkPosition();
        return (ClientSubLevel) this.getContaining(entity.level(), chunkPos.x, chunkPos.z);
    }

    @Override
    public @Nullable ClientSubLevel getContainingClient(final BlockEntity blockEntity) {
        final BlockPos pos = blockEntity.getBlockPos();
        return (ClientSubLevel) this.getContaining(blockEntity.getLevel(), pos.getX() >> SectionPos.SECTION_BITS, pos.getZ() >> SectionPos.SECTION_BITS);
    }

    @Override
    public Vector3d projectOutOfSubLevel(final Level level, final Vector3dc pos, final Vector3d dest) {
        final SubLevel subLevel = this.getContaining(level, pos);

        if (subLevel == null) return dest.set(pos);

        final Pose3dc pose;
        if (level instanceof final LevelPoseProviderExtension extension) {
            pose = extension.sable$getPose(subLevel);
        } else {
            pose = subLevel.logicalPose();
        }

        return pose.transformPosition(pos, dest);
    }

    @Override
    public Vec3 projectOutOfSubLevel(final Level level, final Vec3 pos) {
        return this.projectOutOfSubLevel(level, (Position) pos);
    }

    @Override
    public Vec3 projectOutOfSubLevel(final Level level, final Position pos) {
        final SubLevel subLevel = this.getContaining(level, pos);

        if (subLevel == null) return pos instanceof final Vec3 vec ? vec : new Vec3(pos.x(), pos.y(), pos.z());

        final Pose3dc pose;
        if (level instanceof final LevelPoseProviderExtension extension) {
            pose = extension.sable$getPose(subLevel);
        } else {
            pose = subLevel.logicalPose();
        }

        return JOMLConversion.toMojang(pose.transformPosition(JOMLConversion.toJOML(pos)));
    }

    @Override
    public @Nullable <T, S extends SubLevelAccess> T runIncludingSubLevels(final Level level, final Vec3 origin, final boolean shouldCheckOrigin, @Nullable final S subLevel, final BiFunction<S, BlockPos, T> converter) {
        return this.runIncludingSubLevels(level, (Position) origin, shouldCheckOrigin, subLevel, converter);
    }

    @SuppressWarnings("unchecked")
    @Override
    public @Nullable <T, S extends SubLevelAccess> T runIncludingSubLevels(final Level level, final Position origin, final boolean shouldCheckOrigin, @Nullable final S subLevel, final BiFunction<S, BlockPos, T> converter) {
        final BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos(origin.x(), origin.y(), origin.z());
        final Vector3d mutablePos = JOMLConversion.toJOML(origin);
        T test;

        // Initial test
        if (shouldCheckOrigin) {
            test = converter.apply(subLevel, mutableBlockPos.immutable());
            if (test != null)
                return test;
        }

        // Test projectedPos
        if (subLevel != null) {
            subLevel.logicalPose().transformPosition(mutablePos);
            mutableBlockPos.set(mutablePos.x, mutablePos.y, mutablePos.z);

            test = converter.apply(null, mutableBlockPos.immutable());
            if (test != null)
                return test;
        }

        final Vec3 copyPos = JOMLConversion.toMojang(mutablePos);

        // Test other sub-level plots
        final Iterable<SubLevel> subLevels = this.getAllIntersecting(level, new BoundingBox3d(BlockPos.containing(JOMLConversion.toMojang(mutablePos))));
        for (final SubLevel otherSubLevel : subLevels) {
            if (otherSubLevel == subLevel) // Ignore sub-level if it has already been checked
                continue;

            mutablePos.set(copyPos.x, copyPos.y, copyPos.z);

            otherSubLevel.logicalPose().transformPositionInverse(mutablePos);
            mutableBlockPos.set(mutablePos.x, mutablePos.y, mutablePos.z);

            test = converter.apply((S) otherSubLevel, mutableBlockPos.immutable());
            if (test != null)
                return test;
        }

        return null; // Return null if no valid position was found
    }

    @Override
    public <S extends SubLevelAccess> boolean findIncludingSubLevels(final Level level, final Vec3 origin, final boolean shouldCheckOrigin, @Nullable final S subLevel, final BiFunction<S, BlockPos, Boolean> converter) {
        return this.findIncludingSubLevels(level, (Position) origin, shouldCheckOrigin, subLevel, converter);
    }

    @Override
    public <S extends SubLevelAccess> boolean findIncludingSubLevels(final Level level, final Position origin, final boolean shouldCheckOrigin, @Nullable final S subLevel, final BiFunction<S, BlockPos, Boolean> converter) {
        return Boolean.TRUE.equals(
                this.runIncludingSubLevels(
                        level, origin, shouldCheckOrigin, subLevel,
                        (candidateSublevel, pos) -> Boolean.TRUE.equals(converter.apply(candidateSublevel, pos)) ? true : null //Null is treated as false as a fallback
                )
        );
    }

    @Override
    public double distanceSquaredWithSubLevels(final Level level, final Vector3dc a, final Vector3dc b) {
        final Vector3dc globalA = this.projectOutOfSubLevel(level, a, new Vector3d());
        final Vector3dc globalB = this.projectOutOfSubLevel(level, b, new Vector3d());

        return globalA.distanceSquared(globalB);
    }

    @Override
    public double distanceSquaredWithSubLevels(final Level level, final Position a, final Position b) {
        final Vector3dc globalA = this.projectOutOfSubLevel(level, JOMLConversion.toJOML(a));
        final Vector3dc globalB = this.projectOutOfSubLevel(level, JOMLConversion.toJOML(b));

        return globalA.distanceSquared(globalB);
    }

    @Override
    public double distanceSquaredWithSubLevels(final Level level, final Vector3dc a, final double bX, final double bY, final double bZ) {
        final Vector3dc globalA = this.projectOutOfSubLevel(level, a, new Vector3d());
        final Vector3dc globalB = this.projectOutOfSubLevel(level, new Vector3d(bX, bY, bZ));

        return globalA.distanceSquared(globalB);
    }

    @Override
    public double distanceSquaredWithSubLevels(final Level level, final Position a, final double bX, final double bY, final double bZ) {
        final Vector3dc globalA = this.projectOutOfSubLevel(level, JOMLConversion.toJOML(a));
        final Vector3dc globalB = this.projectOutOfSubLevel(level, new Vector3d(bX, bY, bZ));

        return globalA.distanceSquared(globalB);
    }

    @Override
    public double distanceSquaredWithSubLevels(final Level level, final double aX, final double aY, final double aZ, final double bX, final double bY, final double bZ) {
        final Vector3dc globalA = this.projectOutOfSubLevel(level, new Vector3d(aX, aY, aZ));
        final Vector3dc globalB = this.projectOutOfSubLevel(level, new Vector3d(bX, bY, bZ));

        return globalA.distanceSquared(globalB);
    }

    private static double rectilinearDistance(final Vector3dc a, final Vector3dc b) {
        final double d0 = Math.abs(b.x() - a.x());
        final double d1 = Math.abs(b.y() - a.y());
        final double d2 = Math.abs(b.z() - a.z());
        return Math.max(d0, Math.max(d1, d2));
    }

    @Override
    public double rectilinearDistanceWithSubLevels(final Level level, final Vector3dc a, final Vector3dc b) {
        final Vector3dc globalA = this.projectOutOfSubLevel(level, a, new Vector3d());
        final Vector3dc globalB = this.projectOutOfSubLevel(level, b, new Vector3d());

        return rectilinearDistance(globalA, globalB);
    }

    @Override
    public double rectilinearDistanceWithSubLevels(final Level level, final Position a, final Position b) {
        final Vector3dc globalA = this.projectOutOfSubLevel(level, JOMLConversion.toJOML(a));
        final Vector3dc globalB = this.projectOutOfSubLevel(level, JOMLConversion.toJOML(b));

        return rectilinearDistance(globalA, globalB);
    }

    @Override
    public double rectilinearDistanceWithSubLevels(final Level level, final Vector3dc a, final double bX, final double bY, final double bZ) {
        final Vector3dc globalA = this.projectOutOfSubLevel(level, a, new Vector3d());
        final Vector3dc globalB = this.projectOutOfSubLevel(level, new Vector3d(bX, bY, bZ));

        return rectilinearDistance(globalA, globalB);
    }

    @Override
    public double rectilinearDistanceWithSubLevels(final Level level, final Position a, final double bX, final double bY, final double bZ) {
        final Vector3dc globalA = this.projectOutOfSubLevel(level, JOMLConversion.toJOML(a));
        final Vector3dc globalB = this.projectOutOfSubLevel(level, new Vector3d(bX, bY, bZ));

        return rectilinearDistance(globalA, globalB);
    }

    @Override
    public double rectilinearDistanceWithSubLevels(final Level level, final double aX, final double aY, final double aZ, final double bX, final double bY, final double bZ) {
        final Vector3dc globalA = this.projectOutOfSubLevel(level, new Vector3d(aX, aY, aZ));
        final Vector3dc globalB = this.projectOutOfSubLevel(level, new Vector3d(bX, bY, bZ));

        return rectilinearDistance(globalA, globalB);
    }

    @Override
    public Vector3d getVelocity(final Level level, final Vector3dc pos, final Vector3d dest) {
        final SubLevel subLevel = this.getContaining(level, pos);

        if (subLevel == null) return dest.zero();

        return this.getVelocity(level, subLevel, pos, dest);
    }

    @Override
    public Vec3 getVelocity(final Level level, final Vec3 pos) {
        return JOMLConversion.toMojang(this.getVelocity(level, JOMLConversion.toJOML(pos), new Vector3d()));
    }

    @Override
    public Vec3 getVelocity(final Level level, final Position pos) {
        return JOMLConversion.toMojang(this.getVelocity(level, JOMLConversion.toJOML(pos), new Vector3d()));
    }

    @Override
    public Vector3d getVelocity(final Level level, final SubLevelAccess subLevel, final Vector3dc pos, final Vector3d dest) {
        final Pose3dc pose = subLevel.logicalPose();

        if (subLevel instanceof final ServerSubLevel serverSubLevel) {
            final ServerSubLevelContainer container = SubLevelContainer.getContainer((ServerLevel) level);
            assert container != null;

            final RigidBodyHandle handle = container.physicsSystem().getPhysicsHandle(serverSubLevel);
            final Vector3dc linearVelocity = handle.getLinearVelocity(new Vector3d());
            final Vector3dc angularVelocity = handle.getAngularVelocity(new Vector3d());

            // Use dest as a "temp" variable for calculating the local pos, then set it to the real value after
            final Vector3dc localPos = pose.transformPosition(pos, dest).sub(pose.position());

            return angularVelocity.cross(localPos, dest).add(linearVelocity);
        }

        // This uses dest to store the transformed position in, then immediately stores the real value after
        return pose.transformPosition(pos, new Vector3d())
                .sub(subLevel.lastPose().transformPosition(pos, dest), dest)
                .mul(20.0);
    }

    @Override
    public Vec3 getVelocity(final Level level, final SubLevelAccess subLevel, final Vec3 pos) {
        return JOMLConversion.toMojang(Sable.HELPER.getVelocity(level, subLevel, JOMLConversion.toJOML(pos), new Vector3d()));
    }

    @Override
    public Vec3 getVelocity(final Level level, final SubLevelAccess subLevel, final Position pos) {
        return JOMLConversion.toMojang(Sable.HELPER.getVelocity(level, subLevel, JOMLConversion.toJOML(pos), new Vector3d()));
    }

    @Override
    public Vector3d getVelocityRelativeToAir(final Level level, final Vector3dc pos, final Vector3d dest) {
        return SubLevelHelper.getVelocityRelativeToAir(level, pos, dest);
    }

    @Override
    public Vec3 getVelocityRelativeToAir(final Level level, final Vec3 pos) {
        return JOMLConversion.toMojang(SubLevelHelper.getVelocityRelativeToAir(level, JOMLConversion.toJOML(pos), new Vector3d()));
    }

    @Override
    public Vec3 getVelocityRelativeToAir(final Level level, final Position pos) {
        return JOMLConversion.toMojang(SubLevelHelper.getVelocityRelativeToAir(level, JOMLConversion.toJOML(pos), new Vector3d()));
    }

    @Override
    public boolean isInPlotGrid(final Level level, final int chunkX, final int chunkZ) {
        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        return container != null && container.inBounds(chunkX, chunkZ);
    }

    @Override
    public @Nullable SubLevel getTrackingSubLevel(final Entity entity) {
        return ((EntityMovementExtension) entity).sable$getTrackingSubLevel();
    }

    @Override
    public @Nullable SubLevel getLastTrackingSubLevel(final Entity entity) {
        final UUID uuid = ((EntityMovementExtension) entity).sable$getLastTrackingSubLevelID();
        if (uuid != null) {
            final SubLevelContainer container = SubLevelContainer.getContainer(entity.level());
            return container.getSubLevel(uuid);
        }
        return null;
    }

    @Override
    public @Nullable SubLevel getTrackingOrVehicleSubLevel(final Entity entity) {
        SubLevel trackingSubLevel = Sable.HELPER.getTrackingSubLevel(entity);

        if (trackingSubLevel == null) {
            trackingSubLevel = Sable.HELPER.getVehicleSubLevel(entity);
        }

        return trackingSubLevel;
    }

    @Override
    public @Nullable SubLevel getVehicleSubLevel(final Entity entity) {
        if (entity.getVehicle() != null) {
            return Sable.HELPER.getContaining(entity.getVehicle());
        }

        return null;
    }

    @Override
    public @NotNull Vec3 getEyePositionInterpolated(final Entity entity, final float partialTicks) {
        final SubLevel trackingSubLevel = Sable.HELPER.getTrackingOrVehicleSubLevel(entity);

        if (trackingSubLevel instanceof final ClientSubLevel clientSubLevel) {
            final Vector3d startPos = new Vector3d(entity.xo, entity.yo + entity.getEyeHeight(), entity.zo);
            final Vector3d endPos = new Vector3d(entity.getX(), entity.getY() + entity.getEyeHeight(), entity.getZ());

            final Pose3dc renderPose = clientSubLevel.renderPose(partialTicks);
            clientSubLevel.lastPose().transformPositionInverse(startPos);
            clientSubLevel.logicalPose().transformPositionInverse(endPos);

            startPos.lerp(endPos, partialTicks);
            renderPose.transformPosition(startPos);

            return new Vec3(startPos.x, startPos.y, startPos.z);
        } else {
            return entity.getEyePosition(partialTicks);
        }
    }

    @Override
    public @NotNull Vector3d getFeetPos(final Entity entity, final float distanceDown) {
        final Quaterniondc orientation = EntitySubLevelUtil.getCustomEntityOrientation(entity, 1.0f);
        return Sable.HELPER.getFeetPos(entity, distanceDown, orientation);
    }

    @Override
    public Level getClientLevel() {
        throw new UnsupportedOperationException("Should not be called");
    }
}
