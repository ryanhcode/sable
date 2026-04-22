package dev.ryanhcode.sable.mixin.plot.lighting.sodium;

import dev.ryanhcode.sable.render.light_bridge.VirtualLightManager;
import net.caffeinemc.mods.sodium.client.world.LevelSlice;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LevelSlice.class, remap = false)
public abstract class LevelSliceMixin {

    @Inject(method = "getBrightness", at = @At("RETURN"), cancellable = true, remap = true)
    private void sable$injectVirtualLight(final LightLayer type, final BlockPos pos, final CallbackInfoReturnable<Integer> cir) {
        if (type != LightLayer.BLOCK) return;

        final VirtualLightManager vlm = VirtualLightManager.get();
        if (!vlm.hasAnyLights()) return;

        final int virtualLight = vlm.getVirtualLight(pos);
        if (virtualLight > cir.getReturnValueI()) {
            cir.setReturnValue(virtualLight);
        }
    }
}
