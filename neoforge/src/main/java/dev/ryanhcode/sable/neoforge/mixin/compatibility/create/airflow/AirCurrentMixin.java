package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.airflow;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.kinetics.fan.AirCurrent;
import com.simibubi.create.content.kinetics.fan.IAirCurrentSource;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.ref.WeakReference;

@Mixin(AirCurrent.class)
public abstract class AirCurrentMixin {

    @Shadow
    @Final
    public IAirCurrentSource source;

    @Unique
    private WeakReference<SubLevel> sable$subLevelReference;

    @Inject(method = "tick", at = @At("HEAD"))
    public void sable$updateSubLevel(final CallbackInfo ci) {
        if (this.sable$subLevelReference == null) {
            this.sable$subLevelReference = new WeakReference<>(Sable.HELPER.getContaining(this.source.getAirCurrentWorld(), this.source.getAirCurrentPos()));
        }
    }

    @WrapOperation(method = "tickAffectedEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;"))
    public AABB sable$reverseProjectEntityBB(final Entity instance, Operation<AABB> original) {
        final SubLevel subLevel = this.sable$subLevelReference.get();
        if (subLevel != null) {
            return new BoundingBox3d(original.call(instance)).transformInverse(subLevel.logicalPose(), new BoundingBox3d()).toMojang();
        }

        return original.call(instance);
    }

    @WrapOperation(method = "tickAffectedEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"))
    public void sable$transformFlowVector(final Entity instance, final Vec3 vec3, final Operation<Void> original, @Local final Vec3i flow, @Local(ordinal = 2) final float acceleration, @Local final Vec3 previousMotion, @Local(ordinal = 3) final float maxAcceleration) {
        final SubLevel subLevel = this.sable$subLevelReference.get();
        if (subLevel != null) {
            final Vector3d nonIntFlow = JOMLConversion.atLowerCornerOf(flow);
            subLevel.logicalPose().transformNormal(nonIntFlow);

            final double xIn = Mth.clamp((double) ((float) nonIntFlow.get(0) * acceleration) - previousMotion.x, -maxAcceleration, maxAcceleration);
            final double yIn = Mth.clamp((double) ((float) nonIntFlow.get(1) * acceleration) - previousMotion.y, -maxAcceleration, maxAcceleration);
            final double zIn = Mth.clamp((double) ((float) nonIntFlow.get(2) * acceleration) - previousMotion.z, -maxAcceleration, maxAcceleration);

            original.call(instance, previousMotion.add(new Vec3(xIn, yIn, zIn).scale(0.125f)));
            return;
        }

        original.call(instance, vec3);
    }

    @WrapOperation(method = "tickAffectedEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;position()Lnet/minecraft/world/phys/Vec3;"))
    public Vec3 sable$reverseProjectAllPositions(final Entity instance, Operation<Vec3> original) {
        final SubLevel subLevel = this.sable$subLevelReference.get();
        if (subLevel != null) {
            return subLevel.logicalPose().transformPositionInverse(original.call(instance));
        }

        return original.call(instance);
    }

}
