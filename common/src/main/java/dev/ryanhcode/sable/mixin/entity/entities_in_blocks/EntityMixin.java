package dev.ryanhcode.sable.mixin.entity.entities_in_blocks;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.math.LevelReusedVectors;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.LevelExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin {

    @Shadow
    public abstract boolean isAlive();

    @Shadow
    private Level level;

    @Shadow
    protected abstract void onInsideBlock(BlockState blockState);

    @Inject(method = "checkInsideBlocks", at = @At("TAIL"))
    protected void sable$checkInsideBlocks(final CallbackInfo ci) {
        final Entity entity = (Entity) (Object) this;
        final LevelReusedVectors sink = ((LevelExtension) this.level).sable$getJOMLSink();
        final AABB entityBounds = entity.getBoundingBox();
        final Vector3d entityBoundsCenter = JOMLConversion.getAABBCenter(entityBounds, new Vector3d());
        final OrientedBoundingBox3d entityBoundsOBB = new OrientedBoundingBox3d(entityBoundsCenter.x, entityBoundsCenter.y, entityBoundsCenter.z, entityBounds.getXsize(), entityBounds.getYsize(), entityBounds.getZsize(), new Quaterniond().identity(), sink);
        final OrientedBoundingBox3d blockBoundsOBB = new OrientedBoundingBox3d(new Vector3d(0), new Vector3d(1), new Quaterniond().identity(), sink);
        final BoundingBox3d fullContextBounds = new BoundingBox3d(entityBounds);
        final Vector3d mtv = sink.mtv;

        for (final SubLevel intersecting : Sable.HELPER.getAllIntersecting(this.level, new BoundingBox3d(entityBounds))) {
            final Pose3d pose = intersecting.logicalPose();
            final double yaw = SubLevelEntityCollision.getHitBoxYaw(pose);
            entityBoundsOBB.setOrientation(new Quaterniond().identity().rotateY(yaw));
            blockBoundsOBB.getOrientation().set(pose.orientation());

            final BoundingBox3d localBounds = new BoundingBox3d();
            final BoundingBox3d rotatedContextBounds = new BoundingBox3d().set(fullContextBounds);
            entityBoundsOBB.vertices(sink.a);
            for (final Vector3d vec : sink.a) {
                rotatedContextBounds.expandTo(vec);
            }
            rotatedContextBounds.expand(0.35f);
            rotatedContextBounds.transformInverse(pose, new Matrix4d(), localBounds);

            final Iterable<BlockPos> blocks = BlockPos.betweenClosed(sink.minPos.set(localBounds.minX, localBounds.minY, localBounds.minZ), sink.maxPos.set(localBounds.maxX, localBounds.maxY, localBounds.maxZ));

            for (final BlockPos blockPos : blocks) {
                if (!this.isAlive()) {
                    return;
                }
                blockBoundsOBB.getPosition().set(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5);
                pose.transformPosition(blockBoundsOBB.getPosition());

                OrientedBoundingBox3d.sat(entityBoundsOBB, blockBoundsOBB, mtv);

                if (mtv.lengthSquared() > 0.03 && mtv.x != Double.MAX_VALUE && mtv.y != Double.MAX_VALUE && mtv.z != Double.MAX_VALUE) {
                    final BlockState blockState = this.level.getBlockState(blockPos);
                    try {
                        blockState.entityInside(this.level, blockPos, entity);
                        this.onInsideBlock(blockState);
                    } catch (final Throwable throwable) {
                        SubLevelHelper.popEntityLocal(intersecting, entity);
                        final CrashReport crashReport = CrashReport.forThrowable(throwable, "Colliding entity with block");
                        final CrashReportCategory crashReportCategory = crashReport.addCategory("Block being collided with");
                        CrashReportCategory.populateBlockDetails(crashReportCategory, this.level, blockPos, blockState);
                        throw new ReportedException(crashReport);
                    }
                }
            }
        }
    }
}
