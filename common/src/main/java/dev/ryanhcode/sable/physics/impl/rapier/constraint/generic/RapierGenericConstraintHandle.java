package dev.ryanhcode.sable.physics.impl.rapier.constraint.generic;

import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.generic.GenericConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.generic.GenericConstraintHandle;
import dev.ryanhcode.sable.physics.impl.rapier.Rapier3D;
import dev.ryanhcode.sable.physics.impl.rapier.constraint.RapierConstraintHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.ApiStatus;
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
    public static RapierGenericConstraintHandle create(final ServerLevel serverLevel, @Nullable final ServerSubLevel sublevelA, @Nullable final ServerSubLevel sublevelB, final GenericConstraintConfiguration config) {
        final int sceneID = Rapier3D.getID(serverLevel);

        int lockedAxesMask = 0;
        for (final ConstraintJointAxis axis : config.lockedAxes()) {
            lockedAxesMask |= 1 << axis.ordinal();
        }

        final long handle = Rapier3D.addGenericConstraint(
                sceneID,
                sublevelA == null ? -1 : Rapier3D.getID(sublevelA),
                sublevelB == null ? -1 : Rapier3D.getID(sublevelB),
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
                lockedAxesMask
        );

        return new RapierGenericConstraintHandle(sceneID, handle);
    }

    /**
     * Creates a new constraint handle
     *
     * @param sceneID the scene ID that this constraint is in
     * @param handle the handle from the physics engine
     */
    public RapierGenericConstraintHandle(final int sceneID, final long handle) {
        super(sceneID, handle);
    }

    @Override
    public void setFrame1(final Vector3dc localPosition, final Quaterniondc localRotation) {
        Rapier3D.setConstraintFrame(
                this.sceneID, this.handle, FRAME_SIDE_FIRST,
                localPosition.x(), localPosition.y(), localPosition.z(),
                localRotation.x(), localRotation.y(), localRotation.z(), localRotation.w()
        );
    }

    @Override
    public void setFrame2(final Vector3dc localPosition, final Quaterniondc localRotation) {
        Rapier3D.setConstraintFrame(
                this.sceneID, this.handle, FRAME_SIDE_SECOND,
                localPosition.x(), localPosition.y(), localPosition.z(),
                localRotation.x(), localRotation.y(), localRotation.z(), localRotation.w()
        );
    }
}
