package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.raycast;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.foundation.utility.RaycastHelper;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;

import static dev.ryanhcode.sable.neoforge.mixinhelper.compatibility.create.raycasts.SableRaycastHelper.rayCastUntilWithSublevels;

/**
 * Fixes Create's {@link RaycastHelper} to take into account sub-levels in raycasts
 */
@Mixin(RaycastHelper.class)
public class RaycastHelperMixin {


    @WrapOperation(method = "getTraceTarget", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;add(DDD)Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 sable$rotateWithSublevels(final Vec3 instance, final double pX, final double pY, final double pZ, Operation<Vec3> original, @Local(argsOnly = true) final Player player) {
        Vec3 resultTarget = new Vec3(pX, pY, pZ);

        final Entity vehicle = player.getVehicle();
        if (vehicle != null) {
            final SubLevel vehicleSubLevel = Sable.HELPER.getContaining(player.level(), vehicle.position());

            // Rotate the target if the player is in a vehicle
            if (vehicleSubLevel != null) {
                final Vector3d vec = JOMLConversion.toJOML(resultTarget);

                vehicleSubLevel.logicalPose().orientation().transform(vec);
                resultTarget = JOMLConversion.toMojang(vec);
            }
        }

        return original.call(instance, resultTarget.x, resultTarget.y, resultTarget.z);
    }

    @Inject(method = "rayTraceUntil(Lnet/minecraft/world/entity/player/Player;DLjava/util/function/Predicate;)Lcom/simibubi/create/foundation/utility/RaycastHelper$PredicateTraceResult;",
            at = @At(value = "HEAD"),
            remap = false, cancellable = true)
    private static void sable$rayTraceSublevels(final Player playerIn, final double range, final Predicate<BlockPos> predicate, final CallbackInfoReturnable<RaycastHelper.PredicateTraceResult> cir) {
        final Vec3 start = playerIn.getEyePosition();
        final Vec3 end = RaycastHelper.getTraceTarget(playerIn, range, start);

        cir.setReturnValue(rayCastUntilWithSublevels(playerIn.level(), start, end, predicate));
    }
}
