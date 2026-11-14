package dev.ryanhcode.sable.api.math;


import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.*;

/**
 * Class containing mutability optimization vectors for OBB SAT calculations
 */
public class LevelReusedVectors {
    public final VoxelShape SCAFFOLDING_TOP = Shapes.create(new AABB(0, 15 / 16f, 0, 1f, 1f, 1f));

    public final Vector3d tempVert6 = new Vector3d();
    public final Vector3d tempVert5 = new Vector3d();
    public final Vector3d tempVert4 = new Vector3d();
    public final Vector3d tempVert3 = new Vector3d();
    public final Vector3d tempVert2 = new Vector3d();
    public final Vector3d tempVert1 = new Vector3d();

    public final Vector3dc zero = new Vector3d();
    public final Vector2d proj1 = new Vector2d();
    public final Vector2d proj2 = new Vector2d();
    public final Vector3d oppo = new Vector3d();
    public final Vector3d obbARight = new Vector3d();
    public final Vector3d obbAForward = new Vector3d();
    public final Vector3d obbAUp = new Vector3d();
    public final Vector3d obbBRight = new Vector3d();
    public final Vector3d obbBForward = new Vector3d();
    public final Vector3d obbBUp = new Vector3d();
    public final Vector3d checker = new Vector3d();
    public final BlockPos.MutableBlockPos minPos = new BlockPos.MutableBlockPos();
    public final BlockPos.MutableBlockPos maxPos = new BlockPos.MutableBlockPos();
    public final BlockPos.MutableBlockPos maxBlockPos = new BlockPos.MutableBlockPos();
    public final BlockPos.MutableBlockPos offsetPos = new BlockPos.MutableBlockPos();
    public final BoundingBox3d fullContextBounds = new BoundingBox3d();
    public final BoundingBox3d rotatedContextBounds = new BoundingBox3d();
    public final BoundingBox3d considerationBounds = new BoundingBox3d();
    public final BoundingBox3d localBounds = new BoundingBox3d();
    public final BoundingBox3d localBounds2 = new BoundingBox3d();
    public final Vector3d collisionMotion = new Vector3d();
    public final Vector3d velocityMotion = new Vector3d();
    public final Vector3d entityBoundsCenter = new Vector3d();
    public final Vector3d stepHeightEntityBoundsCenter = new Vector3d();
    public final Vector3d lastStepTestMTV = new Vector3d();
    public final Vector3d entityPosition = new Vector3d();
    public final Vector3d posMinusCenter = new Vector3d();
    public final Vector3d trackingPosition = new Vector3d();
    public final Pose3d lastPose = new Pose3d();
    public final Pose3d lastSubLevelPose = new Pose3d();
    public final Pose3d subLevelPose = new Pose3d();
    public final Matrix4d bakedMatrix = new Matrix4d();
    public final Vector3d mtv = new Vector3d();
    public final Vector3d normalizedMtv = new Vector3d();
    public final Vector3d localMtv = new Vector3d();
    public final Vector3d existingDeltaMovement = new Vector3d();
    public final Vector3d maxMTV = new Vector3d();
    public final BoundingBox3d maxAABB = new BoundingBox3d();
    public final Vector3d center = new Vector3d();
    public final BoundingBox3d offsetAABB = new BoundingBox3d();
    public final BoundingBox3d compressedMinAABB = new BoundingBox3d();
    public final BoundingBox3d compressedOffsetAABB = new BoundingBox3d();
    public final BoundingBox3d intersection = new BoundingBox3d();

    public final Quaterniond entityBoxOrientation = new Quaterniond();
    public final Quaterniond entityCustomOrientation = new Quaterniond();
    public final Vector3d tempEyePosition = new Vector3d();
    public final Vector3d anchorRelativePosition = new Vector3d();

    public final Vector3d[] a = {
            new Vector3d(),
            new Vector3d(),
            new Vector3d(),
            new Vector3d(),

            new Vector3d(),
            new Vector3d(),
            new Vector3d(),
            new Vector3d()
    };
    public final Vector3d[] b = {
            new Vector3d(),
            new Vector3d(),
            new Vector3d(),
            new Vector3d(),

            new Vector3d(),
            new Vector3d(),
            new Vector3d(),
            new Vector3d()
    };
    public final Vector3d[] checks = {
            new Vector3d(),
            new Vector3d(),
            new Vector3d(),
            new Vector3d(),

            new Vector3d(),
            new Vector3d(),
            new Vector3d(),
            new Vector3d(),

            new Vector3d(),
            new Vector3d(),
            new Vector3d(),
            new Vector3d(),

            new Vector3d(),
            new Vector3d(),
            new Vector3d()
    };
    protected final Vector3d tempmin = new Vector3d();
    protected final Vector3d tempmax = new Vector3d();
    public final Vector3d entityUpDirection = new Vector3d();
    public final Vector3d entityDownDirection = new Vector3d();

    public final Vector3d hitNormal = new Vector3d();
    public final Vector3d overlapNormal = new Vector3d();
    public final Vector3d exitNormal = new Vector3d();
    public final Vector3d relativePos = new Vector3d();
}
