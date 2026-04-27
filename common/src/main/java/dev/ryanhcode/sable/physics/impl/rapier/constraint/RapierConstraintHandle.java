package dev.ryanhcode.sable.physics.impl.rapier.constraint;

import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.physics.impl.rapier.Rapier3D;
import org.jetbrains.annotations.ApiStatus;
import org.joml.Vector3d;

@ApiStatus.Internal
public abstract class RapierConstraintHandle implements PhysicsConstraintHandle {

    /**
     * The handle to use for {@link dev.ryanhcode.sable.physics.impl.rapier.Rapier3D} methods
     */
    protected final long handle;

    /**
     * The scene ID that this constraint is in
     */
    protected final int sceneID;

    private final double[] impulseCache;

    /**
     * Creates a new constraint handle
     *
     * @param sceneID the scene ID that this constraint is in
     * @param handle  the handle from the physics engine
     */
    protected RapierConstraintHandle(final int sceneID, final long handle) {
        this.sceneID = sceneID;
        this.handle = handle;
        this.impulseCache = new double[6];
    }

    /**
     * Sets if contacts are enabled between the two bodies in the constraint
     */
    @Override
    public void setContactsEnabled(final boolean enabled) {
        Rapier3D.setConstraintContactsEnabled(this.sceneID, this.handle, enabled);
    }

    /**
     * Gets the latest linear and angular joint impulses from the solver
     */
    @Override
    public void getJointImpulses(final Vector3d linearImpulseDest, final Vector3d angularImpulseDest) {
        Rapier3D.getConstraintImpulses(this.sceneID, this.handle, this.impulseCache);
        linearImpulseDest.set(this.impulseCache[0], this.impulseCache[1], this.impulseCache[2]);
        angularImpulseDest.set(this.impulseCache[3], this.impulseCache[4], this.impulseCache[5]);
    }

    /**
     * Adds / sets a motor on this joint
     *
     * @param axis          The axis on which the motor operates
     * @param target        The target position along that axis [m | rad]
     * @param stiffness     How stiff the motor should act, or P in the PD controller
     * @param damping       How much damping the motor should have, or D in the PD controller
     * @param hasForceLimit If the motor should have a force limit
     * @param maxForce      The maximum force the motor can apply
     */
    @Override
    public void setMotor(final ConstraintJointAxis axis, final double target, final double stiffness, final double damping, final boolean hasForceLimit, final double maxForce) {
        Rapier3D.setConstraintMotor(this.sceneID, this.handle, axis.ordinal(), target, stiffness, damping, hasForceLimit, maxForce);
    }

    /**
     * Removes the constraint from the active physics engine
     */
    @Override
    public void remove() {
        Rapier3D.removeConstraint(this.sceneID, this.handle);
    }

    /**
     * @return if the constraint is still valid, and has not been removed by the engine
     */
    @Override
    public boolean isValid() {
        return Rapier3D.isConstraintValid(this.sceneID, this.handle);
    }
}
