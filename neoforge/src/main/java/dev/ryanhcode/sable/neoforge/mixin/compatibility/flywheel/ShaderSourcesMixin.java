package dev.ryanhcode.sable.neoforge.mixin.compatibility.flywheel;

import dev.engine_room.flywheel.backend.glsl.ShaderSources;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Mixin into {@link dev.engine_room.flywheel.backend.glsl.ShaderSources} to load shader sources used for sub-level rendering.
 */

@Mixin(ShaderSources.class)
public class ShaderSourcesMixin {
}
