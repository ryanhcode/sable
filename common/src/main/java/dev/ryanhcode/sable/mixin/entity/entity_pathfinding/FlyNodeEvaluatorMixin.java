package dev.ryanhcode.sable.mixin.entity.entity_pathfinding;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.FlyNodeEvaluator;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(FlyNodeEvaluator.class)
public abstract class FlyNodeEvaluatorMixin extends NodeEvaluator {

    @Inject(method = "getStart", at = @At("HEAD"))
    private void sable$init(final CallbackInfoReturnable<Node> cir, @Share("mobPosition") final LocalRef<Vec3> mobPosition) {
        final SubLevel trackingSubLevel = Sable.HELPER.getTrackingSubLevel(this.mob);

        if (trackingSubLevel != null) {
            mobPosition.set(trackingSubLevel.logicalPose().transformPositionInverse(this.mob.position()));
        } else {
            mobPosition.set(this.mob.position());
        }
    }

    @Redirect(method = "getStart", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;getBlockY()I"))
    private int sable$redirectGetBlockY(final Mob mob, @Share("mobPosition") final LocalRef<Vec3> mobPosition) {
        return Mth.floor(mobPosition.get().y);
    }

    @Redirect(method = "getStart", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;getX()D"))
    private double sable$redirectGetX(final Mob mob, @Share("mobPosition") final LocalRef<Vec3> mobPosition) {
        return mobPosition.get().x;
    }

    @Redirect(method = "getStart", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;getY()D"))
    private double sable$redirectGetY(final Mob mob, @Share("mobPosition") final LocalRef<Vec3> mobPosition) {
        return mobPosition.get().y;
    }

    @Redirect(method = "getStart", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;getZ()D"))
    private double sable$redirectGetZ(final Mob mob, @Share("mobPosition") final LocalRef<Vec3> mobPosition) {
        return mobPosition.get().z;
    }

    /**
     * @author RyanH
     * @reason Work on sub-levels
     */
    @Overwrite
    private Iterable<BlockPos> iteratePathfindingStartNodeCandidatePositions(final Mob mob) {
        final AABB mobBounds = mob.getBoundingBox();
        final boolean small = mobBounds.getSize() < (double) 1.0F;

        final SubLevel trackingSubLevel = Sable.HELPER.getTrackingSubLevel(this.mob);

        Vec3 localPosition = this.mob.position();

        if (trackingSubLevel != null)
            localPosition = trackingSubLevel.logicalPose().transformPositionInverse(localPosition);

        final AABB localMobBounds = mob.getBoundingBox().move(localPosition.subtract(this.mob.position()));

        if (!small) {
            final int blockY = Mth.floor(localPosition.y);
            return List.of(BlockPos.containing(localMobBounds.minX, blockY, localMobBounds.minZ),
                    BlockPos.containing(localMobBounds.minX, blockY, localMobBounds.maxZ),
                    BlockPos.containing(localMobBounds.maxX, blockY, localMobBounds.minZ),
                    BlockPos.containing(localMobBounds.maxX, blockY, localMobBounds.maxZ));
        } else {
            final double xSize = Math.max(0.0F, (double) 1.1F - mobBounds.getXsize());
            final double ySize = Math.max(0.0F, (double) 1.1F - mobBounds.getYsize());
            final double zSize = Math.max(0.0F, (double) 1.1F - mobBounds.getZsize());
            final AABB localBounds = localMobBounds.inflate(xSize, ySize, zSize);
            return BlockPos.randomBetweenClosed(mob.getRandom(), 10, Mth.floor(localBounds.minX), Mth.floor(localBounds.minY), Mth.floor(localBounds.minZ), Mth.floor(localBounds.maxX), Mth.floor(localBounds.maxY), Mth.floor(localBounds.maxZ));
        }
    }
}
