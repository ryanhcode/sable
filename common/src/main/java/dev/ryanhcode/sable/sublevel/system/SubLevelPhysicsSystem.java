package dev.ryanhcode.sable.sublevel.system;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.SableConfig;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassTracker;
import dev.ryanhcode.sable.api.physics.object.ArbitraryPhysicsObject;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.mixinterface.plot.SubLevelContainerHolder;
import dev.ryanhcode.sable.mixinterface.toast.SableToastableServer;
import dev.ryanhcode.sable.physics.config.PhysicsConfigData;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.platform.SableEventPublishPlatform;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.system.ticket.PhysicsChunkTicketManager;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Math;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

/**
 * Runs a physics pipeline on sub-levels.
 */
public class SubLevelPhysicsSystem implements SubLevelObserver {

    /**
     * Default capacity in a physics chunk for sub-levels
     */
    public static final int DEFAULT_RESIDENT_CAPACITY = 8;

    /**
     * If tickets are used for queries
     */
    public static final boolean USE_TICKETS_FOR_QUERIES = false;
    /**
     * TODO: Nuke this for threading
     */
    public static SubLevelPhysicsSystem currentlySteppingSystem;
    /**
     * The current physics pipeline.
     */
    private final PhysicsPipeline pipeline;
    /**
     * The level that this system is running on.
     */
    private final ServerLevel level;
    /**
     * Punch cooldowns for every player
     */
    private final Object2IntMap<UUID> punchCooldowns = new Object2IntOpenHashMap<>();
    /**
     * The current physics config
     */
    private final PhysicsConfigData config = new PhysicsConfigData();

    /**
     * The ticket manager for physics chunks
     */
    private final PhysicsChunkTicketManager ticketManager = new PhysicsChunkTicketManager();
    /**
     * For allocation optimization
     */
    private final Pose3d storagePose = new Pose3d();
    /**
     * All arbitrary objects currently loaded
     */
    private final Collection<ArbitraryPhysicsObject> arbitraryObjects = new ObjectOpenHashSet<>();
    /**
     * If physics should be paused.
     */
    private boolean paused;
    /**
     * The substep / physics tick we're currently on
     */
    private int currentSubstep;

    /**
     * Creates a new physics system.
     */
    public SubLevelPhysicsSystem(final ServerLevel level) {
        this.level = level;
        this.pipeline = Sable.createPhysicsPipeline(this.level);
    }

    /**
     * @return the physics system associated with a level, or null if none
     */
    public static SubLevelPhysicsSystem get(final Level level) {
        final SubLevelContainer container = SubLevelContainer.getContainer(level);

        if (container instanceof final ServerSubLevelContainer serverContainer) {
            return serverContainer.physicsSystem();
        }

        return null;
    }

    /**
     * @return the physics system associated with a level, or null if none
     */
    public static @NotNull SubLevelPhysicsSystem require(final Level level) {
        final SubLevelContainer container = SubLevelContainer.getContainer(level);

        if (container instanceof final ServerSubLevelContainer serverContainer) {
            return Objects.requireNonNull(serverContainer.physicsSystem());
        }

        throw new IllegalArgumentException("Sub-level container not found");
    }

    public static SubLevelPhysicsSystem getCurrentlySteppingSystem() {
        if (SubLevelPhysicsSystem.currentlySteppingSystem == null) {
            throw new IllegalStateException("No physics system is currently stepping");
        }
        return SubLevelPhysicsSystem.currentlySteppingSystem;
    }

    /**
     * Initializes the physics pipeline.
     */
    public void initialize() {
        final Vector3d gravity = new Vector3d(DimensionPhysicsData.getGravity(this.level));
        final double universalDrag = DimensionPhysicsData.getUniversalDrag(this.level);

        this.pipeline.init(gravity, universalDrag);
        this.pipeline.updateConfigFrom(this.config);
    }

    /**
     * Signals that the physics config has been updated
     */
    public void onConfigUpdated() {
        this.pipeline.updateConfigFrom(this.config);
    }

    /**
     * Called after a sub-level is added to a {@link SubLevelContainer}.
     *
     * @param subLevel the sub-level that was added
     */
    @Override
    public void onSubLevelAdded(final SubLevel subLevel) {
        if (subLevel instanceof final ServerSubLevel serverSubLevel) {
            this.pipeline.add(serverSubLevel, serverSubLevel.logicalPose());
        } else {
            throw new UnsupportedOperationException("Client sub-levels are not supported by the physics system. How did we end up here?");
        }
    }

    /**
     * Called before a sub-level is removed from a {@link SubLevelContainer}.
     *
     * @param subLevel the sub-level that will be removed
     */
    @Override
    public void onSubLevelRemoved(final SubLevel subLevel, final SubLevelRemovalReason reason) {
        if (subLevel instanceof final ServerSubLevel serverSubLevel) {
            this.pipeline.remove(serverSubLevel);
        } else {
            throw new UnsupportedOperationException("Client sub-levels are not supported by the physics system");
        }
    }

    /**
     * Called every tick for each {@link SubLevelContainer}.
     *
     * @param sidelessContainer the sub-level container that is ticking
     */
    @Override
    public void tick(final SubLevelContainer sidelessContainer) {
        final ServerSubLevelContainer container = (ServerSubLevelContainer) sidelessContainer;
        this.tickPunchCooldowns();

        this.ticketManager.update(this.level, container, this, this.pipeline, 1.0 / 20.0);

        for (final ServerSubLevel subLevel : container.getAllSubLevels()) {
            subLevel.updateLastPose();
            for (final BlockEntitySubLevelActor actor : subLevel.getPlot().getBlockEntityActors()) {
                actor.sable$tick(subLevel);
            }
        }

        this.pipeline.tick();

        if (!this.paused) {
            SubLevelPhysicsSystem.currentlySteppingSystem = this;

            // tick the pipeline physics
            try {
                this.tickPipelinePhysics(container);
            } catch (final Exception e) {
                final CrashReport crashReport = CrashReport.forThrowable(e, "Sable ticking physics");
                final CrashReportCategory crashReportCategory = crashReport.addCategory("Current physics state");
                crashReportCategory.setDetail("Dimension", this.level.dimension());
                throw new ReportedException(crashReport);
            }

            SubLevelPhysicsSystem.currentlySteppingSystem = null;
        }
    }

    private void tickPipelinePhysics(final ServerSubLevelContainer container) {
        this.pipeline.prePhysicsTicks();

        for (this.currentSubstep = 0; this.currentSubstep < this.config.substepsPerTick; this.currentSubstep++) {
            final double substepTimeStep = 1.0 / 20.0 / this.config.substepsPerTick;

            for (final ServerSubLevel subLevel : container.getAllSubLevels()) {
                if (subLevel.isRemoved()) continue;
                subLevel.prePhysicsTickBegin();
            }

            for (final ServerSubLevel subLevel : container.getAllSubLevels()) {
                if (subLevel.isRemoved()) continue;
                subLevel.updateMergedMassData((float) this.getPartialPhysicsTick());
            }

            for (final ServerSubLevel subLevel : container.getAllSubLevels()) {
                if (subLevel.isRemoved()) continue;
                subLevel.prePhysicsTick(this, this.getPhysicsHandle(subLevel), substepTimeStep);
            }

            SableEventPublishPlatform.INSTANCE.prePhysicsTick(this, substepTimeStep);

            for (final ServerSubLevel subLevel : container.getAllSubLevels()) {
                if (subLevel.isRemoved()) continue;
                subLevel.applyQueuedForces(this, this.getPhysicsHandle(subLevel), substepTimeStep);
            }

            this.pipeline.physicsTick(substepTimeStep);

            // if any blocks were modified due to, say, fragile blocks breaking
            // sub-levels could have been removed during the physics tick
            // we must therefore process removals every physics tick
            container.processSubLevelRemovals();
            this.updateAllPoses(container);

            SableEventPublishPlatform.INSTANCE.postPhysicsTick(this, substepTimeStep);
        }

        this.pipeline.postPhysicsTicks();
        this.currentSubstep = this.config.substepsPerTick;
    }

    private void updateAllPoses(final ServerSubLevelContainer container) {
        for (final ServerSubLevel subLevel : container.getAllSubLevels()) {
            if (subLevel.isRemoved()) continue;

            this.updatePose(subLevel);
        }
    }

    /**
     * Updates the pose of a {@link ServerSubLevel} from the current {@link PhysicsPipeline}, updating relevant data.
     *
     * @param serverSubLevel the sub-level to update
     */
    public void updatePose(final ServerSubLevel serverSubLevel) {
        this.pipeline.readPose(serverSubLevel, this.storagePose);

        {
            final Vector3d position = this.storagePose.position();
            final Quaterniond orientation = this.storagePose.orientation();

            if (Double.isNaN(position.x) ||
                    Double.isNaN(position.y) ||
                    Double.isNaN(position.z) ||
                    Double.isNaN(orientation.x) ||
                    Double.isNaN(orientation.y) ||
                    Double.isNaN(orientation.z) ||
                    Double.isNaN(orientation.w)) {
                Sable.LOGGER.info("Invalid position {} or orientation {} received for sub-level {} from pipeline.", this.storagePose.position(), this.storagePose.orientation(), serverSubLevel);
                if (!this.recoverSubLevel(serverSubLevel)) {
                    return;
                }
                this.pipeline.readPose(serverSubLevel, this.storagePose);
            }
        }

        final Pose3d logicalPose = serverSubLevel.logicalPose();
        logicalPose.position().set(this.storagePose.position());
        logicalPose.orientation().set(this.storagePose.orientation());

        // Update latest velocities
        logicalPose.position().sub(serverSubLevel.lastPose().position(), serverSubLevel.latestLinearVelocity);

        final Quaterniond difference = logicalPose.orientation().difference(serverSubLevel.lastPose().orientation(), new Quaterniond()).conjugate();
        final Vector3d angularVelocity = serverSubLevel.latestAngularVelocity.set(difference.x, difference.y, difference.z);
        if (angularVelocity.lengthSquared() <= 1E-15)
            angularVelocity.mul(2.0 / difference.w);
        else {
            angularVelocity.normalize().mul(2.0 * Math.safeAcos(difference.w));
        }

        // [m/t] to [m/s]
        serverSubLevel.latestLinearVelocity.mul(20.0);
        serverSubLevel.latestAngularVelocity.mul(20.0);
    }

    /**
     * Attempts to recover a sub-level that the pipeline messed up the state for (ex. NaNs)
     * Will remove and re-add it to the pipeline.
     *
     * @param serverSubLevel the sub-level to recover
     */
    public boolean recoverSubLevel(final ServerSubLevel serverSubLevel) {
        Sable.LOGGER.info("Attempting to recover physics state for sub-level {}. Removing and re-adding from pipeline.", serverSubLevel);

        final MinecraftServer server = this.level.getServer();
        if (server instanceof final SableToastableServer toastable) {
            toastable.sable$reportSubLevelPhysicsFailure(serverSubLevel);
        }

        // The sub-level has NaN'ed!
        // We need to remove it and re-add from the physics world.
        this.pipeline.remove(serverSubLevel);
        this.pipeline.add(serverSubLevel, serverSubLevel.logicalPose());

        if (serverSubLevel.getMassTracker().getCenterOfMass() == null) {
            // there's no center of mass for the body, which means it's effectively removed
            Sable.LOGGER.info("Sub-level recovery added sub-level to pipeline, but center of mass is null. Aborting and removing sub-level.");
            SubLevelContainer.getContainer(this.level).removeSubLevel(serverSubLevel, SubLevelRemovalReason.REMOVED);
            return false;
        }

        final ServerLevelPlot plot = serverSubLevel.getPlot();

        for (final PlotChunkHolder holder : plot.getLoadedChunks()) {
            final LevelChunk chunk = holder.getChunk();
            final ChunkPos global = chunk.getPos();

            final LevelChunkSection[] levelChunkSections = chunk.getSections();
            for (int i = 0; i < chunk.getSectionsCount(); i++) {
                final LevelChunkSection section = levelChunkSections[i];

                if (!section.hasOnlyAir()) {
                    final int sectionY = chunk.getSectionYFromSectionIndex(i);
                    this.pipeline.handleChunkSectionAddition(section, global.x, sectionY, global.z, true);
                }
            }
        }

        return true;
    }

    private void tickPunchCooldowns() {
        // Decrement punch cooldowns
        final ObjectIterator<Object2IntMap.Entry<UUID>> punchCooldownIter = this.punchCooldowns.object2IntEntrySet().iterator();

        while (punchCooldownIter.hasNext()) {
            final Object2IntMap.Entry<UUID> entry = punchCooldownIter.next();
            final int cooldown = entry.getIntValue() - 1;

            if (cooldown <= 0) {
                punchCooldownIter.remove();
            } else {
                entry.setValue(cooldown);
            }
        }
    }

    public boolean tryPunch(final UUID player, final int cooldownAttempt) {
        final int cooldown = this.punchCooldowns.getOrDefault(player, 0);

        if (cooldown > 0) {
            return false;
        }

        final int newCooldown = Math.max(SableConfig.SUB_LEVEL_PUNCH_COOLDOWN_TICKS.getAsInt(), cooldownAttempt);
        this.punchCooldowns.put(player, newCooldown);
        return true;
    }

    /**
     * @return the physics pipeline
     */
    public PhysicsPipeline getPipeline() {
        return this.pipeline;
    }

    /**
     * Gets a physics handle for a sub-level
     *
     * @param subLevel the sub-level to get the handle for
     * @return the physics handle for the sub-level
     */
    public RigidBodyHandle getPhysicsHandle(@NotNull final ServerSubLevel subLevel) {
        return new RigidBodyHandle(Objects.requireNonNull(subLevel), this);
    }

    /**
     * Handles a block change in the world.
     *
     * @param sectionPos the section position
     * @param section    the chunk section
     * @param localX     the local x position
     * @param localY     the local y position
     * @param localZ     the local z position
     * @param oldState   the old block state
     * @param newState   the new block state
     */
    public void handleBlockChange(final SectionPos sectionPos, final LevelChunkSection section, final int localX, final int localY, final int localZ, final BlockState oldState, final BlockState newState) {
        final ChunkPos chunk = sectionPos.chunk();
        final LevelPlot plot = ((SubLevelContainerHolder) this.level).sable$getPlotContainer().getPlot(chunk);
        if (plot != null) {
            this.ticketManager.addSectionIfNotTracked(this.level, section, sectionPos, this.pipeline);
        }

        final int x = (sectionPos.x() << SectionPos.SECTION_BITS) + localX;
        final int y = (sectionPos.y() << SectionPos.SECTION_BITS) + localY;
        final int z = (sectionPos.z() << SectionPos.SECTION_BITS) + localZ;
        final SubLevel subLevel = Sable.HELPER.getContaining(this.level, sectionPos);
        final BlockPos globalBlockPos = new BlockPos(x, y, z);

        this.updateMassDataFromBlockChange(subLevel, globalBlockPos, oldState, newState, true);

        this.pipeline.handleBlockChange(sectionPos, section, localX, localY, localZ, oldState, newState);

        this.wakeUpObjectsAt(x, y, z);
    }

    /**
     * Wakes up all sub-levels that intersect with the given block position.
     *
     * @param x the x position
     * @param y the y position
     * @param z the z position
     */
    public void wakeUpObjectsAt(final int x, final int y, final int z) {
        // Wake up intersecting sub-levels
        final BoundingBox3d bounds = new BoundingBox3d(x, y, z, x + 1, y + 1, z + 1);
        bounds.expand(0.1, bounds);
        final Iterable<SubLevel> intersectingSubLevels = Sable.HELPER.getAllIntersecting(this.level, bounds);

        for (final SubLevel intersectingSubLevel : intersectingSubLevels) {
            if (intersectingSubLevel instanceof final ServerSubLevel intersectingServerSubLevel) {
                this.pipeline.wakeUp(intersectingServerSubLevel);
            }
        }

        if (this.arbitraryObjects.isEmpty()) return;
        final BoundingBox3d objectBounds = new BoundingBox3d();
        for (final ArbitraryPhysicsObject object : this.arbitraryObjects) {
            object.getBoundingBox(objectBounds);

            if (objectBounds.intersects(bounds)) {
                object.wakeUp();
            }
        }
    }

    public void updateMassDataFromBlockChange(final SubLevel subLevel, final BlockPos globalBlockPos, final BlockState oldState, final BlockState newState, final boolean notifyPipeline) {
        if ((subLevel instanceof final ServerSubLevel serverSubLevel)) {

            final Vec3 oldInertia = oldState.isAir() ? null : PhysicsBlockPropertyHelper.getInertia(this.level, globalBlockPos, oldState);
            final Vec3 inertia = newState.isAir() ? null : PhysicsBlockPropertyHelper.getInertia(this.level, globalBlockPos, newState);

            final double oldMass = oldState.isAir() ? 0.0 : PhysicsBlockPropertyHelper.getMass(this.level, globalBlockPos, oldState);
            final double mass = newState.isAir() ? 0.0 : PhysicsBlockPropertyHelper.getMass(this.level, globalBlockPos, newState);

            if (mass != oldMass || (newState != oldState && (oldMass != 0.0 || mass != 0.0)) || oldInertia != inertia) {
                final Level level = subLevel.getLevel();
                final MassTracker massTracker = serverSubLevel.getSelfMassTracker();

                if (mass != 0.0) massTracker.addBlockMass(level, newState, globalBlockPos, mass, inertia);
                if (oldMass != 0.0) massTracker.addBlockMass(level, oldState, globalBlockPos, -oldMass, oldInertia);

                if (!subLevel.isRemoved() && massTracker.isInvalid()) {
                    serverSubLevel.getPlot().destroyAllBlocks();
                    serverSubLevel.markRemoved();
                    return;
                }

                if (notifyPipeline) {
                    serverSubLevel.updateMergedMassData((float) this.getPartialPhysicsTick());
                    this.pipeline.onStatsChanged(serverSubLevel);
                }
            }
        }
    }

    /**
     * Returns the current fraction between the last physics tick and the next one.
     *
     * @return 0.0 - 1.0, 0.0 at beginning of the physics tick and (substeps - 1) / (substeps) at the last substep in the tick
     */
    public double getPartialPhysicsTick() {
        return (double) (this.currentSubstep + 1) / this.config.substepsPerTick;
    }

    /**
     * @return whether the physics system is currently paused or not
     */
    public boolean getPaused() {
        return this.paused;
    }

    /**
     * @param paused whether the physics system should be paused or not
     */
    public void setPaused(final boolean paused) {
        this.paused = paused;
    }

    /**
     * Queries all intersecting sub-levels with the given bounds.
     *
     * @param bounds the bounds to query
     * @return the intersecting sub-levels
     */
    public Iterable<SubLevel> queryIntersecting(final BoundingBox3dc bounds) {
        if (USE_TICKETS_FOR_QUERIES) {
            return this.ticketManager.queryIntersecting(bounds);
        } else {
            // Brute force check all of them
            final SubLevelContainer container = SubLevelContainer.getContainer(this.level);
            assert container != null : "No sub-level container found for level that somehow also has a physics system";

            return container.queryIntersecting(bounds);
        }
    }

    /**
     * @return the current physics config
     */
    public PhysicsConfigData getConfig() {
        return this.config;
    }

    public Iterable<ArbitraryPhysicsObject> getArbitraryObjects() {
        return this.arbitraryObjects;
    }

    public ServerLevel getLevel() {
        return this.level;
    }

    public PhysicsChunkTicketManager getTicketManager() {
        return this.ticketManager;
    }

    /**
     * Adds an arbitrary physics object to the system
     */
    public void addObject(final ArbitraryPhysicsObject object) {
        if (this.arbitraryObjects.add(object)) {
            object.onAddition(this);
        }
    }

    /**
     * Removes an arbitrary physics object from the system
     */
    public void removeObject(final ArbitraryPhysicsObject object) {
        if (this.arbitraryObjects.remove(object)) {
            object.onRemoved();
        }
    }

    /**
     * @return the next runtime ID from the physics pipeline
     */
    public int getNextRuntimeID() {
        return this.pipeline.getNextRuntimeID();
    }
}
