package dev.ryanhcode.sable.physics.impl.rapier.constraint.generic;

import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintHandle;
import dev.ryanhcode.sable.physics.impl.rapier.Rapier3D;
import dev.ryanhcode.sable.physics.impl.rapier.constraint.RapierConstraintHandle;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniondc;
import org.joml.Vector3dc;

@ApiStatus.Internal
public class RapierGenericConstraintHandle extends RapierConstraintHandle implements GenericConstraintHandle {

    private static final int FRAME_SIDE_FIRST = 0;
    private static final int FRAME_SIDE_SECOND = 1;

    /**
     * Creates a rapier constraint handle
     */
    @Contract("_, _, _, _ -> new")
    public static @NotNull RapierGenericConstraintHandle create(final ServerLevel serverLevel, @Nullable final PhysicsPipelineBody bodyA, @Nullable final PhysicsPipelineBody bodyB, final GenericConstraintConfiguration config) {
        final long sceneHandle = Rapier3D.getSceneHandle(serverLevel);

        int lockedAxesMask = 0;
        for (final ConstraintJointAxis axis : config.lockedAxes()) {
            lockedAxesMask |= 1 << axis.ordinal();
        }

        int coupledAxesMask = 0;
        for (final ConstraintJointAxis axis : config.coupledAxes()) {
            coupledAxesMask |= 1 << axis.ordinal();
        }

        final long handle = Rapier3D.addGenericConstraint(
                sceneHandle,
                bodyA == null ? -1 : Rapier3D.getID(bodyA),
                bodyB == null ? -1 : Rapier3D.getID(bodyB),
                config.pos1().x(),
                config.pos1().y(),
                config.pos1().z(),
                config.orientation1().x(),
                config.orientation1().y(),
                config.orientation1().z(),
                config.orientation1().w(),
                config.pos2().x(),
                config.pos2().y(),
                config.pos2().z(),
                config.orientation2().x(),
                config.orientation2().y(),
                config.orientation2().z(),
                config.orientation2().w(),
                lockedAxesMask,
                coupledAxesMask
        );

        return new RapierGenericConstraintHandle(sceneHandle, handle);
    }

    /**
     * Creates a new constraint handle
     *
     * @param sceneHandle the scene ID that this constraint is in
     * @param handle the handle from the physics engine
     */
    public RapierGenericConstraintHandle(final long sceneHandle, final long handle) {
        super(sceneHandle, handle);
    }

    @Override
    public void setFrame1(final Vector3dc localPosition, final Quaterniondc localOrientation) {
        this.assertValid();
        Rapier3D.setConstraintFrame(
                this.sceneHandle, this.handle, FRAME_SIDE_FIRST,
                localPosition.x(), localPosition.y(), localPosition.z(),
                localOrientation.x(), localOrientation.y(), localOrientation.z(), localOrientation.w()
        );
    }

    @Override
    public void setFrame2(final Vector3dc localPosition, final Quaterniondc localOrientation) {
        this.assertValid();
        Rapier3D.setConstraintFrame(
                this.sceneHandle, this.handle, FRAME_SIDE_SECOND,
                localPosition.x(), localPosition.y(), localPosition.z(),
                localOrientation.x(), localOrientation.y(), localOrientation.z(), localOrientation.w()
        );
    }

    /**
     * Adds / sets an axis limit on this constraint
     *
     * @param axis The axis on which the limit should be placed
     * @param min  The minimum limit on the constraint axis
     * @param max  The maximum limit on the constraint axis
     */
    @Override
    public void setLimit(final ConstraintJointAxis axis, final double min, final double max) {
        this.assertValid();
        Rapier3D.setConstraintLimit(this.sceneHandle, this.handle, axis.ordinal(), min, max);
    }

    /**
     * Locks the given constraint axes on this constraint
     *
     * @param axes The axes to lock
     */
    @Override
    public void lockAxes(final ConstraintJointAxis @NotNull... axes) {
        byte mask = 0;

        for (final ConstraintJointAxis axis : axes) {
            final byte bit = (byte) (1 << axis.ordinal());

            if ((mask & bit) != 0) {
                throw new RuntimeException("Duplicate axis: " + axis);
            }

            mask |= bit;
        }

        this.assertValid();
        Rapier3D.lockConstraintAxes(this.sceneHandle, this.handle, mask);
    }
}
