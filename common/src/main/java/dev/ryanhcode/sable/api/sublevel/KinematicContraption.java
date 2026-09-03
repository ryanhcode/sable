package dev.ryanhcode.sable.api.sublevel;

import dev.ryanhcode.sable.api.block.BlockSubLevelLiftProvider;
import dev.ryanhcode.sable.api.physics.mass.MassTracker;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.physics.floating_block.FloatingClusterContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import org.joml.Quaterniond;
import org.joml.Vector3dc;

import java.util.Map;

/**
 * A kinematic (position and orientation driven) moving structure that can be hosted on a sub-level. Provides access to
 * the local bounds, block state, mass, pose, lift providers, and floating clusters of the contraption.
 */
public interface KinematicContraption {

    void sable$getLocalBounds(final BoundingBox3i bounds);
    BlockGetter sable$blockGetter();
    MassTracker sable$getMassTracker();
    Vector3dc sable$getPosition(double partialTick);
    Quaterniond sable$getOrientation(double partialTick);
    Map<BlockPos, BlockSubLevelLiftProvider.LiftProviderContext> sable$liftProviders();
    FloatingClusterContainer sable$getFloatingClusterContainer();

    boolean sable$shouldCollide();

    boolean sable$isValid();

    default Vector3dc sable$getPosition() {
        return this.sable$getPosition(1.0f);
    }

    default Quaterniond sable$getOrientation() {
        return this.sable$getOrientation(1.0f);
    }

    default Pose3d sable$getLocalPose(final Pose3d dest, final double partialTick) {
        dest.rotationPoint().set(this.sable$getMassTracker().getCenterOfMass());
        dest.position().set(this.sable$getPosition(partialTick));
        dest.orientation().set(this.sable$getOrientation(partialTick));
        dest.scale().set(JOMLConversion.ONE);
        return dest;
    }
}
