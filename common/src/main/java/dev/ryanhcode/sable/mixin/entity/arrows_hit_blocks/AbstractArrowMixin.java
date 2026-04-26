package dev.ryanhcode.sable.mixin.entity.arrows_hit_blocks;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.mixinhelpers.CanFallAtleastHelper;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fixes the delta movement that arrows get & the direction they face when they hit blocks
 */
@Mixin(AbstractArrow.class)
public abstract class AbstractArrowMixin extends Entity {

    @Shadow
    protected boolean inGround;

    public AbstractArrowMixin(final EntityType<?> entityType, final Level level) {
        super(entityType, level);
    }

    @WrapOperation(method = "onHitBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/projectile/AbstractArrow;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"))
    private void sable$setDeltaMovement(final AbstractArrow arrow,
                                        final Vec3 difference,
                                        Operation<Void> original,
                                        @Local(argsOnly = true) final BlockHitResult blockHitResult,
                                        @Share("difference") final LocalRef<Vec3> differenceRef,
                                        @Share("subLevel") final LocalRef<SubLevel> subLevelRef) {
        final SubLevel subLevel = Sable.HELPER.getContaining(this.level(), blockHitResult.getLocation());

        if (subLevel == null) {
            original.call(arrow, difference);
            return;
        }

        final Vec3 localPosition = subLevel.logicalPose().transformPositionInverse(this.position());
        final Vec3 diff = blockHitResult.getLocation().subtract(localPosition);

        if (!this.level().isClientSide && !this.inGround) {
            final Vec3 localImpulse = subLevel.logicalPose().transformNormalInverse(this.getDeltaMovement());
            RigidBodyHandle.of((ServerSubLevel) subLevel).applyImpulseAtPoint(localPosition, localImpulse);
        }

        arrow.setDeltaMovement(diff.x, diff.y, diff.z);
        differenceRef.set(diff);
        subLevelRef.set(subLevel);
    }

    @WrapOperation(method = "onHitBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/projectile/AbstractArrow;setPosRaw(DDD)V"))
    private void sable$setPosRaw(final AbstractArrow instance,
                                 final double x,
                                 final double y,
                                 final double z,
                                 Operation<Void> original,
                                 @Share("subLevel") final LocalRef<SubLevel> subLevelRef,
                                 @Share("difference") final LocalRef<Vec3> differenceRef) {
        final Vec3 difference = differenceRef.get();

        if (difference == null) {
            original.call(instance, x, y, z);
            return;
        }

        final Vec3 nudge = difference.normalize().scale(0.05F);
        final SubLevel subLevel = subLevelRef.get();
        final Vec3 localPosition = subLevel.logicalPose().transformPositionInverse(this.position());

        original.call(instance, localPosition.x - nudge.x, localPosition.y - nudge.y, localPosition.z - nudge.z);

        final Vec3 vec3 = this.getDeltaMovement();
        final double d = vec3.horizontalDistance();
        this.setXRot((float) (Mth.atan2(vec3.y, d) * 57.2957763671875));
        this.setYRot((float) (Mth.atan2(vec3.x, vec3.z) * 57.2957763671875));

        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
    }

    @Inject(method = "startFalling", at = @At("TAIL"))
    private void sable$startFalling(final CallbackInfo ci) {
        final SubLevel subLevel = Sable.HELPER.getContaining(this);
        
        if (subLevel != null) {
            EntitySubLevelUtil.kickEntity(subLevel, this);
        }
    }

    @WrapOperation(method = "shouldFall", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;noCollision(Lnet/minecraft/world/phys/AABB;)Z"))
    private boolean sable$noCollision(final Level level, final AABB aabb, Operation<Boolean> orignal) {
        return orignal.call(level, aabb) && (CanFallAtleastHelper.canFallAtleastWithSubLevels(level, aabb) == null);
    }
}
