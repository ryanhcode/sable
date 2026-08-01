package dev.ryanhcode.sable.physics.impl.rapier;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.constraint.*;
import dev.ryanhcode.sable.api.physics.mass.MassTracker;
import dev.ryanhcode.sable.api.physics.object.box.BoxHandle;
import dev.ryanhcode.sable.api.physics.object.box.BoxPhysicsObject;
import dev.ryanhcode.sable.api.physics.object.rope.RopeHandle;
import dev.ryanhcode.sable.api.physics.object.rope.RopePhysicsObject;
import dev.ryanhcode.sable.api.sublevel.KinematicContraption;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
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
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceList;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
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

    private final ServerLevel level;
    private final LevelAccelerator accelerator;
    private final RapierVoxelColliderBakery colliderBakery;
    private final Int2ObjectMap<ServerSubLevel> activeSubLevels = new Int2ObjectArrayMap<>();
    private final Object2ObjectMap<KinematicContraption, TrackedKinematicContraption> activeContraptions = new Object2ObjectOpenHashMap<>();
    private final Long2LongOpenHashMap recentCollisions = new Long2LongOpenHashMap();
    private final ReferenceList<PhysicsPipelineBody> queuedWakeUps = new ReferenceArrayList<>();
    private final double[] poseCache;
    private RapierPhysicsScene scene;

    public RapierPhysicsPipeline(final ServerLevel level) {
        this.level = level;
        this.accelerator = new LevelAccelerator(level);
        this.colliderBakery = new RapierVoxelColliderBakery(this.accelerator);
        this.recentCollisions.defaultReturnValue(-1);
        this.poseCache = new double[7];
    }

    /**
     * Packs a voxel collider ID and neighborhood state into an integer the rapier companion library will re-interpret as a block-state.
     * @return the packed block state
     */
    private static int packBlockState(final VoxelNeighborhoodState state, final int colliderID) {
        return ((int) state.byteRepresentation()) | (colliderID << 16);
    }

    protected long getSceneHandle() {
        if (this.scene == null) {
            throw new IllegalStateException("Physics scene is not initialized");
        }
        return this.scene.handle();
    }

    /**
     * Initializes the physics pipeline.
     *
     * @param gravity the gravity vector
     * @param universalDrag the universal drag to apply to all bodies
     */
    @Override
    public void init(@Nullable final Vector3dc gravity, final double universalDrag) {
        try {
            Sable.LOGGER.info("[SableDebug] Initializing Rapier physics pipeline with gravity=({}, {}, {}), drag={}", gravity.x(), gravity.y(), gravity.z(), universalDrag);
            this.scene = new RapierPhysicsScene(Rapier3D.initialize(gravity.x(), gravity.y(), gravity.z(), universalDrag));
            Sable.LOGGER.info("[SableDebug] Rapier physics pipeline initialized successfully, scene handle={}", this.scene.handle());
        } catch (final UnsatisfiedLinkError e) {
            Sable.LOGGER.error("Sable has failed to link with the natives for its Rapier pipeline. Please report with system details to " + Sable.ISSUE_TRACKER_URL, e);
            final CrashReport crashReport = CrashReport.forThrowable(e.getCause(), "Sable linking with Rapier natives");
            final CrashReportCategory category = crashReport.addCategory("Natives");
            category.setDetail("Name", Rapier3D.NATIVE_NAME);
            throw new ReportedException(crashReport);
        }
    }

    /**
     * Disposes all resources used by the physics pipeline.
     */
    @Override
    public void dispose() {
        if (this.scene != null) {
            Rapier3D.dispose(this.scene.handle());
            this.scene = null;
        }
    }

    /**
     * Runs a physics tick with a time step of {@code 1.0 / 20.0} seconds.
     */
    @Override
    public void prePhysicsTicks() {
        final double timeStep = 1.0 / 20.0;
        try {
            Sable.LOGGER.debug("[SableDebug] prePhysicsTicks: calling tick(handle={}, timeStep={})", this.scene.handle(), timeStep);
            Rapier3D.tick(this.scene.handle(), timeStep);
        } catch (final Exception e) {
            Sable.LOGGER.error("[SableDebug] EXCEPTION in prePhysicsTicks tick() call", e);
            throw e;
        }
    }

    /**
     * Runs a physics substep with a time step of {@code 1.0 / 20.0 / substeps} seconds.
     *
     * @param timeStep the time step of this physics substep [s]
     */
    @Override
    public void physicsTick(final double timeStep) {
        this.updateContraptionPoses();
        try {
            Sable.LOGGER.debug("[SableDebug] physicsTick: calling step(handle={}, timeStep={})", this.scene.handle(), timeStep);
            Rapier3D.step(this.scene.handle(), timeStep);
        } catch (final Exception e) {
            Sable.LOGGER.error("[SableDebug] EXCEPTION in physicsTick step() call", e);
            throw e;
        }

        for (final PhysicsPipelineBody queuedWakeUp : this.queuedWakeUps) {
            if (queuedWakeUp.isRemoved()) {
                continue;
            }

            final int wakeId = Rapier3D.getID(queuedWakeUp);
            Sable.LOGGER.debug("[SableDebug] physicsTick: waking up body id={}", wakeId);
            try {
                Rapier3D.wakeUpObject(this.scene.handle(), wakeId);
            } catch (final Exception e) {
                Sable.LOGGER.error("[SableDebug] EXCEPTION waking up body id={}", wakeId, e);
            }
        }

        this.queuedWakeUps.clear();
    }

    /**
     * Called after all physics substeps have been run, to finalize the physics tick.
     */
    @Override
    public void postPhysicsTicks() {
        this.processCollisionEffects();
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
        this.assertBodyValid(subLevel);
        final Vector3dc pos = pose.position();
        final Quaterniondc rot = pose.orientation();

        final int id = Rapier3D.getID(subLevel);
        Sable.LOGGER.info("[SableDebug] add(SubLevel): id={}, pos=({}, {}, {}), rot=({}, {}, {}, {})", id, pos.x(), pos.y(), pos.z(), rot.x(), rot.y(), rot.z(), rot.w());
        try {
            Rapier3D.createSubLevel(this.scene.handle(), id, new double[]{pos.x(), pos.y(), pos.z(), rot.x(), rot.y(), rot.z(), rot.w()});
        } catch (final Exception e) {
            Sable.LOGGER.error("[SableDebug] EXCEPTION in add(SubLevel) createSubLevel, id={}", id, e);
            throw e;
        }

        subLevel.updateMergedMassData(1.0f);
        final Vector3dc centerOfMass = subLevel.getMassTracker().getCenterOfMass();

        if (centerOfMass != null) {
            subLevel.logicalPose().rotationPoint().set(centerOfMass);
            Sable.LOGGER.info("[SableDebug] add(SubLevel): id={}, centerOfMass=({}, {}, {})", id, centerOfMass.x(), centerOfMass.y(), centerOfMass.z());
            this.onStatsChanged(subLevel);
        }

        this.activeSubLevels.put(Rapier3D.getID(subLevel), subLevel);
        Sable.LOGGER.info("[SableDebug] add(SubLevel): id={} added successfully, activeSubLevels.size={}", id, this.activeSubLevels.size());
    }

    /**
     * Removes a {@link SubLevel} from the physics pipeline.
     */
    @Override
    public void remove(final ServerSubLevel subLevel) {
        final int id = Rapier3D.getID(subLevel);
        Sable.LOGGER.info("[SableDebug] remove(SubLevel): id={}", id);
        try {
            Rapier3D.removeSubLevel(this.scene.handle(), id);
        } catch (final Exception e) {
            Sable.LOGGER.error("[SableDebug] EXCEPTION in remove(SubLevel), id={}", id, e);
            throw e;
        }
        this.activeSubLevels.remove(id);
        Sable.LOGGER.info("[SableDebug] remove(SubLevel): id={} removed, activeSubLevels.size={}", id, this.activeSubLevels.size());
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

        Rapier3D.createKinematicContraption(this.scene.handle(), mountId, id, pose);

        // collect chunks
        record UploadingContraptionChunk(int[] data) {
        }
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
                Rapier3D.addKinematicContraptionChunkSection(this.scene.handle(), id, sectionPos.x(), sectionPos.y(), sectionPos.z(), chunk.data());
            }
        }

        this.updateContraptionPose(contraption, 1.0f);
        Rapier3D.setLocalBounds(this.scene.handle(), id, localBounds.minX, localBounds.minY, localBounds.minZ, localBounds.maxX, localBounds.maxY, localBounds.maxZ);
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

        Rapier3D.removeKinematicContraption(this.scene.handle(), removed.id());
    }

    /**
     * Queries the physics pipeline for the current pose of a {@link SubLevel}.
     */
    @Override
    public Pose3d readPose(final ServerSubLevel subLevel, final Pose3d dest) {
        this.assertBodyValid(subLevel);
        final int id = Rapier3D.getID(subLevel);
        Sable.LOGGER.debug("[SableDebug] readPose: id={}", id);
        try {
            Rapier3D.getPose(this.scene.handle(), id, this.poseCache);
        } catch (final Exception e) {
            Sable.LOGGER.error("[SableDebug] EXCEPTION in readPose getPose(), id={}", id, e);
            throw e;
        }

        dest.position().set(this.poseCache[0], this.poseCache[1], this.poseCache[2]);
        dest.orientation().set(this.poseCache[3], this.poseCache[4], this.poseCache[5], this.poseCache[6]);

        Sable.LOGGER.debug("[SableDebug] readPose: id={}, pos=({}, {}, {}), rot=({}, {}, {}, {})", id, this.poseCache[0], this.poseCache[1], this.poseCache[2], this.poseCache[3], this.poseCache[4], this.poseCache[5], this.poseCache[6]);
        return dest;
    }

    /**
     * Adds a rope to the physics pipeline
     */
    @Override
    public RopeHandle addRope(final RopePhysicsObject rope) {
        return RapierRopeHandle.create(this.scene.handle(), rope.getCollisionRadius(), rope.getPoints());
    }

    /**
     * Adds a box to the physics pipeline
     */
    @Override
    public BoxHandle addBox(final BoxPhysicsObject box) {
        return RapierBoxHandle.create(this.scene.handle(), box.getPose(), box.getHalfExtents(), box.getMass());
    }

    /**
     * Handles the addition of a chunk section to the physics context
     */
    @Override
    public void handleChunkSectionAddition(final LevelChunkSection section, final int x, final int y, final int z, final boolean uploadDataIfGlobal) {
        this.accelerator.clearCache();
        Sable.LOGGER.debug("[SableDebug] handleChunkSectionAddition: section=({}, {}, {}), uploadDataIfGlobal={}, hasOnlyAir={}", x, y, z, uploadDataIfGlobal, section.hasOnlyAir());

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
        try {
            Rapier3D.addChunk(this.scene.handle(), x, y, z, array, global, id);
        } catch (final Exception e) {
            Sable.LOGGER.error("[SableDebug] EXCEPTION in handleChunkSectionAddition addChunk({}, {}, {}), global={}, id={}", x, y, z, global, id, e);
            throw e;
        }
    }

    /**
     * Handles the removal of a chunk section from the physics context
     */
    @Override
    public void handleChunkSectionRemoval(final int x, final int y, final int z) {
        Sable.LOGGER.debug("[SableDebug] handleChunkSectionRemoval: ({}, {}, {})", x, y, z);
        try {
            Rapier3D.removeChunk(this.scene.handle(), x, y, z, !SubLevelContainer.getContainer(this.level).inBounds(x, z));
        } catch (final Exception e) {
            Sable.LOGGER.error("[SableDebug] EXCEPTION in handleChunkSectionRemoval removeChunk({}, {}, {})", x, y, z, e);
            throw e;
        }
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

        Sable.LOGGER.debug("[SableDebug] handleBlockChange: pos=({}, {}, {}), oldState={}, newState={}", x, y, z, oldState, newState);

        final BlockPos globalBlockPos = new BlockPos(x, y, z);

        for (final Direction dir : Direction.values()) {
            final BlockPos pos = globalBlockPos.relative(dir);
            final VoxelNeighborhoodState state = VoxelNeighborhoodState.getState(this.accelerator, pos, null);
            final RapierVoxelColliderData colliderData = this.colliderBakery.getPhysicsDataForBlock(this.level.getBlockState(pos));

            final int colliderValue = colliderData == null ? 0 : colliderData.handle() + 1;
            try {
                Rapier3D.changeBlock(this.scene.handle(), pos.getX(), pos.getY(), pos.getZ(), packBlockState(state, colliderValue));
            } catch (final Exception e) {
                Sable.LOGGER.error("[SableDebug] EXCEPTION in handleBlockChange changeBlock neighbor ({}, {}, {}), colliderValue={}", pos.getX(), pos.getY(), pos.getZ(), colliderValue, e);
                throw e;
            }
        }

        // do it for the block without offset
        final VoxelNeighborhoodState state = VoxelNeighborhoodState.getState(this.accelerator, globalBlockPos, null);
        final RapierVoxelColliderData colliderData = this.colliderBakery.getPhysicsDataForBlock(newState);

        final int colliderValue = colliderData == null ? 0 : colliderData.handle() + 1;
        try {
            Rapier3D.changeBlock(this.scene.handle(), x, y, z, packBlockState(state, colliderValue));
        } catch (final Exception e) {
            Sable.LOGGER.error("[SableDebug] EXCEPTION in handleBlockChange changeBlock self ({}, {}, {}), colliderValue={}", x, y, z, colliderValue, e);
            throw e;
        }
    }

    @Override
    public void onStatsChanged(@NotNull final ServerSubLevel subLevel) {
        this.assertBodyValid(subLevel);

        final BoundingBox3ic plotBounds = subLevel.getPlot().getBoundingBox();
        final int id = Rapier3D.getID(subLevel);

        final Vector3dc centerOfMass = subLevel.getMassTracker().getCenterOfMass();
        if (centerOfMass != null) {
            Sable.LOGGER.debug("[SableDebug] onStatsChanged: id={}, centerOfMass=({}, {}, {}), mass={}", id, centerOfMass.x(), centerOfMass.y(), centerOfMass.z(), subLevel.getMassTracker().getMass());
            try {
                Rapier3D.setCenterOfMass(this.scene.handle(), id, centerOfMass.x(), centerOfMass.y(), centerOfMass.z());
                Rapier3D.setMassPropertiesFrom(this.scene.handle(), id, subLevel.getMassTracker());
            } catch (final Exception e) {
                Sable.LOGGER.error("[SableDebug] EXCEPTION in onStatsChanged setCenterOfMass/setMassProperties, id={}", id, e);
                throw e;
            }
        }

        Sable.LOGGER.debug("[SableDebug] onStatsChanged: id={}, bounds=({},{},{} -> {},{},{})", id, plotBounds.minX(), plotBounds.minY(), plotBounds.minZ(), plotBounds.maxX(), plotBounds.maxY(), plotBounds.maxZ());
        try {
            Rapier3D.setLocalBounds(this.scene.handle(), id, plotBounds.minX(), plotBounds.minY(), plotBounds.minZ(), plotBounds.maxX(), plotBounds.maxY(), plotBounds.maxZ());
        } catch (final Exception e) {
            Sable.LOGGER.error("[SableDebug] EXCEPTION in onStatsChanged setLocalBounds, id={}", id, e);
            throw e;
        }
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
        this.assertBodyValid(body);

        final int id = Rapier3D.getID(body);
        Sable.LOGGER.debug("[SableDebug] teleport: id={}, pos=({}, {}, {}), rot=({}, {}, {}, {})", id, position.x(), position.y(), position.z(), orientation.x(), orientation.y(), orientation.z(), orientation.w());
        try {
            Rapier3D.teleportObject(this.scene.handle(), id, position.x(), position.y(), position.z(), orientation.x(), orientation.y(), orientation.z(), orientation.w());
        } catch (final Exception e) {
            Sable.LOGGER.error("[SableDebug] EXCEPTION in teleport, id={}", id, e);
            throw e;
        }
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
        this.assertBodyValid(body);

        final int id = Rapier3D.getID(body);
        final Vector3dc centerOfMass = body.getMassTracker().getCenterOfMass();
        Sable.LOGGER.debug("[SableDebug] applyImpulse: id={}, relPos=({}, {}, {}), force=({}, {}, {})", id, position.x() - centerOfMass.x(), position.y() - centerOfMass.y(), position.z() - centerOfMass.z(), force.x(), force.y(), force.z());
        try {
            Rapier3D.applyForce(this.scene.handle(), id, position.x() - centerOfMass.x(), position.y() - centerOfMass.y(), position.z() - centerOfMass.z(), force.x(), force.y(), force.z(), true);
        } catch (final Exception e) {
            Sable.LOGGER.error("[SableDebug] EXCEPTION in applyImpulse, id={}", id, e);
            throw e;
        }
    }

    /**
     * Adds a local force and torque
     *
     * @param body the sub-level to apply the force to
     * @param torque   the local torque to apply [Nm]
     */
    @Override
    public void applyLinearAndAngularImpulse(final PhysicsPipelineBody body, final Vector3dc force, final Vector3dc torque, final boolean wakeUp) {
        this.assertBodyValid(body);
        final int id = Rapier3D.getID(body);
        Sable.LOGGER.debug("[SableDebug] applyLinearAndAngularImpulse: id={}, force=({}, {}, {}), torque=({}, {}, {}), wakeUp={}", id, force.x(), force.y(), force.z(), torque.x(), torque.y(), torque.z(), wakeUp);
        try {
            Rapier3D.applyForceAndTorque(this.scene.handle(), id, force.x(), force.y(), force.z(), torque.x(), torque.y(), torque.z(), wakeUp);
        } catch (final Exception e) {
            Sable.LOGGER.error("[SableDebug] EXCEPTION in applyLinearAndAngularImpulse, id={}", id, e);
            throw e;
        }
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
        this.assertBodyValid(body);
        Rapier3D.addLinearAngularVelocities(this.scene.handle(), Rapier3D.getID(body), linearVelocity.x(), linearVelocity.y(), linearVelocity.z(), angularVelocity.x(), angularVelocity.y(), angularVelocity.z(), true);
    }

    @Override
    public Vector3d getLinearVelocity(final PhysicsPipelineBody body, final Vector3d dest) {
        this.assertBodyValid(body);
        Rapier3D.getLinearVelocity(this.scene.handle(), Rapier3D.getID(body), this.poseCache);
        return dest.set(this.poseCache);
    }

    @Override
    public Vector3d getAngularVelocity(final PhysicsPipelineBody body, final Vector3d dest) {
        this.assertBodyValid(body);
        Rapier3D.getAngularVelocity(this.scene.handle(), Rapier3D.getID(body), this.poseCache);
        return dest.set(this.poseCache);
    }

    /**
     * "Wakes up" a sub-level, indicating environmental or other changes have occurred that should resume physics for idled or sleeping sub-levels.
     *
     * @param body the sub-level to wake up
     */
    @Override
    public void wakeUp(final PhysicsPipelineBody body) {
        this.assertBodyValid(body);

        if (!SubLevelPhysicsSystem.IN_PHYSICS_STEP) {
            Rapier3D.wakeUpObject(this.scene.handle(), Rapier3D.getID(body));
        } else {
            this.queuedWakeUps.add(body);
        }
    }

    /**
     * Adds a constraint to the engine, returning its handle
     *
     * @param bodyA     the first sub-level to constrain, or null to constrain the second sub-level to the world
     * @param bodyB     the second sub-level to constrain, or null to constrain the first sub-level to the world
     * @param configuration the configuration of the constraint
     */
    @SuppressWarnings("unchecked")
    @Override
    @Nullable
    public <T extends PhysicsConstraintHandle> T addConstraint(@Nullable final PhysicsPipelineBody bodyA, @Nullable final PhysicsPipelineBody bodyB, @NotNull final PhysicsConstraintConfiguration<T> configuration) {
        if (bodyA == null && bodyB == null) {
            throw new IllegalArgumentException("Cannot add a constraint between the static world and static world");
        }

        if (bodyA == bodyB) {
            throw new IllegalArgumentException("Cannot add a constraint between a body and itself");
        }

        try {
            configuration.validate(ServerSubLevelContainer.getContainer(this.level), bodyA, bodyB);
        } catch (final Exception e) {
            throw new IllegalArgumentException("Constraint validation failed", e);
        }

        final T constraint = switch (configuration) {
            case final RotaryConstraintConfiguration config ->
                    (T) RapierRotaryConstraintHandle.create(this.level, bodyA, bodyB, config);
            case final FixedConstraintConfiguration config ->
                    (T) RapierFixedConstraintHandle.create(this.level, bodyA, bodyB, config);
            case final FreeConstraintConfiguration config ->
                    (T) RapierFreeConstraintHandle.create(this.level, bodyA, bodyB, config);
            case final GenericConstraintConfiguration config ->
                    (T) RapierGenericConstraintHandle.create(this.level, bodyA, bodyB, config);
        };

        if (!constraint.isValid()) {
            return null;
        }

        return constraint;
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

    private void assertBodyValid(final PhysicsPipelineBody body) {
        if (body.isRemoved()) {
            throw new RuntimeException("Body has been removed");
        }
    }

    private void updateContraptionPoses() {
        final SubLevelPhysicsSystem system = SubLevelPhysicsSystem.require(this.level);
        final double partialPhysicsTick = system.getPartialPhysicsTick();

        for (final KinematicContraption contraption : this.activeContraptions.keySet()) {
            this.updateContraptionPose(contraption, partialPhysicsTick);
        }
    }

    private void updateContraptionPose(final KinematicContraption contraption, final double partialPhysicsTick) {
        final TrackedKinematicContraption trackedContraption = this.activeContraptions.get(contraption);

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
            Rapier3D.setKinematicContraptionTransform(this.scene.handle(), trackedContraption.id(), centerOfMassArray, poseArray, velocityArray);

            trackedContraption.lastUploadedPosition().set(pos);
            trackedContraption.lastUploadedLinVel().set(linVel);
            trackedContraption.lastUploadedAngVel().set(angVel);
            trackedContraption.lastUploadedOrientation().set(rot);
        }
    }

    private void processCollisionEffects() {
        this.recentCollisions.long2LongEntrySet().removeIf(entry -> this.level.getGameTime() - entry.getLongValue() > 2);

        final Vector3d localPointA = new Vector3d();
        final Vector3d localPointB = new Vector3d();
        final Vector3d localNormalA = new Vector3d();
        final Vector3d localNormalB = new Vector3d();

        final Vector3d globalPointA = new Vector3d();
        final Vector3d globalPointB = new Vector3d();

        final double[] collisions = Rapier3D.clearCollisions(this.scene.handle());

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

    private record TrackedKinematicContraption(Vector3d lastUploadedPosition, Quaterniond lastUploadedOrientation,
                                               Vector3d lastUploadedLinVel, Vector3d lastUploadedAngVel, int id) {
    }

}
