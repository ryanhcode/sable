package dev.ryanhcode.sable.api.physics.mass;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3dc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public interface MassData {

    /**
     * @return total mass [kpg]
     */
    double getMass();

    /**
     * @return total inverse mass [1/kpg]
     */
    double getInverseMass();

    /**
     * @return inertia tensor in local space [kpg*m^2]
     */
    Matrix3dc getInertiaTensor();

    /**
     * @return inverse inertia tensor in local space [1/(kpg*m^2)]
     */
    Matrix3dc getInverseInertiaTensor();

    /**
     * @return the nullable location of the center-of-mass
     */
    @Nullable
    Vector3dc getCenterOfMass();

    default boolean isInvalid() {
        return this.getMass() <= 0.0 || this.getCenterOfMass() == null;
    }

    /**
     * @param position the position to check the normal mass at, assumed to be in the plot
     * @param direction the direction to check the normal mass along, local to the plot
     * @return the normal mass, or effective mass at the plot position and direction
     */
    default double getInverseNormalMass(final Vector3dc position, final Vector3dc direction) {
        final Vector3d comLocalPos = position.sub(this.getCenterOfMass(), new Vector3d());
        final Vector3d normalizedDirection = direction.normalize(new Vector3d());
        final Vector3d cross = comLocalPos.cross(normalizedDirection, new Vector3d());

        return cross.dot(this.getInverseInertiaTensor().transform(cross, new Vector3d()))
                + this.getInverseMass();
    }
}
