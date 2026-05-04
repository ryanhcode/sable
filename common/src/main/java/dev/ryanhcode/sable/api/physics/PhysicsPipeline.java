package dev.ryanhcode.sable.api.physics;

import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.physics.object.box.BoxHandle;
import dev.ryanhcode.sable.api.physics.object.box.BoxPhysicsObject;
import dev.ryanhcode.sable.api.physics.object.rope.RopeHandle;
import dev.ryanhcode.sable.api.physics.object.rope.RopePhysicsObject;
import dev.ryanhcode.sable.api.sublevel.KinematicContraption;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.physics.config.PhysicsConfigData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * An abstracted physics engine & pipeline for handling {@link dev.ryanhcode.sable.sublevel.SubLevel} physics calculations.
 */
public interface PhysicsPipeline {

    /**
     * Initializes the physics pipeline.
     *
     * @param gravity the gravity vector
     * @param universalDrag the universal drag to apply to all bodies
     */
    void init(Vector3dc gravity, double universalDrag);

    /**
     * Disposes all resources used by the physics pipeline.
     */
    void dispose();

    /**
     * Sets up for the physics ticking with a time step of {@code 1.0 / 20.0} seconds.
     */
    void prePhysicsTicks();

    /**
     * Runs a physics substep with a time step of {@code 1.0 / 20.0 / substeps} seconds.
     *
     * @param timeStep the time step of this physics substep ({@code 1.0 / 20.0 / substeps}) [s]
     */
    void physicsTick(double timeStep);

    /**
     * Called after all physics substeps have been run, to finalize the physics tick.
     */
    void postPhysicsTicks();

    /**
     * Runs a tick to update any separate data tracking / logic, even if physics is currently paused
     */
    void tick();

    /**
     * Adds a {@link ServerSubLevel} to the physics pipeline.
     */
    void add(ServerSubLevel subLevel, Pose3dc pose);

    /**
     * Removes a {@link SubLevel} from the physics pipeline.
     */
    void remove(ServerSubLevel subLevel);

    /**
     * Adds a kinematic contraption to the scene
     */
    void add(KinematicContraption contraption);

    /**
     * Removes a kinematic contraption from the scene
     */
    void remove(KinematicContraption contraption);

    /**
     * Queries the physics pipeline for the current pose of a {@link SubLevel}.
     *
     * @param subLevel the sub-level to query
     * @param dest     the pose to write into
     * @return the pose of the sub-level stored in dest
     */
    @ApiStatus.OverrideOnly
    Pose3d readPose(ServerSubLevel subLevel, Pose3d dest);

    /**
     * Adds a rope to the physics pipeline
     */
    @ApiStatus.OverrideOnly
    RopeHandle addRope(RopePhysicsObject rope);

    /**
     * Adds a box to the physics pipeline
     */
    BoxHandle addBox(BoxPhysicsObject boxPhysicsObject);

    /**
     * Handles the addition of a chunk to the physics context
     *
     * @param x                  the section x position
     * @param y                  the section y position
     * @param z                  the section z position
     */
    void handleChunkSectionAddition(LevelChunkSection chunk, int x, int y, int z, boolean uploadDataIfGlobal);

    /**
     * Handles the removal of a chunk section from the physics context
     *
     * @param x the section x position
     * @param y the section y position
     * @param z the section z position
     */
    void handleChunkSectionRemoval(int x, int y, int z);

    /**
     * Handles the change of a block (from oldState to newState) in a chunk at chunk-relative position x, y, z.
     * Only called server-side.
     *
     * @param x chunk-relative x position
     * @param z chunk-relative z position
     * @param y chunk-relative y position
     */
    void handleBlockChange(SectionPos sectionPos, LevelChunkSection chunk, int x, int y, int z, BlockState oldState, BlockState newState);

    /**
     * Called to re-upload center of mass, mass properties, and local bounds to the physics pipeline
     */
    default void onStatsChanged(@NotNull final ServerSubLevel serverSubLevel) {

    }

    /**
     * Teleports the physics pipeline body to a given position.
     *
     * @param body    the physics pipeline body to teleport
     * @param position    the new position to teleport to
     * @param orientation the new orientation to teleport to
     */
    void teleport(PhysicsPipelineBody body, Vector3dc position, Quaterniondc orientation);

    /**
     * Adds a force at a given world position to a data containing the position
     *
     * @param body the physics pipeline body to apply the force to
     * @param position the plot position to apply the force at [m]
     * @param force    the force to apply [N]
     */
    void applyImpulse(PhysicsPipelineBody body, Vector3dc position, Vector3dc force);

    /**
     * Adds a local force and torque
     *
     * @param body     the body to apply the force to
     * @param force    the local force to apply [N]
     * @param torque   the local torque to apply [Nm]
     * @param wakeUp   if the physics pipeline body should be woken if it is sleeping
     */
    void applyLinearAndAngularImpulse(PhysicsPipelineBody body, Vector3dc force, Vector3dc torque, boolean wakeUp);

    /**
     * Adds linear and angular velocities to a physics pipeline body
     *
     * @param body        the physics pipeline body to apply the velocities to
     * @param linearVelocity  the linear velocity to apply [m/s]
     * @param angularVelocity the angular velocity to apply [rad/s]
     */
    default void addLinearAndAngularVelocity(final PhysicsPipelineBody body, final Vector3dc linearVelocity, final Vector3dc angularVelocity) {

    }

    /**
     * Resets the velocity of a physics pipeline body
     *
     * @param body the physics pipeline body to reset the velocity of
     */
    default void resetVelocity(final PhysicsPipelineBody body) {
        this.addLinearAndAngularVelocity(body, this.getLinearVelocity(body, new Vector3d()).negate(), this.getAngularVelocity(body, new Vector3d()).negate());
    }

    /**
     * Gets the linear velocity of a physics pipeline body
     *
     * @param body the physics pipeline body to get the linear velocity from
     * @return the global linear velocity of the body from the physics engine, stored in dest [m/s]
     */
    default Vector3d getLinearVelocity(final PhysicsPipelineBody body, final Vector3d dest) {
        return dest.zero();
    }

    /**
     * Gets the angular velocity of a physics pipeline body
     *
     * @param body the physics pipeline body to get the angular velocity from
     * @return the global angular velocity of the body from the physics engine, stored in dest [rad/s]
     */
    default Vector3d getAngularVelocity(final PhysicsPipelineBody body, final Vector3d dest) {
        return dest.zero();
    }

    /**
     * "Wakes up" a physics pipeline body, indicating environmental or other changes have occurred that should resume physics if idled or sleeping
     *
     * @param body the physics pipeline body to wake up
     */
    void wakeUp(PhysicsPipelineBody body);

    /**
     * Adds a constraint to the engine, returning its handle
     *
     * @param sublevelA     the first sub-level to constrain, or null to constrain the second sub-level to the world
     * @param sublevelB     the second sub-level to constrain, or null to constrain the first sub-level to the world
     * @param configuration the configuration of the constraint
     */
    default <T extends PhysicsConstraintHandle> T addConstraint(@Nullable final ServerSubLevel sublevelA, @Nullable final ServerSubLevel sublevelB, final PhysicsConstraintConfiguration<T> configuration) {
        return this.addConstraint((PhysicsPipelineBody)sublevelA, sublevelB, configuration);
    }

    /**
     * Adds a constraint to the engine, returning its handle
     *
     * @param rigidbodyA    the first rigid-body to constrain, or null to constrain the second rigid-body to the world
     * @param rigidbodyB    the second rigid-body to constrain, or null to constrain the first rigid-body to the world
     * @param configuration the configuration of the constraint
     */
    default <T extends PhysicsConstraintHandle> T addConstraint(@Nullable final PhysicsPipelineBody rigidbodyA, @Nullable final PhysicsPipelineBody rigidbodyB, final PhysicsConstraintConfiguration<T> configuration) {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * Updates the config of the physics engine from a data object
     *
     * @param data the data to update from
     */
    @ApiStatus.OverrideOnly
    default void updateConfigFrom(final PhysicsConfigData data) {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * @return the next runtime ID for a collider / sub-level
     */
    int getNextRuntimeID();

}