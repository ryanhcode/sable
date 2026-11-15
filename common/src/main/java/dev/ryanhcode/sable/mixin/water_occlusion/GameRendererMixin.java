package dev.ryanhcode.sable.mixin.water_occlusion;

import dev.ryanhcode.sable.SableClient;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Updates the water occlusion manager at the start of each frame render.
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(method = "render", at = @At("HEAD"))
    public void sable$updateWaterOcclusionManager(final DeltaTracker deltaTracker, final boolean bl, final CallbackInfo ci) {
        SableClient.WATER_OCCLUSION_RENDERER.update();
    }
}
