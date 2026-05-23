package dev.ryanhcode.sable.mixin.portal;

import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.math.LevelReusedVectors;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.LevelExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EndPortalBlock.class)
public class EndPortalBlockMixin {

    @Redirect(method = "entityInside", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/shapes/Shapes;joinIsNotEmpty(Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/BooleanOp;)Z"))
    private boolean sable$entityInside(final VoxelShape shape1, final VoxelShape shape2, final BooleanOp resultOperator, @Local(name = "level") final Level level, @Local(name = "pos") final BlockPos pos) {
        final SubLevel sublevel = Sable.HELPER.getContaining(level, pos);
        if (sublevel != null) {
            final LevelReusedVectors sink = ((LevelExtension) level).sable$getJOMLSink();
            final Vector3d mtv = sink.mtv;

            final AABB entityBounds = shape1.bounds();
            final Vector3d entityBoundsCenter = JOMLConversion.getAABBCenter(entityBounds, new Vector3d());
            final OrientedBoundingBox3d entityBoundsOBB = new OrientedBoundingBox3d(entityBoundsCenter.x, entityBoundsCenter.y, entityBoundsCenter.z, entityBounds.getXsize(), entityBounds.getYsize(), entityBounds.getZsize(), new Quaterniond().identity(), sink);
            final Pose3d pose = sublevel.logicalPose();
            final double yaw = SubLevelEntityCollision.getHitBoxYaw(pose);
            entityBoundsOBB.setOrientation(new Quaterniond().identity().rotateY(yaw));

            final AABB blockBounds = shape1.bounds();
            final Vector3d blockBoundsCenter = JOMLConversion.getAABBCenter(blockBounds, new Vector3d());
            final OrientedBoundingBox3d blockBoundsOBB = new OrientedBoundingBox3d(blockBoundsCenter.x, blockBoundsCenter.y, blockBoundsCenter.z, blockBounds.getXsize(), blockBounds.getYsize(), blockBounds.getZsize(), new Quaterniond().identity(), sink);
            blockBoundsOBB.getOrientation().set(pose.orientation());

            OrientedBoundingBox3d.sat(entityBoundsOBB, blockBoundsOBB, mtv);
            return (mtv.lengthSquared() > 0.03 && mtv.x != Double.MAX_VALUE && mtv.y != Double.MAX_VALUE && mtv.z != Double.MAX_VALUE);
        }
        return Shapes.joinIsNotEmpty(shape1, shape2, resultOperator);
    }
}
