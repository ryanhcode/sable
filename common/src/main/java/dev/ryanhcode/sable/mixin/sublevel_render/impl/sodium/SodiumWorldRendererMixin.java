package dev.ryanhcode.sable.mixin.sublevel_render.impl.sodium;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.mixinterface.plot.SubLevelContainerHolder;
import dev.ryanhcode.sable.mixinterface.sublevel_render.sodium.SodiumWorldRendererExtension;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.render.dispatcher.SodiumSubLevelRenderDispatcher;
import dev.ryanhcode.sable.sublevel.render.dispatcher.SubLevelRenderDispatcher;
import dev.ryanhcode.sable.sublevel.render.sodium.SodiumSubLevelRenderData;
import dev.ryanhcode.sable.sublevel.render.sodium.SubLevelRenderSectionManager;
import foundry.veil.api.client.render.VeilRenderBridge;
import foundry.veil.api.client.render.rendertype.VeilRenderType;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;
import net.caffeinemc.mods.sodium.client.gl.device.RenderDevice;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.PrioritizeChunkUpdates;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Mixin(value = SodiumWorldRenderer.class, remap = false)
public abstract class SodiumWorldRendererMixin implements SodiumWorldRendererExtension {

    @Unique
    private final Object2ObjectMap<ClientSubLevel, RenderSectionManager> sable$subLevelSectionManagers = new Object2ObjectOpenHashMap<>();
    @Shadow
    private RenderSectionManager renderSectionManager;
    @Shadow
    private ClientLevel level;
    @Shadow
    @Final
    private Minecraft client;


    @Inject(method = "unloadLevel", at = @At("HEAD"))
    private void sable$onUnloadLevel(final CallbackInfo ci) {
        for (final RenderSectionManager manager : this.sable$subLevelSectionManagers.values()) {
            manager.destroy();
        }

        this.sable$subLevelSectionManagers.clear();
    }

    @Inject(method = "scheduleTerrainUpdate", at = @At("HEAD"))
    private void sable$onScheduleTerrainUpdate(final CallbackInfo ci) {
        for (final RenderSectionManager manager : this.sable$subLevelSectionManagers.values()) {
            manager.markGraphDirty();
        }
    }

    /**
     * @author RyanH
     * @reason Account for sub-levels in the visible chunk count
     */
    @Overwrite
    public int getVisibleChunkCount() {
        int sum = this.renderSectionManager.getVisibleChunkCount();

        for (final RenderSectionManager manager : this.sable$subLevelSectionManagers.values()) {
            sum += manager.getVisibleChunkCount();
        }

        return sum;
    }

    @Inject(method = "isTerrainRenderComplete", at = @At("HEAD"), cancellable = true)
    public void sable$isTerrainRenderComplete(final CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            for (final RenderSectionManager sectionManager : this.sable$subLevelSectionManagers.values()) {
                if (!sectionManager.getBuilder().isBuildQueueEmpty()) {
                    cir.setReturnValue(false);
                    break;
                }
            }
        }
    }


    @Inject(method = "setupTerrain", at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSectionManager;markGraphDirty()V"))
    public void sable$markGraphDirty(final Camera camera, final Viewport viewport, final boolean spectator, final boolean updateChunksImmediately, final CallbackInfo ci) {
//        for (ClientSubLevel source : this.sable$subLevelSectionManagers.values()) {
//            source.doFrustumUpdate(camera, frustum);
//        }

        final Iterable<ClientSubLevel> sublevels = ((ClientSubLevelContainer) ((SubLevelContainerHolder) this.level).sable$getPlotContainer()).getAllSubLevels();
        final Vec3 cameraPosition = camera.getPosition();
        final Minecraft minecraft = Minecraft.getInstance();
        final Frustum frustum = minecraft.levelRenderer.cullingFrustum;
        SubLevelRenderDispatcher.get().updateCulling(sublevels, cameraPosition.x, cameraPosition.y, cameraPosition.z, VeilRenderBridge.create(frustum), minecraft.player.isSpectator());

        this.sable$subLevelSectionManagers.values().forEach(RenderSectionManager::markGraphDirty);
    }

    @Inject(method = "setupTerrain", at = @At("TAIL"))
    public void sable$setupTerrain(final Camera camera, final Viewport viewport, final boolean spectator, final boolean updateChunksImmediately, final CallbackInfo ci) {
        final ProfilerFiller profiler = this.client.getProfiler();

        final SubLevelRenderDispatcher dispatcher = SubLevelRenderDispatcher.get();

        if (!(dispatcher instanceof SodiumSubLevelRenderDispatcher)) {
            dispatcher.preRenderChunks(camera);

            final Iterable<ClientSubLevel> sublevels = SubLevelContainer.getContainer(this.level).getAllSubLevels();
            final RenderRegionCache renderRegionCache = new RenderRegionCache();

            final PrioritizeChunkUpdates chunkUpdates = SodiumClientMod.options().performance.alwaysDeferChunkUpdates ? PrioritizeChunkUpdates.NONE : PrioritizeChunkUpdates.NEARBY;
            for (final ClientSubLevel sublevel : sublevels) {
                sublevel.getRenderData().compileSections(chunkUpdates, renderRegionCache, camera);
            }
            return;
        }

        for (final ClientSubLevel clientSubLevel : SubLevelContainer.getContainer(this.level).getAllSubLevels()) {
            this.sable$getOrCreateSubLevelRenderSectionManager(clientSubLevel);
        }

        final ObjectIterator<Map.Entry<ClientSubLevel, RenderSectionManager>> iter = this.sable$subLevelSectionManagers.entrySet().iterator();
        while (iter.hasNext()) {
            final Map.Entry<ClientSubLevel, RenderSectionManager> entry = iter.next();
            final ClientSubLevel subLevel = entry.getKey();
            final RenderSectionManager renderSectionManager = entry.getValue();

            if (subLevel.isRemoved()) {
                renderSectionManager.destroy();
                iter.remove();
            } else {

                final Vector3d cameraPos = JOMLConversion.toJOML(camera.getPosition());
                subLevel.renderPose().transformPositionInverse(cameraPos);
                renderSectionManager.updateCameraState(cameraPos, camera);

                ((SodiumSubLevelRenderData) subLevel.getRenderData()).updateChunks(updateChunksImmediately);
            }
        }

        for (final RenderSectionManager renderSectionManager : this.sable$subLevelSectionManagers.values()) {
            profiler.push("chunk_update");
            renderSectionManager.updateChunks(updateChunksImmediately);
            profiler.popPush("chunk_upload");
            renderSectionManager.uploadChunks();

            profiler.popPush("chunk_render_lists");
            renderSectionManager.update(camera, viewport, spectator);

            if (updateChunksImmediately) {
                profiler.popPush("chunk_upload_immediately");
                renderSectionManager.uploadChunks();
            }

            profiler.popPush("chunk_render_tick");
            renderSectionManager.tickVisibleRenders();
            profiler.pop();
        }
    }

    @Inject(method = "scheduleRebuildForChunk(IIIZ)V", at = @At("TAIL"))
    public void sable$scheduleRebuildForChunk(final int x, final int y, final int z, final boolean playerChanged, final CallbackInfo ci) {
        final ClientSubLevelContainer container = SubLevelContainer.getContainer(this.level);

        if (container != null && container.inBounds(x, z)) {
            final ClientSubLevel subLevel = (ClientSubLevel) Sable.HELPER.getContaining(this.level, new ChunkPos(x, z));

            if (subLevel != null)
                subLevel.getRenderData().setDirty(x, y, z, playerChanged);
        }

        for (final RenderSectionManager manager : this.sable$subLevelSectionManagers.values()) {
            manager.scheduleRebuild(x, y, z, playerChanged);
        }
    }


//    @Inject(method = "processChunkEvents", at = @At("TAIL"))
//    private void sable$onProcessChunkEvents(CallbackInfo ci) {
//        for (RenderSectionManager manager : this.sable$subLevelSectionManagers.values()) {
//            ChunkTracker tracker = ChunkTrackerHolder.get(this.level);
//            Objects.requireNonNull(manager);
//
//            ChunkTracker.ChunkEventHandler handler = manager::onChunkAdded;
//            tracker.forEachEvent(handler, manager::onChunkRemoved);
//        }
//    }


    @Inject(method = "drawChunkLayer", at = @At("TAIL"))
    public void sable$drawRenderSources(final RenderType renderType, final ChunkRenderMatrices matrices, final double camX, final double camY, final double camZ, final CallbackInfo ci) {
        final SubLevelRenderDispatcher renderDispatcher = SubLevelRenderDispatcher.get();

        if (!(renderDispatcher instanceof SodiumSubLevelRenderDispatcher)) {
            final Minecraft minecraft = Minecraft.getInstance();
            final float partialTicks = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
            final List<ClientSubLevel> subLevels = SubLevelContainer.getContainer(this.level).getAllSubLevels();

            final Matrix4f modelView = new Matrix4f(matrices.modelView());
            final Matrix4f projection = new Matrix4f(matrices.projection());

            {
                renderType.setupRenderState();
                final ShaderInstance shader = Objects.requireNonNull(RenderSystem.getShader(), "shader");
                shader.setDefaultUniforms(VertexFormat.Mode.QUADS, modelView, projection, minecraft.getWindow());
                shader.apply();

                renderDispatcher.renderSectionLayer(subLevels, renderType, shader, camX, camY, camZ, modelView, projection, partialTicks);

                shader.clear();
                renderType.clearRenderState();
            }

            RenderType unwrappedRenderType = renderType;
            while (unwrappedRenderType instanceof final VeilRenderType.RenderTypeWrapper wrapper) {
                unwrappedRenderType = wrapper.get();
            }

            if (unwrappedRenderType instanceof final VeilRenderType.LayeredRenderType layered) {
                for (final RenderType layer : layered.getLayers()) {
                    layer.setupRenderState();
                    final ShaderInstance shader = Objects.requireNonNull(RenderSystem.getShader(), "shader");
                    shader.setDefaultUniforms(VertexFormat.Mode.QUADS, modelView, projection, minecraft.getWindow());
                    shader.apply();

                    renderDispatcher.renderSectionLayer(subLevels, layer, shader, camX, camY, camZ, modelView, projection, partialTicks);

                    shader.clear();
                    layer.clearRenderState();
                }
            }

            return;
        }

        if (renderType == RenderType.solid() || renderType == RenderType.translucent()) {
            for (final Map.Entry<ClientSubLevel, RenderSectionManager> entry : this.sable$subLevelSectionManagers.entrySet()) {
                final ClientSubLevel subLevel = entry.getKey();
                final RenderSectionManager manager = entry.getValue();

                ((SodiumSubLevelRenderData) subLevel.getRenderData()).renderAdditional();

                final SubLevelRenderSectionManager subLevelManager = (SubLevelRenderSectionManager) manager;

                subLevelManager.apply(matrices, camX, camY, camZ);
                subLevelManager.render(matrices, renderType, camX, camY, camZ);
            }
        }
    }


    @Override
    public SubLevelRenderSectionManager sable$getSubLevelRenderSectionManager(final ClientSubLevel subLevel) {
        return (SubLevelRenderSectionManager) this.sable$subLevelSectionManagers.get(subLevel);
    }

    @Override
    public void sable$freeRenderSectionManager(final ClientSubLevel subLevel) {
        final SubLevelRenderSectionManager manager = (SubLevelRenderSectionManager) this.sable$subLevelSectionManagers.remove(subLevel);
        if (manager != null) {
            manager.destroy();
        }
    }

    @Unique
    private SubLevelRenderSectionManager sable$getOrCreateSubLevelRenderSectionManager(final ClientSubLevel subLevel) {
        return (SubLevelRenderSectionManager) this.sable$subLevelSectionManagers.computeIfAbsent(subLevel, s -> {
            try (final CommandList commandList = RenderDevice.INSTANCE.createCommandList()) {
                return new SubLevelRenderSectionManager(subLevel, subLevel.getLevel(), this.client.options.getEffectiveRenderDistance(), commandList);
            }
        });
    }
}
