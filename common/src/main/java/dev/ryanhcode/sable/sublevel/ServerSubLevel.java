package dev.ryanhcode.sable.sublevel;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.SableConfig;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.block.BlockSubLevelLiftProvider;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.force.ForceGroup;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.api.physics.mass.MassTracker;
import dev.ryanhcode.sable.api.physics.mass.MergedMassTracker;
import dev.ryanhcode.sable.api.sublevel.KinematicContraption;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundChangeSubLevelNamePacket;
import dev.ryanhcode.sable.physics.ReactionWheelManager;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.physics.floating_block.FloatingBlockController;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import dev.ryanhcode.sable.sublevel.plot.heat.SubLevelHeatMapManager;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.ryanhcode.sable.util.LevelAccelerator;
import foundry.veil.api.network.VeilPacketManager;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.*;

/**
 * A sub-level in a {@link ServerLevel}
 */
public class ServerSubLevel extends SubLevel implements PhysicsPipelineBody {

    /**
     * The latest linear velocity of this sub-level
     */
    @ApiStatus.Internal
    public final Vector3d latestLinearVelocity = new Vector3d();

    /**
     * The latest angular velocity of this sub-level
     */
    @ApiStatus.Internal
    public final Vector3d latestAngularVelocity = new Vector3d();

    /**
     * All players currently tracking this sub-level
     */
    private final Set<UUID> trackingPlayers = new ObjectOpenHashSet<>();

    /**
     * The last pose sent out to players
     */
    private final Pose3d lastNetworkedPose = new Pose3d();

    /**
     * The last plot bounding box sent out to players
     */
    private final BoundingBox3i lastNetworkedBoundingBox = new BoundingBox3i();

    /**
     * The runtime ID of this sub-level
     */
    private final int runtimeId;

    /**
     * The manager for the heatmap of this sub-level
     */
    private final SubLevelHeatMapManager heatMapManager = new SubLevelHeatMapManager(this);
    /**
     * The floating block controller for this sub-level
     */
    private final FloatingBlockController floatingBlockController = new FloatingBlockController(this);

    private final ReactionWheelManager reactionWheelManager = new ReactionWheelManager(this);

    /**
     * The Sublevels jointed to this sublevel
     * This does not include the sublevel of "this" or its child sublevels.
     */
    private final ObjectCollection<ServerSubLevel> jointedSubLevels = new ObjectOpenHashSet<>();

    /**
     * sub-levels that interact directly
     * This also includes the child's jointed sub-level.
     * This includes instances of `this`.
     */
    private final ObjectCollection<ServerSubLevel> interactiveSubLevels = new ObjectOpenHashSet<>();

    /**
     * Nullable lazy map of force group -> force totals
     */
    @Nullable
    private Object2ObjectMap<ForceGroup, QueuedForceGroup> queuedForceGroups = null;
    /**
     * The merged mass tracker for this sub-level (including merged masses from subcontraptions)
     */
    private MergedMassTracker massTracker;
    /**
     * The last stopped status sent out to players (if the sub-level is standing still)
     */
    private boolean lastNetworkedStopped = false;
    /**
     * The sub-level that this was split from, if any.
     */
    @Nullable
    private UUID splitFromSubLevel = null;
    /**
     * The original pose of the sub-level before it was projected out of the containing sub-level.
     */
    @Nullable
    private Pose3d splitFromPose = null;
    /**
     * The last place this sub-level was saved to
     */
    @ApiStatus.Internal
    private GlobalSavedSubLevelPointer lastSerializationPointer = null;
    /**
     * The user data tag, if any exists.
     * This tag will be serialized & stored with the sub-level
     */
    @Nullable
    private CompoundTag userDataTag;
    /**
     * If individual queued forces should be kept track of
     */
    private boolean trackIndividualQueuedForces = false;

    private boolean updateInteractiveSubLevelsNextTick;

    /**
     * Creates a new sub-level with the given parent level and pose.
     *
     * @param level the parent level
     * @param plotX the global plot x coordinate
     * @param plotY the global plot y coordinate
     * @param pose  the initialization pose of the sub-level
     */
    public ServerSubLevel(final ServerLevel level, final int plotX, final int plotY, final Pose3d pose) {
        super(level, plotX, plotY, pose);

        final SubLevelPhysicsSystem physicsSystem = SubLevelPhysicsSystem.get(level);

        assert physicsSystem != null;
        this.runtimeId = physicsSystem.getNextRuntimeID();
        this.updateInteractiveSubLevels();
    }

    /**
     * @return all uuids of players currently tracking this sub-level
     */
    public Collection<UUID> getTrackingPlayers() {
        return this.trackingPlayers;
    }

    /**
     * @return packet sink for all players currently tracking this sub-level
     */
    public VeilPacketManager.PacketSink playerSink() {
        return packet -> {
            for (final UUID uuid : this.trackingPlayers) {
                final ServerPlayer player = (ServerPlayer) this.getLevel().getPlayerByUUID(uuid);
                if (player instanceof ServerPlayer) {
                    player.connection.send(packet);
                }
            }
        };
    }

    /**
     * @return the last pose sent out to players
     */
    public Pose3d lastNetworkedPose() {
        return this.lastNetworkedPose;
    }

    /**
     * @return the last plot bounding box sent out to players
     */
    public BoundingBox3i lastNetworkedBoundingBox() {
        return this.lastNetworkedBoundingBox;
    }

    /**
     * The unique runtime ID of this sub-level
     *
     * @return the runtime ID
     */
    @Override
    public int getRuntimeId() {
        return this.runtimeId;
    }

    /**
     * Creates the plot for this sub-level.
     *
     * @param plotContainer the parent plot container of this sub-level
     * @param plotX         the global plot x coordinate
     * @param plotY         the global plot y coordinate
     * @param logPlotSize   the log_2 of the side length of a plot, in chunks
     * @return a new {@link LevelPlot} instance for this sub-level
     */
    @Override
    protected LevelPlot createPlot(final SubLevelContainer plotContainer, final int plotX, final int plotY, final int logPlotSize) {
        return new ServerLevelPlot(plotContainer, plotX, plotY, plotContainer.getLogPlotSize(), this);
    }

    @Override
    @ApiStatus.Internal
    public void onPlotBoundsChanged() {
        final BoundingBox3ic bounds = this.getPlot().getBoundingBox();

        if (bounds == BoundingBox3i.EMPTY || bounds.volume() <= 0) {
            this.markRemoved();
        }
    }

    /**
     * Ticks this sub-level, updating the global bounding box and components.
     */
    @Override
    @ApiStatus.Internal
    public void tick() {
        super.tick();
        this.updateBoundingBox();

        final BoundingBox3dc bounds = this.boundingBox();

        if (!this.isRemoved() && (bounds.minY() < SableConfig.SUB_LEVEL_REMOVE_MIN.getAsDouble() || bounds.maxY() > SableConfig.SUB_LEVEL_REMOVE_MAX.getAsDouble())) {
            Sable.LOGGER.info("Sub-level {} has an extreme Y coordinate range, removing", this);
            this.markRemoved();
            return;
        }

        if (SableConfig.SUB_LEVEL_SPLITTING.getAsBoolean()) {
            this.heatMapManager.tick();
        }
    }

    /**
     * @return the last stopped status sent out to players
     */
    @ApiStatus.Internal
    public boolean getLastNetworkedStopped() {
        return this.lastNetworkedStopped;
    }

    /**
     * Sets the last stopped status sent out to players
     *
     * @param stopped the last stopped status
     */
    @ApiStatus.Internal
    public void setLastNetworkedStopped(final boolean stopped) {
        this.lastNetworkedStopped = stopped;
    }

    /**
     * Updates & merges the mass trackers for this sub-level.
     * Called before physics ticking, before the physics tick event is dispatched,
     * and before {@link ServerSubLevel#prePhysicsTick(SubLevelPhysicsSystem, RigidBodyHandle, double)}.
     */
    @ApiStatus.Internal
    public void updateMergedMassData(final float partialPhysicsTick) {
        if (this.massTracker != null) {
            this.massTracker.update(partialPhysicsTick);
        }
    }

    /**
     * Called before the physics tick begins.
     */
    @ApiStatus.Internal
    public void prePhysicsTickBegin() {
        if (this.queuedForceGroups != null) {
            this.queuedForceGroups.values().forEach(QueuedForceGroup::reset);
        }
    }

    public void applyQueuedForces(final SubLevelPhysicsSystem physicsSystem, final RigidBodyHandle handle, final double timeStep) {
        if (this.queuedForceGroups != null) {
            for (final Map.Entry<ForceGroup, QueuedForceGroup> entry : this.queuedForceGroups.entrySet()) {
                final QueuedForceGroup group = entry.getValue();

                handle.applyForcesAndReset(group.getForceTotal());
            }
        }
    }

    /**
     * Called once per **physics** tick.
     * There may be multiple physics ticks per tick. <br/>
     *
     * @param handle   The physics pipeline handle for sub-level
     * @param timeStep The time step between physics ticks
     */
    @ApiStatus.Internal
    public void prePhysicsTick(final SubLevelPhysicsSystem physicsSystem, final RigidBodyHandle handle, final double timeStep) {
        this.jointedSubLevels.forEach(jointedSubLevels -> {
            if (jointedSubLevels.isRemoved()) {
                this.removeJointedSubLevels(jointedSubLevels);
            }
        });

        if (this.updateInteractiveSubLevelsNextTick) {
            this.updateInteractiveSubLevels();
            this.updateInteractiveSubLevelsNextTick = false;
        }

        final ServerLevelPlot plot = this.getPlot();
        for (final BlockEntitySubLevelActor actor : plot.getBlockEntityActors()) {
            actor.sable$physicsTick(this, handle, timeStep);
        }

        final ObjectCollection<BlockSubLevelLiftProvider.LiftProviderContext> liftProviders = plot.getLiftProviders();
        final Collection<KinematicContraption> contraptions = plot.getContraptions();

        if (!liftProviders.isEmpty() || this.floatingBlockController.needsTicking() || this.reactionWheelManager.needsTicking() || !contraptions.isEmpty()) {
            final boolean trackForces = this.isTrackingIndividualQueuedForces();

            final Vector3d linearVelocity = handle.getLinearVelocity(new Vector3d());
            final Vector3d angularVelocity = handle.getAngularVelocity(new Vector3d());

            final Vector3d linearImpulse = new Vector3d();
            final Vector3d angularImpulse = new Vector3d();

            final List<BlockSubLevelLiftProvider.LiftProviderGroup> groups = trackForces ? BlockSubLevelLiftProvider.groupLiftProviders(liftProviders) : List.of();

            // main sub-level lift & drag
            for (final BlockSubLevelLiftProvider.LiftProviderContext context : liftProviders) {
                BlockSubLevelLiftProvider.LiftProviderGroup group = null;

                // TODO: don't do this, bad for performance
                for (final BlockSubLevelLiftProvider.LiftProviderGroup g : groups) {
                    if (g.positions().contains(context.pos())) {
                        group = g;
                        break;
                    }
                }

                ((BlockSubLevelLiftProvider) context.state().getBlock()).sable$contributeLiftAndDrag(context, this, null, timeStep, linearVelocity, angularVelocity, linearImpulse, angularImpulse, group);
            }

            for (final BlockSubLevelLiftProvider.LiftProviderGroup group : groups) {
                if (group.totalLift().lengthSquared() >= 0.001 * 0.001)
                    this.getOrCreateQueuedForceGroup(ForceGroups.LIFT.get())
                            .recordPointForce(group.liftCenter().div(group.totalLiftStrength), group.totalLift());

                if (group.totalDrag().lengthSquared() >= 0.001 * 0.001)
                    this.getOrCreateQueuedForceGroup(ForceGroups.DRAG.get())
                            .recordPointForce(group.dragCenter().div(group.totalDragStrength), group.totalDrag());
            }

            // contraption lift & drag
            if (!contraptions.isEmpty()) {
                final Pose3d localContraptionPose = new Pose3d();

                for (final KinematicContraption contraption : contraptions) {
                    final Collection<BlockSubLevelLiftProvider.LiftProviderContext> contraptionProviders = contraption.sable$liftProviders().values();

                    contraption.sable$getLocalPose(localContraptionPose, physicsSystem.getPartialPhysicsTick());
                    final List<BlockSubLevelLiftProvider.LiftProviderGroup> contraptionGroups = trackForces ? BlockSubLevelLiftProvider.groupLiftProviders(contraptionProviders) : List.of();

                    for (final BlockSubLevelLiftProvider.LiftProviderContext context : contraptionProviders) {
                        BlockSubLevelLiftProvider.LiftProviderGroup group = null;

                        // TODO: don't do this
                        for (final BlockSubLevelLiftProvider.LiftProviderGroup g : contraptionGroups) {
                            if (g.positions().contains(context.pos())) {
                                group = g;
                                break;
                            }
                        }

                        ((BlockSubLevelLiftProvider) context.state().getBlock())
                                .sable$contributeLiftAndDrag(context, this, localContraptionPose, timeStep, linearVelocity, angularVelocity, linearImpulse, angularImpulse, group);
                    }

                    for (final BlockSubLevelLiftProvider.LiftProviderGroup group : contraptionGroups) {
                        if (group.totalLift().lengthSquared() >= 0.001 * 0.001)
                            this.getOrCreateQueuedForceGroup(ForceGroups.LIFT.get())
                                    .recordPointForce(group.liftCenter().div(group.totalLiftStrength), group.totalLift());

                        if (group.totalDrag().lengthSquared() >= 0.001 * 0.001)
                            this.getOrCreateQueuedForceGroup(ForceGroups.DRAG.get())
                                    .recordPointForce(group.dragCenter().div(group.totalDragStrength), group.totalDrag());
                    }
                }
            }

            // TODO: what.
            linearVelocity.fma(-1.0 / 2.1 * timeStep, DimensionPhysicsData.getGravity(this.getLevel()));

            this.floatingBlockController.physicsTick(physicsSystem.getPartialPhysicsTick(),timeStep, linearVelocity, angularVelocity, linearImpulse, angularImpulse);
            this.reactionWheelManager.physicsTick(handle);

            handle.applyLinearAndAngularImpulse(linearImpulse, angularImpulse, false);
        }
    }

    private void updateInteractiveSubLevels() {
        final Deque<ServerSubLevel> queue = new ArrayDeque<>();
        queue.add(this);

        final ObjectCollection<ServerSubLevel> allSubLevels = new ObjectOpenHashSet<>();
        while (!queue.isEmpty()) {
            final ServerSubLevel currentSubLevel = queue.pop();
            allSubLevels.add(currentSubLevel);

            currentSubLevel.getJointedSubLevels().forEach(childSubLevel -> {
               if (!allSubLevels.contains(childSubLevel)) {
                   queue.add(childSubLevel);
               }
            });
        }
        allSubLevels.forEach(serverSubLevel -> {
            serverSubLevel.interactiveSubLevels.clear();
            serverSubLevel.interactiveSubLevels.addAll(allSubLevels);

            //Display the size of allSubLevels for debugging.
            //serverSubLevel.setName(String.valueOf(allSubLevels.size()));
            //Display the size of jointedSubLevels for debugging.
            //serverSubLevel.setName(String.valueOf(serverSubLevel.jointedSubLevels.size()));
        });
    }

    /**
     * Gets or creates a queued force group for the given force group.
     *
     * @param forceGroup the force group to get or create a queued force group for
     * @return the queued force group
     */
    public QueuedForceGroup getOrCreateQueuedForceGroup(final ForceGroup forceGroup) {
        if (this.queuedForceGroups == null) {
            this.queuedForceGroups = new Object2ObjectOpenHashMap<>();
        }

        return this.queuedForceGroups.computeIfAbsent(forceGroup, fg -> new QueuedForceGroup(this));
    }

    /**
     * Deletes all entities inside the plot
     */
    public void deleteAllEntities() {
        this.getPlot().kickAllEntities();
    }

    @Override
    public void setName(@Nullable final String name) {
        if (!Objects.equals(name, this.getName())) {
            this.playerSink().sendPacket(new ClientboundChangeSubLevelNamePacket(this.getUniqueId(), name));
        }

        super.setName(name);
    }

    public SubLevelHeatMapManager getHeatMapManager() {
        return this.heatMapManager;
    }

    public FloatingBlockController getFloatingBlockController() {
        return this.floatingBlockController;
    }

    public ReactionWheelManager getReactionWheelManager()
    {
        return this.reactionWheelManager;
    }

    /**
     * Sets that this sub-level was split from another sub-level,
     * and that this should be conveyed to the client for snapshot interpolation to
     * maintain smoothness
     *
     * @param containingSubLevel the sub-level that this was split from
     * @param originalPose       the original pose of the sub-level before it was projected out of the containing sub-level
     */
    public void setSplitFrom(final ServerSubLevel containingSubLevel, final Pose3d originalPose) {
        this.splitFromSubLevel = containingSubLevel.getUniqueId();
        this.splitFromPose = originalPose;
    }

    /**
     * Gets the sub-level that this was split from, if any.
     */
    @Nullable
    public UUID getSplitFromSubLevel() {
        return this.splitFromSubLevel;
    }

    /**
     * Gets the original pose of the sub-level before it was projected out of the containing sub-level.
     */
    @Nullable
    public Pose3d getSplitFromPose() {
        return this.splitFromPose;
    }

    /**
     * Clears the split from sub-level and original pose.
     */
    public void clearSplitFrom() {
        this.splitFromSubLevel = null;
        this.splitFromPose = null;
    }

    /**
     * @return the parent level of this sub-level
     */
    @Override
    public ServerLevel getLevel() {
        return (ServerLevel) super.getLevel();
    }

    /**
     * @return the plot containing the contents of this sub-level
     */
    @Override
    public ServerLevelPlot getPlot() {
        return (ServerLevelPlot) super.getPlot();
    }

    /**
     * The mass & inertia tracker for this sub-level
     */
    @Override
    public MassData getMassTracker() {
        return this.massTracker;
    }

    @ApiStatus.Internal
    public void buildMassTracker() {
        final MassTracker internalTracker = MassTracker.build(new LevelAccelerator(this.getLevel()), this.getPlot().getBoundingBox());
        this.massTracker = new MergedMassTracker(this, internalTracker);
    }

    /**
     * @return the mass tracker for just the sub-level, not including merged masses
     */
    public MassTracker getSelfMassTracker() {
        return this.massTracker.getSelfMassTracker();
    }

    public ObjectCollection<ServerSubLevel> getJointedSubLevels() {
        return this.jointedSubLevels;
    }

    public ObjectCollection<ServerSubLevel> getInteractiveSubLevels() {
        return this.interactiveSubLevels;
    }

    public void addJointedSubLevels(final ServerSubLevel subLevel) {
        this.jointedSubLevels.add(subLevel);
        this.updateInteractiveSubLevelsNextTick = true;
    }

    public void removeJointedSubLevels(final ServerSubLevel subLevel) {
        this.jointedSubLevels.remove(subLevel);
        this.updateInteractiveSubLevelsNextTick = true;
    }

    /**
     * @return the last place this sub-level was saved
     */
    @ApiStatus.Internal
    public GlobalSavedSubLevelPointer getLastSerializationPointer() {
        return this.lastSerializationPointer;
    }

    /**
     * Sets the last place this sub-level was saved to.
     *
     * @param lastSerializationPointer the pointer to the last serialization location
     */
    @ApiStatus.Internal
    public void setLastSerializationPointer(final GlobalSavedSubLevelPointer lastSerializationPointer) {
        this.lastSerializationPointer = lastSerializationPointer;
    }

    /**
     * Sets if individual queued forces should be kept track of
     */
    public void enableIndividualQueuedForcesTracking(final boolean enable) {
        this.trackIndividualQueuedForces = enable;
    }

    /**
     * @return if individual queued forces are being tracked
     */
    public boolean isTrackingIndividualQueuedForces() {
        return this.trackIndividualQueuedForces;
    }

    /**
     * @return a map of force group -> queued force groups
     */
    public @Nullable Object2ObjectMap<ForceGroup, QueuedForceGroup> getQueuedForceGroups() {
        return this.queuedForceGroups;
    }

    /**
     * @return the user-data compound tag if any exists, which is saved and serialized with this sub-level
     */
    public @Nullable CompoundTag getUserDataTag() {
        return this.userDataTag;
    }

    /**
     * Sets the user-data compound tag, which is saved and serialized with this sub-level
     *
     * @param userDataTag the user-data compound tag
     */
    public void setUserDataTag(final CompoundTag userDataTag) {
        this.userDataTag = userDataTag;
    }

    @Override
    public String toString() {
        return "ServerSubLevel" + super.toString();
    }
}
