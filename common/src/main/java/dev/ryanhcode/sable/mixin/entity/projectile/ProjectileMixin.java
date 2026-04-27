package dev.ryanhcode.sable.mixin.entity.projectile;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.LivingEntityMovementExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes projectiles acquire the shell velocity of their shooter
 */
@Mixin(Projectile.class)
public abstract class ProjectileMixin extends Entity {

    public ProjectileMixin(final EntityType<?> entityType, final Level level) {
        super(entityType, level);
    }

    @WrapOperation(method = "shootFromRotation", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/projectile/Projectile;shoot(DDDFF)V"))
    private void sable$zeroVelocityBeforeShooting(final Projectile instance, final double x, final double y, final double z, final float velocity, final float inaccuracy, final Operation<Void> original, @Local(argsOnly = true) final Entity shooter) {
        final SubLevel containing = Sable.HELPER.getVehicleSubLevel(shooter);

        if (containing == null) {
            original.call(instance, x, y, z, velocity, inaccuracy);
            return;
        }

        final Vector3d out = containing.logicalPose().transformNormal(new Vector3d(x, y, z));
        original.call(instance, out.x, out.y, out.z, velocity, inaccuracy);
    }

    @Inject(method = "shootFromRotation", at = @At("TAIL"))
    private void sable$shootFromRotation(final Entity entity, final float x, final float y, final float z, final float i, final float j, final CallbackInfo ci) {
        if (entity instanceof final LivingEntityMovementExtension extension) {
            this.setDeltaMovement(this.getDeltaMovement().add(JOMLConversion.toMojang(extension.sable$getInheritedVelocity())));
        }
    }

}
