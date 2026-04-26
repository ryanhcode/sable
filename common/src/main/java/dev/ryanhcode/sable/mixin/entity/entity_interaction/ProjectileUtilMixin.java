package dev.ryanhcode.sable.mixin.entity.entity_interaction;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.LevelPoseProviderExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Optional;

@Mixin(ProjectileUtil.class)
public class ProjectileUtilMixin {

    @WrapOperation(method = "getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"))
    private static double sable$fixDistance(final Vec3 start, final Vec3 hitPos, Operation<Double> original, @Local(argsOnly = true) final Entity source) {
        return Sable.HELPER.distanceSquaredWithSubLevels(source.level(), start, hitPos, original);
    }

    @WrapOperation(method = "getEntityHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;F)Lnet/minecraft/world/phys/EntityHitResult;", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"))
    private static double sable$fixDistance2(final Vec3 start, final Vec3 hitPos, Operation<Double> original, @Local(argsOnly = true) final Level level) {
        return Sable.HELPER.distanceSquaredWithSubLevels(level, start, hitPos, original);
    }

    @WrapOperation(method = "getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/AABB;clip(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)Ljava/util/Optional;"))
    private static Optional<Vec3> sable$getBoundingBox(final AABB toClip, final Vec3 start, final Vec3 end, Operation<Optional<Vec3>> original, @Local(argsOnly = true) final Entity source, @Local(ordinal = 2) final Entity clipping) {
        final ActiveSableCompanion helper = Sable.HELPER;
        return sable$getHitPosWithSublevels(source.level(), toClip, start, end, helper.getContaining(source.level(), start), helper.getContaining(clipping.level(), clipping.position()), original);
    }

    @WrapOperation(method = "getEntityHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;F)Lnet/minecraft/world/phys/EntityHitResult;", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/AABB;clip(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)Ljava/util/Optional;"))
    private static Optional<Vec3> sable$getBoundingBox2(final AABB toClip, final Vec3 start, final Vec3 end, Operation<Optional<Vec3>> original, @Local(argsOnly = true) final Level level, @Local(ordinal = 2) final Entity clipping) {
        final ActiveSableCompanion helper = Sable.HELPER;
        return sable$getHitPosWithSublevels(level, toClip, start, end, helper.getContaining(level, start), helper.getContaining(clipping.level(), clipping.position()), original);
    }

    @Unique
    private static @NotNull Optional<Vec3> sable$getHitPosWithSublevels(final Level level, final AABB toClip, Vec3 start, Vec3 end, final SubLevel sourceSubLevel, final SubLevel clippingSubLevel, Operation<Optional<Vec3>> vanillaCallback) {
        if (sourceSubLevel == clippingSubLevel) { // either both null, or both same
            return vanillaCallback.call(toClip, start, end);
        }

        if (level instanceof final LevelPoseProviderExtension poseProvider) {
            if (sourceSubLevel != null) {
                start = poseProvider.sable$getPose(sourceSubLevel).transformPosition(start);
                end = poseProvider.sable$getPose(sourceSubLevel).transformPosition(end);
            }

            if (clippingSubLevel != null) {
                start = poseProvider.sable$getPose(clippingSubLevel).transformPositionInverse(start);
                end = poseProvider.sable$getPose(clippingSubLevel).transformPositionInverse(end);
            }
        } else {
            if (sourceSubLevel != null) {
                start = sourceSubLevel.logicalPose().transformPosition(start);
                end = sourceSubLevel.logicalPose().transformPosition(end);
            }

            if (clippingSubLevel != null) {
                start = clippingSubLevel.logicalPose().transformPositionInverse(start);
                end = clippingSubLevel.logicalPose().transformPositionInverse(end);
            }
        }

        return vanillaCallback.call(toClip, start, end);
    }
}
