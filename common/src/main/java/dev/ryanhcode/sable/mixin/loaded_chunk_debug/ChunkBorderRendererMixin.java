package dev.ryanhcode.sable.mixin.loaded_chunk_debug;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.SableClientConfig;
import dev.ryanhcode.sable.mixinhelpers.loaded_chunk_debug.SableChunkDebugRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.debug.ChunkBorderRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkBorderRenderer.class)
public class ChunkBorderRendererMixin {

    @Inject(at = @At("HEAD"), method = "render", cancellable = true)
    public void render(final PoseStack poseStack, final MultiBufferSource bufferSource, final double camX, final double camY, final double camZ, final CallbackInfo ci) {
        if (SableClientConfig.DEBUG_DRAW_LOADED_CHUNKS.getAsBoolean()) {
            ci.cancel();
            SableChunkDebugRenderer.render(poseStack, bufferSource, camX, camY, camZ);
        }
    }
}
