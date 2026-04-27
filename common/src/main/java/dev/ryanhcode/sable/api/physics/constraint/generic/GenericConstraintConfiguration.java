package dev.ryanhcode.sable.api.physics.constraint.generic;

import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintConfiguration;
import org.joml.Quaterniondc;
import org.joml.Vector3dc;

import java.util.EnumSet;
import java.util.Set;

/**
 * A configuration for a generic constraint, with per-axis hard locks and re-anchorable local frames.
 *
 * @param pos1         the position in world space assumed to be inside the plot of the first sub-level (ex. a block position).
 * @param pos2         the position in world space assumed to be inside the plot of the second sub-level (ex. a block position).
 * @param orientation1 the local orientation of the joint frame on the first sub-level.
 * @param orientation2 the local orientation of the joint frame on the second sub-level.
 * @param lockedAxes   the set of axes hard-locked by the solver; empty matches a free constraint.
 * @since 1.1.0
 */
public record GenericConstraintConfiguration(
        Vector3dc pos1,
        Vector3dc pos2,
        Quaterniondc orientation1,
        Quaterniondc orientation2,
        Set<ConstraintJointAxis> lockedAxes
) implements PhysicsConstraintConfiguration<GenericConstraintHandle> {

    public GenericConstraintConfiguration(final Vector3dc pos1, final Vector3dc pos2, final Quaterniondc orientation1, final Quaterniondc orientation2) {
        this(pos1, pos2, orientation1, orientation2, EnumSet.noneOf(ConstraintJointAxis.class));
    }
}
