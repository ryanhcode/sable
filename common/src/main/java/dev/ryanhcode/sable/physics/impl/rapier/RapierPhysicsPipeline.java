package dev.ryanhcode.sable.physics.impl.rapier;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.physics.constraint.fixed.FixedConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.free.FreeConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.generic.GenericConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.rotary.RotaryConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.mass.MassTracker;
import dev.ryanhcode.sable.api.physics.object.box.BoxHandle;
import dev.ryanhcode.sable.api.physics.object.box.BoxPhysicsObject;
import dev.ryanhcode.sable.api.physics.object.rope.RopeHandle;
import dev.ryanhcode.sable.api.physics.object.rope.RopePhysicsObject;
import dev.ryanhcode.sable.api.sublevel.KinematicContraption;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.*;
import dev.ryanhcode.sable.physics.chunk.VoxelNeighborhoodState;
import dev.ryanhcode.sable.physics.config.PhysicsConfigData;
import dev.ryanhcode.sable.physics.impl.rapier.box.RapierBoxHandle;
import dev.ryanhcode.sable.physics.impl.rapier.collider.RapierVoxelColliderBakery;
import dev.ryanhcode.sable.physics.impl.rapier.collider.RapierVoxelColliderData;
import dev.ryanhcode.sable.physics.impl.rapier.constraint.fixed.RapierFixedConstraintHandle;
import dev.ryanhcode.sable.physics.impl.rapier.constraint.free.RapierFreeConstraintHandle;
import dev.ryanhcode.sable.physics.impl.rapier.constraint.generic.RapierGenericConstraintHandle;
import dev.ryanhcode.sable.physics.impl.rapier.constraint.rotary.RapierRotaryConstraintHandle;
import dev.ryanhcode.sable.physics.impl.rapier.rope.RapierRopeHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.ryanhcode.sable.util.LevelAccelerator;
import dev.ryanhcode.sable.util.SableMathUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Implementation of {@link PhysicsPipeline} for the rust Rapier 3D physics engine.
 */
public class RapierPhysicsPipeline implements PhysicsPipeline {

    /**
     * Distance threshold for uploading sub-contraptions to the physics pipeline
     */
    private static final double DISTANCE_THRESHOLD = 1e-7;

    /**
     * Angle threshold for uploading sub-contraptions to the physics pipeline
     */
    private static final double ANGULAR_THRESHOLD = 1e-7;

    private record TrackedKinematicContraption(Vector3d lastUploadedPosition, Quaterniond lastUploadedOrientation, Vector3d lastUploadedLinVel, Vector3d lastUploadedAngVel, int id) {}
    private final ServerLevel level;
    private final LevelAccelerator accelerator;
    private final RapierVoxelColliderBakery colliderBakery;
    private final Int2ObjectMap<ServerSubLevel> activeSubLevels = new Int2ObjectArrayMap<>();
    private final Object2ObjectMap<KinematicContraption, TrackedKinematicContraption> activeContraptions = new Object2ObjectOpenHashMap<>();
    private final Long2LongOpenHashMap recentCollisions = new Long2LongOpenHashMap();
    private final int sceneId;
    private final double[] cache;

    public RapierPhysicsPipeline(final ServerLevel level) {
        this.level = level;
        this.accelerator = new LevelAccelerator(level);
        this.colliderBakery = new RapierVoxelColliderBakery(this.accelerator);
        this.recentCollisions.defaultReturnValue(-1);
        this.sceneId = Rapier3D.getID(this.level);
        this.cache = new double[7];
    }

    /**
     * Packs a voxel collider ID and neighborhood state into an integer the rapier companion library will re-interpret as a block-state.
     * @return the packed block state
     */
    private static int packBlockState(final VoxelNeighborhoodState state, final int colliderID) {
        return ((int) state.byteRepresentation()) | (colliderID << 16);
    }

    /**
     * Initializes the physics pipeline.
     *
     * @param gravity the gravity vector
     * @param universalDrag the universal drag to apply to all bodies
     */
    @Override
    public void init(final Vector3dc gravity, final double universalDrag) {
        try {
            Rapier3D.initialize(this.sceneId, gravity.x(), gravity.y(), gravity.z(), universalDrag);
        } catch (final UnsatisfiedLinkError e) {
            Sable.LOGGER.error("Sable has failed to link with the natives for its Rapier pipeline. Please report with system details to " + Sable.ISSUE_TRACKER_URL);
            final CrashReport crashReport = CrashReport.forThrowable(e, "Sable linking with Rapier natives");
            throw new ReportedException(crashReport);
        }
    }

    /**
     * Disposes all resources used by the physics pipeline.
     */
    @Override
    public void dispose() {
        Rapier3D.dispose();
    }

    /**
     * Runs a physics tick with a time step of {@code 1.0 / 20.0} seconds.
     */
    @Override
    public void prePhysicsTicks() {
        final double timeStep = 1.0 / 20.0;
        Rapier3D.tick(this.sceneId, timeStep);
    }

    /**
     * Runs a physics substep with a time step of {@code 1.0 / 20.0 / substeps} seconds.
     *
     * @param timeStep the time step of this physics substep [s]
     */
    @Override
    public void physicsTick(final double timeStep) {
        this.updateContraptionPoses();
        Rapier3D.step(this.sceneId, timeStep);
    }

    private void updateContraptionPoses() {
        for (final KinematicContraption contraption : this.activeContraptions.keySet()) {
            final TrackedKinematicContraption trackedContraption = this.activeContraptions.get(contraption);
            final SubLevelPhysicsSystem system = SubLevelPhysicsSystem.require(this.level);
            final double partialPhysicsTick = system.getPartialPhysicsTick();

            final SubLevel mountSubLevel = Sable.HELPER.getContaining(this.level, contraption.sable$getPosition());
            final Vector3dc parentCenterOfMass = mountSubLevel != null ? ((ServerSubLevel) mountSubLevel).getMassTracker().getCenterOfMass() : JOMLConversion.ZERO;

            final Vector3dc lastPosition = new Vector3d(contraption.sable$getPosition(partialPhysicsTick - 1.0f));
            final Quaterniondc lastOrientation = new Quaterniond(contraption.sable$getOrientation(partialPhysicsTick - 1.0f));

            final Vector3d pos = new Vector3d(contraption.sable$getPosition(partialPhysicsTick));
            final Quaterniondc rot = contraption.sable$getOrientation(partialPhysicsTick);

            final Vector3d linVel = pos.sub(lastPosition, new Vector3d());
            final Vector3d angVel = SableMathUtils.getAngularVelocity(lastOrientation, rot, new Vector3d());

            linVel.mul(20.0);
            angVel.mul(20.0);
            rot.transformInverse(linVel);
            rot.transformInverse(angVel);

            pos.sub(parentCenterOfMass);

            if (
                    pos.distanceSquared(trackedContraption.lastUploadedPosition()) > DISTANCE_THRESHOLD * DISTANCE_THRESHOLD ||
                            linVel.distanceSquared(trackedContraption.lastUploadedLinVel()) > DISTANCE_THRESHOLD * DISTANCE_THRESHOLD ||
                            angVel.distanceSquared(trackedContraption.lastUploadedAngVel()) > DISTANCE_THRESHOLD * DISTANCE_THRESHOLD ||
                            rot.div(trackedContraption.lastUploadedOrientation(), new Quaterniond()).angle() > ANGULAR_THRESHOLD * ANGULAR_THRESHOLD
            ) {
                final MassTracker massTracker = contraption.sable$getMassTracker();
                final Vector3dc centerOfMass = massTracker.getCenterOfMass();

                final double[] centerOfMassArray = new double[]{centerOfMass.x(), centerOfMass.y(), centerOfMass.z()};
                final double[] poseArray = {pos.x(), pos.y(), pos.z(), rot.x(), rot.y(), rot.z(), rot.w()};
                final double[] velocityArray = {linVel.x(), linVel.y(), linVel.z(), angVel.x(), angVel.y(), angVel.z()};
                Rapier3D.setKinematicContraptionTransform(this.sceneId, trackedContraption.id(), centerOfMassArray, poseArray, velocityArray);

                trackedContraption.lastUploadedPosition().set(pos);
                trackedContraption.lastUploadedLinVel().set(linVel);
                trackedContraption.lastUploadedAngVel().set(angVel);
                trackedContraption.lastUploadedOrientation().set(rot);
            }
        }
    }

    /**
     * Called after all physics substeps have been run, to finalize the physics tick.
     */
    @Override
    public void postPhysicsTicks() {
        this.processCollisionEffects();
    }

    private void processCollisionEffects() {
        this.recentCollisions.long2LongEntrySet().removeIf(entry -> this.level.getGameTime() - entry.getLongValue() > 2);

        final Vector3d localPointA = new Vector3d();
        final Vector3d localPointB = new Vector3d();
        final Vector3d localNormalA = new Vector3d();
        final Vector3d localNormalB = new Vector3d();

        final Vector3d globalPointA = new Vector3d();
        final Vector3d globalPointB = new Vector3d();

        final double[] collisions = Rapier3D.clearCollisions(this.sceneId);

        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        final BlockPos.MutableBlockPos cornerPos = new BlockPos.MutableBlockPos();

        for (int i = 0; i < collisions.length / 15; i++) {
            final int startIndex = i * 15;
            final int idA = (int) collisions[startIndex];
            final int idB = (int) collisions[startIndex + 1];

            final double forceAmount = collisions[startIndex + 2];
            localNormalA.set(collisions[startIndex + 3], collisions[startIndex + 4], collisions[startIndex + 5]);
            localNormalB.set(collisions[startIndex + 6], collisions[startIndex + 7], collisions[startIndex + 8]);
            localPointA.set(collisions[startIndex + 9], collisions[startIndex + 10], collisions[startIndex + 11]);
            localPointB.set(collisions[startIndex + 12], collisions[startIndex + 13], collisions[startIndex + 14]);

            final ServerSubLevel subLevelA = this.activeSubLevels.get(idA);
            final ServerSubLevel subLevelB = this.activeSubLevels.get(idB);

            final double minMass = Math.min(subLevelA != null ? subLevelA.getMassTracker().getMass() : Double.MAX_VALUE, subLevelB != null ? subLevelB.getMassTracker().getMass() : Double.MAX_VALUE);

            if (forceAmount > 25.0 * minMass) {
                BlockState stateA = Blocks.STONE.defaultBlockState();
                BlockState stateB = stateA;

                if (subLevelA != null) {
                    final Pose3d pose = subLevelA.logicalPose();
                    pos.set(localPointA.x + pose.rotationPoint().x, localPointA.y + pose.rotationPoint().y, localPointA.z + pose.rotationPoint().z);
                    cornerPos.set(localPointA.x + pose.rotationPoint().x + 0.5, localPointA.y + pose.rotationPoint().y + 0.5, localPointA.z + pose.rotationPoint().z + 0.5);

                    final long exists = this.recentCollisions.put(cornerPos.asLong(), this.level.getGameTime());

                    if (exists != -1) {
                        continue;
                    }

                    stateA = this.accelerator.getBlockState(pos);
                }


                if (subLevelB != null) {
                    final Pose3d pose = subLevelB.logicalPose();
                    pos.set(localPointB.x + pose.rotationPoint().x, localPointB.y + pose.rotationPoint().y, localPointB.z + pose.rotationPoint().z);
                    cornerPos.set(localPointB.x + pose.rotationPoint().x + 0.5, localPointB.y + pose.rotationPoint().y + 0.5, localPointB.z + pose.rotationPoint().z + 0.5);

                    final long exists = this.recentCollisions.put(cornerPos.asLong(), this.level.getGameTime());

                    if (exists != -1) {
                        continue;
                    }

                    stateB = this.accelerator.getBlockState(pos);
                }

                globalPointA.set(localPointA);
                globalPointB.set(localPointB);

                if (subLevelA != null) {
                    final Pose3d pose = subLevelA.logicalPose();
                    pose.orientation().transform(globalPointA).add(pose.position());
                }

                if (subLevelB != null) {
                    final Pose3d pose = subLevelB.logicalPose();
                    pose.orientation().transform(globalPointB).add(pose.position());
                }

                final BlockState state = stateB;
                this.level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), globalPointA.x, globalPointA.y, globalPointA.z, 2, 0.0, 0.0, 0.0, 0.1);

                final Vec3 position = JOMLConversion.toMojang(globalPointA);
                final float volumeScale = 0.4f;
                final SoundType soundType = state.getSoundType();

                this.level.playSound(null, position.x, position.y, position.z, soundType.getStepSound(), SoundSource.BLOCKS, 0.2f * volumeScale, (float) (0.6 - 0.2 + Math.random() * 0.4));
                this.level.playSound(null, position.x, position.y, position.z, soundType.getHitSound(), SoundSource.BLOCKS, 0.2f * volumeScale, (float) (Math.random() * 0.4));
                this.level.playSound(null, position.x, position.y, position.z, soundType.getPlaceSound(), SoundSource.BLOCKS, 0.2f * volumeScale, (float) (0.5 - 0.2 + Math.random() * 0.4));
            }
        }
    }

    /**
     * Runs a tick to update any separate sub-level tracking / logic, even if physics is currently paused
     */
    @Override
    public void tick() {
        this.accelerator.clearCache();
    }

    /**
     * Adds a {@link SubLevel} to the physics pipeline.
     */
    @Override
    public void add(final ServerSubLevel subLevel, final Pose3dc pose) {
        final Vector3dc pos = pose.position();
        final Quaterniondc rot = pose.orientation();

        subLevel.buildMassTracker();

        final int id = Rapier3D.getID(subLevel);
        Rapier3D.createSubLevel(this.sceneId, id, new double[]{pos.x(), pos.y(), pos.z(), rot.x(), rot.y(), rot.z(), rot.w()});

        subLevel.updateMergedMassData(1.0f);
        final Vector3dc centerOfMass = subLevel.getMassTracker().getCenterOfMass();

        if (centerOfMass != null) {
            subLevel.logicalPose().rotationPoint().set(centerOfMass);

            this.onStatsChanged(subLevel);
        }

        this.activeSubLevels.put(Rapier3D.getID(subLevel), subLevel);
    }

    /**
     * Removes a {@link SubLevel} from the physics pipeline.
     */
    @Override
    public void remove(final ServerSubLevel subLevel) {
        Rapier3D.removeSubLevel(this.sceneId, Rapier3D.getID(subLevel));
        this.activeSubLevels.remove(Rapier3D.getID(subLevel));
    }

    /**
     * Adds a kinematic contraption to the scene
     */
    @Override
    public void add(final KinematicContraption contraption) {
        if (this.activeContraptions.containsKey(contraption)) {
            throw new IllegalStateException("Contraption " + contraption + " is already present in pipeline");
        }

        final int id = this.getNextRuntimeID();
        this.activeContraptions.put(contraption, new TrackedKinematicContraption(new Vector3d(), new Quaterniond(), new Vector3d(), new Vector3d(), id));

        final SubLevel mountSubLevel = Sable.HELPER.getContaining(this.level, contraption.sable$getPosition());
        final int mountId = mountSubLevel != null ? Rapier3D.getID((ServerSubLevel) mountSubLevel) : -1;

        final BoundingBox3i localBounds = new BoundingBox3i();
        contraption.sable$getLocalBounds(localBounds);

        final Vector3dc pos = contraption.sable$getPosition();
        final Quaterniond rot = contraption.sable$getOrientation();
        final double[] pose = {pos.x(), pos.y(), pos.z(), rot.x(), rot.y(), rot.z(), rot.w()};

        Rapier3D.createKinematicContraption(this.sceneId, mountId, id, pose);

        // collect chunks

        record UploadingContraptionChunk(int[] data) { }
        final Long2ObjectMap<UploadingContraptionChunk> chunks = new Long2ObjectOpenHashMap<>();

        final BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();
        for (int x = localBounds.minX(); x <= localBounds.maxX(); x++) {
            for (int z = localBounds.minZ(); z <= localBounds.maxZ(); z++) {
                for (int y = localBounds.minY(); y <= localBounds.maxY(); y++) {
                    final BlockState blockState = contraption.sable$blockGetter().getBlockState(blockPos.set(x, y, z));

                    if (blockState.isAir()) continue;

                    final SectionPos sectionPos = SectionPos.of(blockPos);
                    final UploadingContraptionChunk chunk = chunks.computeIfAbsent(sectionPos.asLong(), longPos -> new UploadingContraptionChunk(new int[LevelChunkSection.SECTION_SIZE]));

                    final VoxelNeighborhoodState state = VoxelNeighborhoodState.CORNER;
                    final RapierVoxelColliderData colliderData = this.colliderBakery.getPhysicsDataForBlock(blockState);

                    final int index = (x & 15) + ((z & 15) << 4) + ((y & 15) << 8);

                    final int colliderValue = colliderData == null ? 0 : colliderData.handle() + 1;
                    chunk.data[index] = packBlockState(state, colliderValue);
                }
            }
        }

        if (contraption.sable$shouldCollide()) {
            for (final Long2ObjectMap.Entry<UploadingContraptionChunk> entry : chunks.long2ObjectEntrySet()) {
                final SectionPos sectionPos = SectionPos.of(entry.getLongKey());
                final UploadingContraptionChunk chunk = entry.getValue();
                Rapier3D.addKinematicContraptionChunkSection(this.sceneId, id, sectionPos.x(), sectionPos.y(), sectionPos.z(), chunk.data());
            }
        }

        Rapier3D.setLocalBounds(this.sceneId, id, localBounds.minX, localBounds.minY, localBounds.minZ, localBounds.maxX, localBounds.maxY, localBounds.maxZ);
    }

    /**
     * Removes a kinematic contraption from the scene
     */
    @Override
    public void remove(final KinematicContraption contraption) {
        final TrackedKinematicContraption removed = this.activeContraptions.remove(contraption);

        if (removed == null) {
            return;
        }

        Rapier3D.removeKinematicContraption(this.sceneId, removed.id());
    }

    /**
     * Queries the physics pipeline for the current pose of a {@link SubLevel}.
     */
    @Override
    public Pose3d readPose(final ServerSubLevel body, final Pose3d dest) {
        Rapier3D.getPose(this.sceneId, Rapier3D.getID(body), this.cache);

        dest.position().set(this.cache[0], this.cache[1], this.cache[2]);
        dest.orientation().set(this.cache[3], this.cache[4], this.cache[5], this.cache[6]);

        return dest;
    }

    /**
     * Adds a rope to the physics pipeline
     */
    @Override
    public RopeHandle addRope(final RopePhysicsObject rope) {
        return RapierRopeHandle.create(this.sceneId, rope.getCollisionRadius(), rope.getPoints());
    }

    /**
     * Adds a box to the physics pipeline
     */
    @Override
    public BoxHandle addBox(final BoxPhysicsObject box) {
        return RapierBoxHandle.create(this.sceneId, box.getPose(), box.getHalfExtents(), box.getMass());
    }

    /**
     * Handles the addition of a chunk section to the physics context
     */
    @Override
    public void handleChunkSectionAddition(final LevelChunkSection section, final int x, final int y, final int z, final boolean uploadDataIfGlobal) {
        this.accelerator.clearCache();

        // this means the x coordinate is the fastest changing, then z, then y
        final int[] array = new int[LevelChunkSection.SECTION_SIZE];

        final SectionPos sectionPos = SectionPos.of(x, y, z);

        // if it's only air, all zeros will do. it'll default to empty neighborhood state and 0 (empty) collider ID
        if (!section.hasOnlyAir()) {
            final LevelChunk chunk = this.accelerator.getChunk(x, z);

            for (int bx = 0; bx < 16; bx++) {
                for (int bz = 0; bz < 16; bz++) {
                    for (int by = 0; by < 16; by++) {
                        final BlockPos globalPos = new BlockPos(bx, by, bz).offset(sectionPos.minBlockX(), sectionPos.minBlockY(), sectionPos.minBlockZ());
                        final VoxelNeighborhoodState state = VoxelNeighborhoodState.getState(this.accelerator, globalPos, chunk);
                        final RapierVoxelColliderData colliderData = this.colliderBakery.getPhysicsDataForBlock(this.accelerator.getBlockState(globalPos));

                        final int index = bx + (bz << 4) + (by << 8);

                        final int colliderValue = colliderData == null ? 0 : colliderData.handle() + 1;
                        array[index] = packBlockState(state, colliderValue);
                    }
                }
            }
        }

        final LevelPlot plot = SubLevelContainer.getContainer(this.level).getPlot(x, z);
        final boolean global = plot == null;
        int id = -1;

        if (plot != null && uploadDataIfGlobal) id = Rapier3D.getID(((ServerSubLevel) plot.getSubLevel()));
        Rapier3D.addChunk(this.sceneId, x, y, z, array, global, id);
    }

    /**
     * Handles the removal of a chunk section from the physics context
     */
    @Override
    public void handleChunkSectionRemoval(final int x, final int y, final int z) {
        Rapier3D.removeChunk(this.sceneId, x, y, z, !SubLevelContainer.getContainer(this.level).inBounds(x, z));
    }

    /**
     * Handles the change of a block (from oldState to newState) in a chunk at chunk-relative position x, y, z.
     * Only called server-side.
     *
     * @param x chunk-relative x position
     * @param y chunk-relative y position
     * @param z chunk-relative z position
     */
    @Override
    public void handleBlockChange(final SectionPos sectionPos, final LevelChunkSection chunk, int x, int y, int z, final BlockState oldState, final BlockState newState) {
        x = (sectionPos.x() << 4) + x;
        y = (sectionPos.y() << 4) + y;
        z = (sectionPos.z() << 4) + z;

        final BlockPos globalBlockPos = new BlockPos(x, y, z);

        for (final Direction dir : Direction.values()) {
            final BlockPos pos = globalBlockPos.relative(dir);
            final VoxelNeighborhoodState state = VoxelNeighborhoodState.getState(this.accelerator, pos, null);
            final RapierVoxelColliderData colliderData = this.colliderBakery.getPhysicsDataForBlock(this.level.getBlockState(pos));

            final int colliderValue = colliderData == null ? 0 : colliderData.handle() + 1;
            Rapier3D.changeBlock(this.sceneId, pos.getX(), pos.getY(), pos.getZ(), packBlockState(state, colliderValue));
        }

        // do it for the block without offset
        final VoxelNeighborhoodState state = VoxelNeighborhoodState.getState(this.accelerator, globalBlockPos, null);
        final RapierVoxelColliderData colliderData = this.colliderBakery.getPhysicsDataForBlock(newState);

        final int colliderValue = colliderData == null ? 0 : colliderData.handle() + 1;
        Rapier3D.changeBlock(this.sceneId, x, y, z, packBlockState(state, colliderValue));
    }

    @Override
    public void onStatsChanged(@NotNull final ServerSubLevel serverSubLevel) {
        final BoundingBox3ic plotBounds = serverSubLevel.getPlot().getBoundingBox();

        final int id = Rapier3D.getID(serverSubLevel);

        final Vector3dc centerOfMass = serverSubLevel.getMassTracker().getCenterOfMass();
        if (centerOfMass != null) {
            Rapier3D.setCenterOfMass(this.sceneId, id, centerOfMass.x(), centerOfMass.y(), centerOfMass.z());
            Rapier3D.setMassPropertiesFrom(this.sceneId, id, serverSubLevel.getMassTracker());
        }

        Rapier3D.setLocalBounds(this.sceneId, id, plotBounds.minX(), plotBounds.minY(), plotBounds.minZ(), plotBounds.maxX(), plotBounds.maxY(), plotBounds.maxZ());
    }

    /**
     * Teleports the physics body of a sub-level to a given position.
     *
     * @param body    the physics pipeline body to teleport
     * @param position    the new position to teleport to
     * @param orientation the new orientation to teleport to
     */
    @Override
    public void teleport(final PhysicsPipelineBody body, final Vector3dc position, final Quaterniondc orientation) {
        Rapier3D.teleportObject(this.sceneId, Rapier3D.getID(body), position.x(), position.y(), position.z(), orientation.x(), orientation.y(), orientation.z(), orientation.w());
        if (body instanceof final ServerSubLevel subLevel) {
            subLevel.logicalPose().position().set(position);
            subLevel.logicalPose().orientation().set(orientation);
        }
    }

    /**
     * Adds a force at a given world position to a sub-level containing the position
     *
     * @param body the sub-level to apply the force to
     * @param position the position to apply the force at [m]
     * @param force    the force to apply [N]
     */
    @Override
    public void applyImpulse(final PhysicsPipelineBody body, final Vector3dc position, final Vector3dc force) {
        final Vector3dc centerOfMass = body.getMassTracker().getCenterOfMass();

        Rapier3D.applyForce(this.sceneId, Rapier3D.getID(body), position.x() - centerOfMass.x(), position.y() - centerOfMass.y(), position.z() - centerOfMass.z(), force.x(), force.y(), force.z(), true);
    }

    /**
     * Adds a local force and torque
     *
     * @param body the sub-level to apply the force to
     * @param torque   the local torque to apply [Nm]
     */
    @Override
    public void applyLinearAndAngularImpulse(final PhysicsPipelineBody body, final Vector3dc force, final Vector3dc torque, final boolean wakeUp) {
        Rapier3D.applyForceAndTorque(this.sceneId, Rapier3D.getID(body), force.x(), force.y(), force.z(), torque.x(), torque.y(), torque.z(), wakeUp);
    }

    /**
     * Adds linear and angular velocities to a sub-level
     *
     * @param body        the sub-level to apply the velocities to
     * @param linearVelocity  the linear velocity to apply [m/s]
     * @param angularVelocity the angular velocity to apply [rad/s]
     */
    @Override
    public void addLinearAndAngularVelocity(final PhysicsPipelineBody body, final Vector3dc linearVelocity, final Vector3dc angularVelocity) {
        Rapier3D.addLinearAngularVelocities(this.sceneId, Rapier3D.getID(body), linearVelocity.x(), linearVelocity.y(), linearVelocity.z(), angularVelocity.x(), angularVelocity.y(), angularVelocity.z(), true);
    }

    @Override
    public Vector3d getLinearVelocity(final PhysicsPipelineBody body, final Vector3d dest) {
        Rapier3D.getLinearVelocity(this.sceneId, Rapier3D.getID(body), this.cache);
        return dest.set(this.cache);
    }

    @Override
    public Vector3d getAngularVelocity(final PhysicsPipelineBody body, final Vector3d dest) {
        Rapier3D.getAngularVelocity(this.sceneId, Rapier3D.getID(body), this.cache);
        return dest.set(this.cache);
    }

    /**
     * "Wakes up" a sub-level, indicating environmental or other changes have occurred that should resume physics for idled or sleeping sub-levels.
     *
     * @param body the sub-level to wake up
     */
    @Override
    public void wakeUp(final PhysicsPipelineBody body) {
        Rapier3D.wakeUpObject(this.sceneId, Rapier3D.getID(body));
    }

    /**
     * Adds a constraint to the engine, returning its handle
     *
     * @param sublevelA     the first sub-level to constrain, or null to constrain the second sub-level to the world
     * @param sublevelB     the second sub-level to constrain, or null to constrain the first sub-level to the world
     * @param configuration the configuration of the constraint
     */
    @SuppressWarnings("unchecked")
    @Override
    public <T extends PhysicsConstraintHandle> T addConstraint(@Nullable final ServerSubLevel sublevelA, @Nullable final ServerSubLevel sublevelB, final PhysicsConstraintConfiguration<T> configuration) {
        if (sublevelA == null && sublevelB == null) {
            Sable.LOGGER.error("Cannot add a constraint between the static world and static world");
            return null;
        }

        if (sublevelA == sublevelB) {
            Sable.LOGGER.error("Cannot add a constraint between a sub-level and itself");
            return null;
        }

        if (configuration instanceof final RotaryConstraintConfiguration config) {
            return (T) RapierRotaryConstraintHandle.create(this.level, sublevelA, sublevelB, config);
        }

        if (configuration instanceof final FixedConstraintConfiguration config) {
            return (T) RapierFixedConstraintHandle.create(this.level, sublevelA, sublevelB, config);
        }

        if (configuration instanceof final FreeConstraintConfiguration config) {
            return (T) RapierFreeConstraintHandle.create(this.level, sublevelA, sublevelB, config);
        }

        if (configuration instanceof final GenericConstraintConfiguration config) {
            return (T) RapierGenericConstraintHandle.create(this.level, sublevelA, sublevelB, config);
        }

        Sable.LOGGER.error("Unknown constraint configuration type: {}", configuration.getClass().getName());
        return null;
    }

    /**
     * Updates the config of the physics engine from a data object
     *
     * @param data the data to update from
     */
    @Override
    public void updateConfigFrom(final PhysicsConfigData data) {
        Rapier3D.configFrequencyAndDamping(data.contactSpringFrequency, data.contactSpringDampingRatio);
        Rapier3D.configSolverIterations(data.solverIterations, data.pgsIterations, data.stabilizationIterations);
        Rapier3D.configMinIslandSize(data.minDynamicBodiesPerIsland);
    }

    /**
     * @return the next runtime ID for a collider / sub-level
     */
    @Override
    public int getNextRuntimeID() {
        return Rapier3D.nextBodyID();
    }
}
