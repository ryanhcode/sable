package dev.ryanhcode.sable.mixin.plot.lighting;

import dev.ryanhcode.sable.render.light_bridge.VirtualLightManager;
import net.minecraft.world.level.lighting.BlockLightSectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockLightSectionStorage.class)
public abstract class BlockLightEngineMixin {

    @Inject(method = "getLightValue", at = @At("RETURN"), cancellable = true)
    private void sable$injectVirtualBlockLight(final long levelPos, final CallbackInfoReturnable<Integer> cir) {
        final VirtualLightManager vlm = VirtualLightManager.get();
        if (vlm.isSampling()) return;
        if (!vlm.hasAnyLights()) return;

        final int virtualLight = vlm.getVirtualLight(levelPos);
        if (virtualLight > cir.getReturnValueI()) {
            cir.setReturnValue(virtualLight);
        }
    }
}
