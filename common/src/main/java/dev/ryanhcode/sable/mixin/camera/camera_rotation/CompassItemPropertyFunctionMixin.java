package dev.ryanhcode.sable.mixin.camera.camera_rotation;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.renderer.item.CompassItemPropertyFunction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(CompassItemPropertyFunction.class)
public abstract class CompassItemPropertyFunctionMixin {

    @WrapOperation(method = "getAngleFromEntityToPos", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;atCenterOf(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 centerBlockPos(Vec3i toCopy, Operation<Vec3> original, @Local(ordinal = 0, argsOnly = true) final Entity entity, @Share("subLevel") final LocalRef<SubLevel> sb) {
        final ActiveSableCompanion helper = Sable.HELPER;
        sb.set(helper.getContaining(entity));
        if (sb.get() == null) {
            final Entity vehicle = entity.getVehicle();

            if (vehicle != null) {
                sb.set(helper.getContaining(vehicle));
            }
        }

        if (sb.get() != null) {
            return sb.get().lastPose().transformPositionInverse(original.call(toCopy));
        }
        return original.call(toCopy);
    }

    @WrapOperation(method = "getAngleFromEntityToPos", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getX()D"))
    private double entityGetX(Entity instance, Operation<Double> original, @Share("subLevel") final LocalRef<SubLevel> sb, @Share("tranformedPos") final LocalRef<Vec3> pos) {
        if (sb.get() != null) {
            if (pos.get() == null)
                pos.set(sb.get().lastPose().transformPositionInverse(instance.position()));
            return pos.get().x;
        }
        return original.call(instance);
    }

    @WrapOperation(method = "getAngleFromEntityToPos", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getZ()D"))
    private double entityGetZ(Entity instance, Operation<Double> original, @Share("subLevel") final LocalRef<SubLevel> sb, @Share("tranformedPos") final LocalRef<Vec3> pos) {
        if (sb.get() != null) {
            if (pos.get() == null)
                pos.set(sb.get().lastPose().transformPositionInverse(instance.position()));
            return pos.get().z;
        }
        return original.call(instance);
    }
}
