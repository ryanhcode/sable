package dev.ryanhcode.sable;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixin.config.GameRendererAccessor;
import dev.ryanhcode.sable.mixinterface.plot.SubLevelContainerHolder;
import dev.ryanhcode.sable.render.dynamic_shade.SableDynamicDirectionalShading;
import dev.ryanhcode.sable.render.sky_light_shadow.SableSkyLightShadows;
import dev.ryanhcode.sable.render.water_occlusion.WaterOcclusionRenderer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.render.SubLevelRenderer;
import foundry.veil.Veil;
import foundry.veil.api.client.render.VeilRenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.ApiStatus;

import java.util.Arrays;

public final class SableClientConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue ATTEMPT_UDP_NETWORKING;
    public static final ModConfigSpec.BooleanValue SUB_LEVEL_DYNAMIC_SHADING;
    public static final ModConfigSpec.BooleanValue SUB_LEVEL_WATER_OCCLUSION;
    public static final ModConfigSpec.BooleanValue SUB_LEVEL_SKYLIGHT_SHADOWS;
    public static final ModConfigSpec.BooleanValue DEBUG_DRAW_LOADED_CHUNKS;
    public static final ModConfigSpec.DoubleValue INTERPOLATION_DELAY;
    public static final ModConfigSpec.EnumValue<SubLevelRenderer.SelectedRenderer> SELECTED_RENDERER;
    public static final ModConfigSpec.DoubleValue ZOOM_SENSITIVITY;

    static {
        final ModConfigSpec.Builder builder = new ModConfigSpec.Builder();


        SUB_LEVEL_DYNAMIC_SHADING = builder
                .comment("Whether sub-levels should apply block shading dynamically")
                .define("sub_level_dynamic_shading", true);
        SUB_LEVEL_WATER_OCCLUSION = builder
                .comment("Whether sub-levels can occlude the water surface")
                .define("sub_level_water_occlusion", true);
        SUB_LEVEL_SKYLIGHT_SHADOWS = builder
                .comment("Whether sub-levels should cast a shadow on the world")
                .define("sub_level_skylight_shadows", false);
        DEBUG_DRAW_LOADED_CHUNKS = builder
                .comment("Whether to draw loaded chunks on the client in the chunk debug renderer")
                .define("debug_draw_loaded_chunks", Veil.platform().isDevelopmentEnvironment());
        INTERPOLATION_DELAY = builder
                .comment("The distance back in game-ticks that the snapshot interpolation should operate")
                .defineInRange("sub_level_snapshot_interpolation_delay_ticks", 1.5, 0.0, 100.0);
        SELECTED_RENDERER = builder
                .comment("The renderer to use for sub-levels")
                .defineEnum("sub_level_renderer", SubLevelRenderer.DEFAULT, Arrays.stream(SubLevelRenderer.SelectedRenderer.values())
                        .filter(SubLevelRenderer.SelectedRenderer::isSupported)
                        .toArray(SubLevelRenderer.SelectedRenderer[]::new));
        ZOOM_SENSITIVITY = builder
                .comment("The zoom sensitivity for sub-level camera types")
                .defineInRange("sub_level_zoom_sensitivity", 0.2, 0.0, 100.0);
        ATTEMPT_UDP_NETWORKING = builder
                .comment("If Sable should attempt to establish a UDP connection with the server, to receive sub-level movement data over a UDP channel")
                .define("attempt_udp_networking", true);

        SPEC = builder.build();
    }

    @ApiStatus.Internal
    public static void onUpdate(final boolean notify) {
        boolean reloadShaders = false;
        boolean reloadChunks = false;

        if (SableDynamicDirectionalShading.isEnabled() != SableClientConfig.SUB_LEVEL_DYNAMIC_SHADING.getAsBoolean()) {
            SableDynamicDirectionalShading.setIsEnabled(SableClientConfig.SUB_LEVEL_DYNAMIC_SHADING.getAsBoolean());
            reloadShaders = true;
            reloadChunks = true;
        }

        if (SableSkyLightShadows.isEnabled() != SableClientConfig.SUB_LEVEL_SKYLIGHT_SHADOWS.getAsBoolean()) {
            SableSkyLightShadows.setIsEnabled(SableClientConfig.SUB_LEVEL_SKYLIGHT_SHADOWS.getAsBoolean());
            reloadShaders = true;
        }

        if (WaterOcclusionRenderer.isEnabled() != SableClientConfig.SUB_LEVEL_WATER_OCCLUSION.getAsBoolean()) {
            WaterOcclusionRenderer.setIsEnabled(SableClientConfig.SUB_LEVEL_WATER_OCCLUSION.getAsBoolean());
            reloadShaders = true;
        }

        Minecraft.getInstance().execute(() -> SubLevelRenderer.setImpl(SableClientConfig.SELECTED_RENDERER.get()));

        if (notify) {
            if (reloadShaders) {
                VeilRenderSystem.renderer().getVanillaShaderCompiler().reload(((GameRendererAccessor) Minecraft.getInstance().gameRenderer).getShaders().values());
            }

            if (reloadChunks) {
                Minecraft.getInstance().execute(() -> {
                    VeilRenderSystem.rebuildChunks();
                    final ClientLevel level = Minecraft.getInstance().level;
                    if (level != null) {
                        final SubLevelContainer plotContainer = ((SubLevelContainerHolder) level).sable$getPlotContainer();
                        for (final SubLevel sublevel : plotContainer.getAllSubLevels()) {
                            ((ClientSubLevel) sublevel).getRenderData().rebuild();
                        }
                    }
                });
            }
        }
    }
}
