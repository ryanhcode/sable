package dev.ryanhcode.sable.mixin.entity.entity_pathfinding;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(FlyNodeEvaluator.class)
public abstract class FlyNodeEvaluatorMixin extends NodeEvaluator {

    @Inject(method = "getStart", at = @At("HEAD"))
    private void sable$init(final CallbackInfoReturnable<Node> cir, @Share("mobPosition") final LocalRef<Vec3> mobPosition) {
        final SubLevel trackingSubLevel = Sable.HELPER.getTrackingSubLevel(this.mob);

        if (trackingSubLevel != null) {
            mobPosition.set(trackingSubLevel.logicalPose().transformPositionInverse(this.mob.position()));
        }
    }

    @WrapOperation(method = "getStart", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;getBlockY()I"))
    private int sable$redirectGetBlockY(final Mob mob, Operation<Integer> original, @Share("mobPosition") final LocalRef<Vec3> mobPosition) {
        if (mobPosition.get() == null)
            return original.call(mob);
        return Mth.floor(mobPosition.get().y);
    }

    @WrapOperation(method = "getStart", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;getX()D"))
    private double sable$redirectGetX(final Mob mob, Operation<Double> original, @Share("mobPosition") final LocalRef<Vec3> mobPosition) {
        if (mobPosition.get() == null)
            return original.call(mob);
        return mobPosition.get().x;
    }

    @WrapOperation(method = "getStart", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;getY()D"))
    private double sable$redirectGetY(final Mob mob, Operation<Double> original, @Share("mobPosition") final LocalRef<Vec3> mobPosition) {
        if (mobPosition.get() == null)
            return original.call(mob);
        return mobPosition.get().y;
    }

    @WrapOperation(method = "getStart", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;getZ()D"))
    private double sable$redirectGetZ(final Mob mob, Operation<Double> original, @Share("mobPosition") final LocalRef<Vec3> mobPosition) {
        if (mobPosition.get() == null)
            return original.call(mob);
        return mobPosition.get().z;
    }

    @WrapMethod(method = "iteratePathfindingStartNodeCandidatePositions")
    private Iterable<BlockPos> getEntitySublevel(Mob mob, Operation<Iterable<BlockPos>> original,
                                                 @Share("localPosition") LocalRef<Vec3> localPosition,
                                                 @Share("localMobBounds") LocalRef<AABB> localMobBounds) {
        final SubLevel trackingSubLevel = Sable.HELPER.getTrackingSubLevel(this.mob);

        if (trackingSubLevel != null) {
            localPosition.set(trackingSubLevel.logicalPose().transformPositionInverse(this.mob.position()));
            localMobBounds.set(mob.getBoundingBox().move(localPosition.get().subtract(this.mob.position())));
        }
        return original.call(mob);
    }

    @WrapOperation(method = "iteratePathfindingStartNodeCandidatePositions", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;getBoundingBox()Lnet/minecraft/world/phys/AABB;"))
    private AABB replaceAABB(Mob instance, Operation<AABB> original, @Share("localMobBounds") LocalRef<AABB> localMobBounds,
                             @Share("originalMobBounds") LocalRef<AABB> originalMobBounds) {
        if (localMobBounds.get() != null) {
            originalMobBounds.set(original.call(instance));
            return localMobBounds.get();
        }
        return original.call(instance);
    }

    @WrapOperation(method = "iteratePathfindingStartNodeCandidatePositions",
            at = {
                    @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/AABB;getXsize()D"),
                    @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/AABB;getYsize()D"),
                    @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/AABB;getZsize()D"),
                    @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/AABB;getSize()D")
            })
    private double useOriginalAABB(AABB instance, Operation<Double> original, @Share("originalMobBounds") LocalRef<AABB> originalMobBounds) {
        if (originalMobBounds.get() != null)
            return original.call(originalMobBounds.get());
        return original.call(instance);
    }
}
