package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.frogports;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorRidingHandler;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ChainConveyorRidingHandler.class)
public class ChainConveyorRidingHandlerMixin {

    @WrapOperation(method = "clientTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;subtract(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;", ordinal = 1))
    private static Vec3 sable$fixDiff(final Vec3 targetPosition, final Vec3 playerPosition, Operation<Vec3> original) {
        return original.call(Sable.HELPER.projectOutOfSubLevel(Minecraft.getInstance().level, targetPosition), playerPosition);
    }

    @WrapOperation(method = "updateTargetPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getLookAngle()Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 sable$fixLookAngle(final LocalPlayer instance, Operation<Vec3> original, @Local(ordinal = 1) final BlockPos connection, @Local final ChainConveyorBlockEntity clbe) {
        final SubLevel subLevel = Sable.HELPER.getContaining(clbe);

        if (subLevel != null) {
            final Pose3dc pose = subLevel.logicalPose();
            final Vec3 lookAngle = original.call(instance);
            return pose.transformNormalInverse(lookAngle);
        }

        return original.call(instance);
    }

}
