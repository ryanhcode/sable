package dev.ryanhcode.sable.mixin.entity.entity_rotations_and_riding;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity{

    @Shadow protected abstract float getJumpPower();

    public LivingEntityMixin(final EntityType<?> entityType, final Level level) {
        super(entityType, level);
    }

    @Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true)
    public void sable$jumpFromGround(final CallbackInfo ci) {
        final Quaterniondc orientation = EntitySubLevelUtil.getCustomEntityOrientation(this, 1.0f);
        if (orientation == null) return;

        final float power = this.getJumpPower();
        if (!(power <= 1.0E-5F)) {
            final Vector3d deltaMovement = JOMLConversion.toJOML(this.getDeltaMovement());
            final Vector3d up = orientation.transform(OrientedBoundingBox3d.UP, new Vector3d());
            deltaMovement.fma(-up.dot(deltaMovement), up).fma(power, up);
            this.setDeltaMovement(deltaMovement.x, deltaMovement.y, deltaMovement.z);

            if (this.isSprinting()) {
                final float yRot = this.getYRot() * (float) (Math.PI / 180.0);
                final Vec3 horizontalImpulse = new Vec3((double) (-Mth.sin(yRot)) * 0.2, 0.0, (double) Mth.cos(yRot) * 0.2);
                this.addDeltaMovement(JOMLConversion.toMojang(orientation.transform(JOMLConversion.toJOML(horizontalImpulse))));
            }

            this.hasImpulse = true;
        }

        ci.cancel();
    }

    @WrapOperation(method = "dismountVehicle", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;dismountTo(DDD)V"))
    public void sable$onDismountVehicle(final LivingEntity instance, final double x, final double y, final double z, final Operation<Void> original) {
        final Vec3 dismountPosition = new Vec3(x, y, z);
        final SubLevel subLevel = Sable.HELPER.getContaining(instance.level(), dismountPosition);

        if (subLevel == null) {
            original.call(instance, x, y, z);
            return;
        }

        final Vec3 pos = subLevel.logicalPose().transformPosition(dismountPosition);
        original.call(instance, pos.x, pos.y, pos.z);
    }

    @WrapOperation(method = "dismountVehicle", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(DD)D"))
    public double sable$maxAltitude(final double a, final double b, Operation<Double> original, @Local(argsOnly = true) final Entity vehicle) {
        final Vec3 vehiclePos = vehicle.position();
        final SubLevel subLevel = Sable.HELPER.getContaining(vehicle);

        if (subLevel != null) {
            return original.call(this.getY(), subLevel.logicalPose().transformPosition(vehiclePos).y);
        } else {
            return original.call(a, b);
        }
    }

}
