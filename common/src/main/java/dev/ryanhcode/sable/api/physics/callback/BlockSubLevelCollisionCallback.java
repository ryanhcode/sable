package dev.ryanhcode.sable.api.physics.callback;

import dev.ryanhcode.sable.companion.math.JOMLConversion;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.ApiStatus;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public interface BlockSubLevelCollisionCallback {

    /**
     * Called when a collision occurs between two blocks, from JNI / pipeline implementations
     *
     * @return tangent motion
     */
    @ApiStatus.Internal
    @SuppressWarnings("unused")
    default double[] onCollision(final int x,
                                 final int y,
                                 final int z,
                                 final double xWorld,
                                 final double yWorld,
                                 final double zWorld,
                                 final int xOther,
                                 final int yOther,
                                 final int zOther,
                                 final double xWorldOther,
                                 final double yWorldOther,
                                 final double zWorldOther,
                                 final double impactVelocity) {
        final CollisionResult result = this.sable$onCollision(new BlockPos(x, y, z), new Vector3d(xWorld, yWorld, zWorld), new BlockPos(xOther, yOther, zOther), new Vector3d(xWorldOther, yWorldOther, zWorldOther), impactVelocity);
        final Vector3dc motion = result.tangentMotion;

        // TODO: this is stupid and moronic to pass through the removal as a double lmao, let's not do that in the future
        return new double[]{motion.x(), motion.y(), motion.z(), result.removeCollision ? 1.0 : 0.0};
    }

    /**
     * Called when a collision occurs between two blocks, from JNI / pipeline implementations.
     * <p>
     * Legacy onCollision method for back-compatibility with older pipeline implementations.
     * It is preferred you implement the fully qualified version {@link #sable$onCollision(BlockPos, Vector3d, BlockPos, Vector3d, double)} for new implementations.
     * <p>
     * Ignored if the fully qualified version is implemented.
     * */
    @Deprecated
    default CollisionResult sable$onCollision(final BlockPos blockPos, final Vector3d pos, final double impactVelocity) {
        return CollisionResult.NONE;
    }

    /**
     * Called when a collision occurs between two blocks, from JNI / pipeline implementations.
     * */
    default CollisionResult sable$onCollision(final BlockPos blockPos, final Vector3d pos, final BlockPos otherBlockPos, final Vector3d otherPos, final double impactVelocity) {
        return this.sable$onCollision(blockPos, pos, impactVelocity);
    }

    record CollisionResult(Vector3dc tangentMotion, boolean removeCollision) {
        public static final CollisionResult NONE = new CollisionResult(JOMLConversion.ZERO, false);
    }

}
