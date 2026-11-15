package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.frogports;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.logistics.packagePort.frogport.FrogportBlock;
import com.simibubi.create.content.logistics.packagePort.frogport.FrogportBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Mixin into {@link com.simibubi.create.content.logistics.packagePort.frogport.FrogportBlock} to project the player position into a sub-level when placing a frogport.
 */

@Mixin(FrogportBlock.class)
public class FrogportBlockMixin {
    @WrapOperation(method = "lambda$setPlacedBy$0", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;position()Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 sable$projectPlayerPosition(final LivingEntity instance, final Operation<Vec3> original, @Local(argsOnly = true, name = "arg2") final FrogportBlockEntity be) {
        final SubLevel subLevel = Sable.HELPER.getContaining(be);
        if (subLevel == null) {
            return original.call(instance);
        }
        return subLevel.logicalPose().transformPositionInverse(original.call(instance));
    }
}
