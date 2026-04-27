package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.frogports;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorPackage;
import com.simibubi.create.content.kinetics.chainConveyor.ChainPackageInteractionHandler;
import com.simibubi.create.foundation.utility.RaycastHelper;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ChainPackageInteractionHandler.class)
public class ChainPackageInteractionHandlerMixin {

    @WrapOperation(method = "lambda$onUse$0", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getEyePosition()Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 sable$getTraceOrigin(final LocalPlayer instance, Operation<Vec3> original, @Local(argsOnly = true) final ChainConveyorPackage.ChainConveyorPackagePhysicsData data) {
        Vec3 origin = original.call(instance);

        final SubLevel subLevel = Sable.HELPER.getContainingClient(data.targetPos);
        if (subLevel != null) {
            origin = subLevel.logicalPose().transformPositionInverse(origin);
        }

        return origin;
    }

    @WrapOperation(method = "lambda$onUse$0", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/foundation/utility/RaycastHelper;getTraceTarget(Lnet/minecraft/world/entity/player/Player;DLnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 sable$getTraceTarget(final Player playerIn, final double range, final Vec3 from, Operation<Vec3> original, @Local(argsOnly = true) final ChainConveyorPackage.ChainConveyorPackagePhysicsData data) {
        Vec3 target = original.call(playerIn, range, from);

        final SubLevel subLevel = Sable.HELPER.getContainingClient(data.targetPos);
        if (subLevel != null) {
            target = subLevel.logicalPose().transformPositionInverse(target);
        }

        return target;
    }

}
