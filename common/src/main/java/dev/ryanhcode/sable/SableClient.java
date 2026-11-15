package dev.ryanhcode.sable;

import dev.ryanhcode.sable.debug.SableClientGizmoHandler;
import dev.ryanhcode.sable.network.client.SableClientNetworkEventLoop;
import dev.ryanhcode.sable.render.dynamic_shade.SableDynamicDirectionalShadingPreProcessor;
import dev.ryanhcode.sable.render.sky_light_shadow.SableDynamicSkyLightShadowPreProcessor;
import dev.ryanhcode.sable.render.sky_light_shadow.SableSkyLightShadows;
import dev.ryanhcode.sable.render.water_occlusion.SableWaterOcclusionPreProcessor;
import dev.ryanhcode.sable.render.water_occlusion.WaterOcclusionRenderer;
import dev.ryanhcode.sable.sublevel.render.fancy.FancySubLevelShaderProcessor;
import dev.ryanhcode.sable.sublevel.storage.debug.SubLevelContainerInspector;
import foundry.veil.api.client.editor.EditorManager;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.platform.VeilEventPlatform;
import net.minecraft.client.Minecraft;

/**
 * Client-side entry point for the Sable mod: registers shader pre-processors, the sky-light shadow pass, gizmo
 * handling, and the ImGui sub-level container inspector.
 */
public class SableClient {

    public static final SableClientGizmoHandler GIZMO_HANDLER = new SableClientGizmoHandler();
    public static SableClientNetworkEventLoop NETWORK_EVENT_LOOP = new SableClientNetworkEventLoop();
    public static WaterOcclusionRenderer WATER_OCCLUSION_RENDERER = new WaterOcclusionRenderer();

    public static void init() {
        VeilEventPlatform.INSTANCE.onVeilRendererAvailable(renderer -> {
            if (VeilRenderSystem.hasImGui()) {
                final EditorManager editorManager = renderer.getEditorManager();

                editorManager.add(new SubLevelContainerInspector());
            }
        });

        VeilEventPlatform.INSTANCE.onVeilAddShaderProcessors((provider, registry) -> {
            registry.addPreprocessor(new SableDynamicDirectionalShadingPreProcessor(), false);
            registry.addPreprocessor(new SableDynamicSkyLightShadowPreProcessor(), false);
            registry.addPreprocessor(new SableWaterOcclusionPreProcessor(), false);
            registry.addPreprocessor(new FancySubLevelShaderProcessor(), false);
        });

        VeilEventPlatform.INSTANCE.onVeilRenderLevelStage(SableSkyLightShadows::renderShadowMap);

        GIZMO_HANDLER.init();
    }

    public static boolean useNativeTransport() {
        final Minecraft client = Minecraft.getInstance();
        return client.options.useNativeTransport();
    }
}
