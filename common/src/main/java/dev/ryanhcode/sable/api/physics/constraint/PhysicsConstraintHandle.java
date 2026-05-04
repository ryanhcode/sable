package dev.ryanhcode.sable.api.physics.constraint;

import org.joml.Vector3d;

/**
 * An active constraint tracked by the physics world.
 * Must be kept track of to be removed.
 */
public interface PhysicsConstraintHandle {

    /**
     * Gets the latest global linear and angular joint impulses from the solver
     */
    void getJointImpulses(Vector3d linearImpulseDest, Vector3d angularImpulseDest);

    /**
     * Sets if contacts are enabled between the two bodies in the constraint
     */
    void setContactsEnabled(boolean enabled);

    /**
     * Adds / sets a motor on this joint
     *
     * @param axis The axis on which the motor operates
     * @param target The target position along that axis [m | rad]
     * @param stiffness How stiff the motor should act, or P in the PD controller
     * @param damping How much damping the motor should have, or D in the PD controller
     * @param hasMaxForce If the motor should have a force limit
     * @param maxForce The maximum force the motor can apply
     */
    void setMotor(ConstraintJointAxis axis, double target, double stiffness, double damping, boolean hasMaxForce, double maxForce);

    /**
     * Sets limits on this joint
     *
     * @param axis The axis on which the limits are set
     * @param minPosition The minimum position along that axis [m | rad]
     * @param maxPosition The maximum position along that axis [m | rad]
     */
    void setLimits(ConstraintJointAxis axis, double minPosition, double maxPosition);

    /**
     * Removes the constraint from the active physics engine
     */
    void remove();

    /**
     * @return if the constraint is still valid, and has not been removed by the engine
     */
    boolean isValid();

}
