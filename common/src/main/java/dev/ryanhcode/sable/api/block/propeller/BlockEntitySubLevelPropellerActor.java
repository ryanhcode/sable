package dev.ryanhcode.sable.api.block.propeller;

import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

/**
 * An interface for {@link net.minecraft.world.level.block.entity.BlockEntity} sub-classes that apply thrust to a
 * sub-level via a {@link BlockEntityPropeller}. Forces are applied on each physics tick while the propeller is active.
 */
public interface BlockEntitySubLevelPropellerActor extends BlockEntitySubLevelActor {

    Vector3d THRUST_VECTOR = new Vector3d();
    Vector3d THRUST_POSITION = new Vector3d();

    BlockEntityPropeller getPropeller();

    @Override
    default void sable$physicsTick(final ServerSubLevel subLevel, final RigidBodyHandle handle, final double timeStep) {
        final BlockEntityPropeller prop = this.getPropeller();

        if (prop.isActive()) {
            final Vec3 thrustDirection = Vec3.atLowerCornerOf(prop.getBlockDirection().getNormal());
            this.applyForces(subLevel, thrustDirection, timeStep);
        }
    }

    default void applyForces(final ServerSubLevel subLevel, final Vec3 thrustDirection, final double timeStep) {
        final BlockEntityPropeller prop = this. getPropeller();
        final Vec3 thrust = thrustDirection.scale(prop.getScaledThrust() * timeStep);

        THRUST_POSITION.set(JOMLConversion.atCenterOf(prop.getBlockPos()));
        THRUST_VECTOR.set(thrust.x, thrust.y, thrust.z);

        final QueuedForceGroup forceGroup = subLevel.getOrCreateQueuedForceGroup(ForceGroups.PROPULSION.get());
        forceGroup.applyAndRecordPointForce(new Vector3d(THRUST_POSITION), new Vector3d(THRUST_VECTOR));
    }
}
