package dev.ryanhcode.sable.neoforge.mixin.dynamic_directional_shading;

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
import net.neoforged.neoforge.client.event.AddSectionGeometryEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Mixin into {@link net.minecraft.client.renderer.chunk.SectionCompiler} to mark sub-level sections to enable dynamic directional shading.
 */

@Mixin(SectionCompiler.class)
public class SectionCompilerMixin {

    @Inject(method = "compile(Lnet/minecraft/core/SectionPos;Lnet/minecraft/client/renderer/chunk/RenderChunkRegion;Lcom/mojang/blaze3d/vertex/VertexSorting;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;Ljava/util/List;)Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;", at = @At(value = "HEAD"))
    private void sable$preCompile(final SectionPos sectionPos, final RenderChunkRegion region, final VertexSorting sorting, final SectionBufferBuilderPack pack, final List<AddSectionGeometryEvent.AdditionalSectionRenderer> list, final CallbackInfoReturnable<SectionCompiler.Results> cir) {
        final ClientLevel level = Minecraft.getInstance().level;
        final SubLevelContainer container = SubLevelContainer.getContainer(level);

        final LevelPlot plot = container.getPlot(sectionPos.chunk());

        ((ModelBlockRendererCacheExtension) ModelBlockRenderer.CACHE.get()).sable$setOnSubLevel(plot != null);
    }

    @Inject(method = "compile(Lnet/minecraft/core/SectionPos;Lnet/minecraft/client/renderer/chunk/RenderChunkRegion;Lcom/mojang/blaze3d/vertex/VertexSorting;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;Ljava/util/List;)Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;", at = @At("TAIL"))
    private void sable$postCompile(final SectionPos arg, final RenderChunkRegion arg2, final VertexSorting arg3, final SectionBufferBuilderPack arg4, final List<AddSectionGeometryEvent.AdditionalSectionRenderer> additionalRenderers, final CallbackInfoReturnable<SectionCompiler.Results> cir) {
        ((ModelBlockRendererCacheExtension) ModelBlockRenderer.CACHE.get()).sable$setOnSubLevel(false);
    }

}
