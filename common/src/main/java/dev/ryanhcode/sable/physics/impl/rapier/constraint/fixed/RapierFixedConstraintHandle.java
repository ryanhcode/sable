package dev.ryanhcode.sable.physics.impl.rapier.constraint.fixed;

import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.constraint.fixed.FixedConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.fixed.FixedConstraintHandle;
import dev.ryanhcode.sable.physics.impl.rapier.Rapier3D;
import dev.ryanhcode.sable.physics.impl.rapier.constraint.RapierConstraintHandle;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

public class RapierFixedConstraintHandle extends RapierConstraintHandle implements FixedConstraintHandle {
    /**
     * Creates a rapier constraint handle
     */
    public static RapierFixedConstraintHandle create(final ServerLevel serverLevel, @Nullable final PhysicsPipelineBody bodyA, @Nullable final PhysicsPipelineBody bodyB, final FixedConstraintConfiguration config) {
        final int sceneID = Rapier3D.getID(serverLevel);

        final long handle = Rapier3D.addFixedConstraint(
                sceneID,
                bodyA == null ? -1 : Rapier3D.getID(bodyA),
                bodyB == null ? -1 : Rapier3D.getID(bodyB),
                config.pos1().x(),
                config.pos1().y(),
                config.pos1().z(),
                config.pos2().x(),
                config.pos2().y(),
                config.pos2().z(),
                config.orientation().x(),
                config.orientation().y(),
                config.orientation().z(),
                config.orientation().w()
        );

        return new RapierFixedConstraintHandle(sceneID, handle);
    }

    /**
     * Creates a new constraint handle
     *
     * @param sceneID the scene ID that this constraint is in
     * @param handle the handle from the physics engine
     */
    public RapierFixedConstraintHandle(final int sceneID, final long handle) {
        super(sceneID, handle);
    }

}
