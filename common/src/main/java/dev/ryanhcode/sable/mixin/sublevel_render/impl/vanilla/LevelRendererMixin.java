package dev.ryanhcode.sable.mixin.sublevel_render.impl.vanilla;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixinterface.plot.SubLevelContainerHolder;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.SubLevelRenderData;
import dev.ryanhcode.sable.sublevel.render.dispatcher.SubLevelRenderDispatcher;
import foundry.veil.api.client.render.VeilRenderBridge;
import foundry.veil.api.client.render.rendertype.VeilRenderType;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.PrioritizeChunkUpdates;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;

/**
 * On the vanilla render path, wires sub-levels into chunk compilation, culling and chunk layer rendering.
 */
@Mixin(value = LevelRenderer.class, priority = 1002) // Higher priority to go after Flywheel
public abstract class LevelRendererMixin {

    @Shadow
    private @Nullable ClientLevel level;

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "compileSections", at = @At("TAIL"))
    private void sable$compileSections(final Camera camera, final CallbackInfo ci) {
        final Iterable<ClientSubLevel> sublevels = ((ClientSubLevelContainer) ((SubLevelContainerHolder) this.level).sable$getPlotContainer()).getAllSubLevels();
        final RenderRegionCache renderRegionCache = new RenderRegionCache();
        final PrioritizeChunkUpdates chunkUpdates = Minecraft.getInstance().options.prioritizeChunkUpdates().get();

        for (final ClientSubLevel sublevel : sublevels) {
            sublevel.getRenderData().compileSections(chunkUpdates, renderRegionCache, camera);
        }
    }

    @Inject(method = "setupRender", at = @At(value = "INVOKE_STRING", target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V", args = "ldc=update"))
    public void sable$cull(final Camera camera, final Frustum frustum, final boolean hasCapturedFrustum, final boolean isSpectator, final CallbackInfo ci) {
        if (hasCapturedFrustum) {
            return;
        }

        final SubLevelRenderDispatcher dispatcher = SubLevelRenderDispatcher.get();
        dispatcher.preRenderChunks(camera);

        final ProfilerFiller profiler = this.minecraft.getProfiler();
        profiler.push("sub_level_section_occlusion_graph");

        final Iterable<ClientSubLevel> sublevels = ((ClientSubLevelContainer) ((SubLevelContainerHolder) this.level).sable$getPlotContainer()).getAllSubLevels();
        final Vec3 cameraPosition = camera.getPosition();
        dispatcher.updateCulling(sublevels, cameraPosition.x, cameraPosition.y, cameraPosition.z, VeilRenderBridge.create(frustum), isSpectator);

        profiler.pop();
    }

    @Inject(method = "isSectionCompiled", at = @At("HEAD"), cancellable = true)
    private void sable$isSectionCompiled(final BlockPos blockPos, final CallbackInfoReturnable<Boolean> cir) {
        final ClientSubLevelContainer container = SubLevelContainer.getContainer(this.level);

        if (container == null) {
            return;
        }

        if (container.inBounds(blockPos)) {
            final ClientSubLevel subLevel = (ClientSubLevel) Sable.HELPER.getContaining(this.level, blockPos);

            if (subLevel == null) {
                cir.setReturnValue(false);
            } else {
                final SubLevelRenderData renderData = subLevel.getRenderData();
                final SectionPos sectionPos = SectionPos.of(blockPos);
                cir.setReturnValue(renderData.isSectionCompiled(sectionPos.x(), sectionPos.y(), sectionPos.z()));
            }
        }
    }

    @Inject(method = "renderSectionLayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ShaderInstance;clear()V"))
    public void sable$renderSubLevels(final RenderType renderType, final double x, final double y, final double z, final Matrix4f modelView, final Matrix4f projection, final CallbackInfo ci, @Local ShaderInstance shader) {
        final Iterable<ClientSubLevel> sublevels = ((ClientSubLevelContainer) ((SubLevelContainerHolder) this.level).sable$getPlotContainer()).getAllSubLevels();
        SubLevelRenderDispatcher.get().renderSectionLayer(sublevels, renderType, shader, x, y, z, modelView, projection, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
    }

    @Inject(method = "renderSectionLayer", at = @At("TAIL"))
    public void sable$renderSubLevelLayers(final RenderType renderType, final double x, final double y, final double z, final Matrix4f modelView, final Matrix4f projection, final CallbackInfo ci) {
        RenderType unwrappedRenderType = renderType;
        while (unwrappedRenderType instanceof final VeilRenderType.RenderTypeWrapper wrapper) {
            unwrappedRenderType = wrapper.get();
        }

        if (!(unwrappedRenderType instanceof final VeilRenderType.LayeredRenderType layered)) {
            return;
        }

        final Iterable<ClientSubLevel> sublevels = ((ClientSubLevelContainer) ((SubLevelContainerHolder) this.level).sable$getPlotContainer()).getAllSubLevels();
        final SubLevelRenderDispatcher renderDispatcher = SubLevelRenderDispatcher.get();
        for (final RenderType layer : layered.getLayers()) {
            layer.setupRenderState();
            final ShaderInstance shader = Objects.requireNonNull(RenderSystem.getShader(), "shader");
            shader.setDefaultUniforms(VertexFormat.Mode.QUADS, modelView, projection, this.minecraft.getWindow());
            shader.apply();

            renderDispatcher.renderSectionLayer(sublevels, renderType, shader, x, y, z, modelView, projection, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));

            shader.clear();
            layer.clearRenderState();
        }
    }
}
