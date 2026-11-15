package dev.ryanhcode.sable.fabric.mixin.dynamic_directional_shading;

import com.mojang.blaze3d.vertex.VertexSorting;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixinterface.dynamic_directional_shading.ModelBlockRendererCacheExtension;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fabric mixin into {@link SectionCompiler} to switch the model renderer cache to sub-level mode while compiling
 * chunk sections that belong to a sub-level plot.
 */
@Mixin(SectionCompiler.class)
public class SectionCompilerMixin {

    @Inject(method = "compile", at = @At(value = "HEAD"))
    private void sable$preCompile(final SectionPos sectionPos, final RenderChunkRegion renderChunkRegion, final VertexSorting vertexSorting, final SectionBufferBuilderPack sectionBufferBuilderPack, final CallbackInfoReturnable<SectionCompiler.Results> cir) {
        final ClientLevel level = Minecraft.getInstance().level;
        final SubLevelContainer container = SubLevelContainer.getContainer(level);

        final LevelPlot plot = container.getPlot(sectionPos.chunk());

        ((ModelBlockRendererCacheExtension) ModelBlockRenderer.CACHE.get()).sable$setOnSubLevel(plot != null);
    }

    @Inject(method = "compile", at = @At("TAIL"))
    private void sable$postCompile(final SectionPos sectionPos, final RenderChunkRegion renderChunkRegion, final VertexSorting vertexSorting, final SectionBufferBuilderPack sectionBufferBuilderPack, final CallbackInfoReturnable<SectionCompiler.Results> cir) {
        ((ModelBlockRendererCacheExtension) ModelBlockRenderer.CACHE.get()).sable$setOnSubLevel(false);
    }

}
