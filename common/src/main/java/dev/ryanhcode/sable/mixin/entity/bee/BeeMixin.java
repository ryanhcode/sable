package dev.ryanhcode.sable.mixin.entity.bee;

import dev.ryanhcode.sable.mixinhelpers.entity.bee.BeeSableHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.Bee;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Bee.class)
public abstract class BeeMixin {
    @Inject(method = "closerThan", at = @At("HEAD"), cancellable = true)
    private void sable$closerThan(final BlockPos pos, final int distance, final CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(BeeSableHooks.closerThan((Bee) (Object) this, pos, distance));
    }
}
