package dev.ryanhcode.sable.api.physics.constraint.generic;

import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import org.joml.Quaterniondc;
import org.joml.Vector3dc;

/**
 * A generic constraint between two bodies.
 *
 * @since 1.1.0
 */
public interface GenericConstraintHandle extends PhysicsConstraintHandle {

    /**
     * Sets the local frame on the first body.
     *
     * @param localPosition the local anchor position
     * @param localRotation the local frame orientation
     */
    void setFrame1(Vector3dc localPosition, Quaterniondc localRotation);

    /**
     * Sets the local frame on the second body.
     *
     * @param localPosition the local anchor position
     * @param localRotation the local frame orientation
     */
    void setFrame2(Vector3dc localPosition, Quaterniondc localRotation);
}
