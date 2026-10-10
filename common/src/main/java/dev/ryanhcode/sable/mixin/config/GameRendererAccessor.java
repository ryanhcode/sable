package dev.ryanhcode.sable.mixin.config;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * Accessor exposing the private shaders field of {@link net.minecraft.client.renderer.GameRenderer}.
 */
@Mixin(GameRenderer.class)
public interface GameRendererAccessor {

    @Accessor
    Map<String, ShaderInstance> getShaders();
}
