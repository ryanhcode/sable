package dev.ryanhcode.sable.mixin.entity.entities_stick_sublevels.effects;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Mixin into {@link net.minecraft.world.entity.LivingEntity} so fall particles spawn correctly when landing on sub-level blocks.
 */
@Mixin(LivingEntity.class)
public class LivingEntityMixin {

    /**
     * Make the fall particles work on when falling on sub-level blocks. I don't like that we don't keep track of
     * supporting blocks for sub-levels, but I can change that later.
     */
    @WrapOperation(method = "checkFallDamage", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;blockPosition()Lnet/minecraft/core/BlockPos;"))
    private BlockPos sable$fallDamageParticlesPosition(final LivingEntity instance, final Operation<BlockPos> original, @Local(argsOnly = true) final BlockPos blockPos) {
        if (Sable.HELPER.getContaining(instance.level(), blockPos) != null) {
            return blockPos;
        }

        return original.call(instance);
    }
}
