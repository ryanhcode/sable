package dev.ryanhcode.sable.mixin.adrenofix;

import com.mojang.blaze3d.shaders.Uniform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adreno X1-85 Compatibility Fix: Sets scalar uniforms for block face brightness.
 *
 * Problem: Veil uses uniform float[6] VeilBlockFaceBrightness which causes 276ms
 * stalls on Qualcomm Adreno X1-85 due to OpenGL-to-D3D12 translation layer.
 *
 * Solution: This mixin sets 6 separate scalar uniforms after the shader is applied,
 * which our patched shader (light_adreno_compatible.glsl) uses instead of the array.
 *
 * Uniform mapping:
 *   VeilBlockFaceBrightness[0] -> VeilBlockFaceBrightness_0 (DOWN)
 *   VeilBlockFaceBrightness[1] -> VeilBlockFaceBrightness_1 (UP)
 *   VeilBlockFaceBrightness[2] -> VeilBlockFaceBrightness_2 (NORTH)
 *   VeilBlockFaceBrightness[3] -> VeilBlockFaceBrightness_3 (SOUTH)
 *   VeilBlockFaceBrightness[4] -> VeilBlockFaceBrightness_4 (WEST)
 *   VeilBlockFaceBrightness[5] -> VeilBlockFaceBrightness_5 (EAST)
 */
@Mixin(ShaderInstance.class)
public class ShaderInstanceAdrenoFixMixin {

    @Unique
    private static final Direction[] ADRENO$DIRECTIONS = Direction.values();

    @Unique
    private static final String[] ADRENO$SCALAR_UNIFORM_NAMES = {
        "VeilBlockFaceBrightness_0", // DOWN (Direction.DOWN.get3DDataValue() == 0)
        "VeilBlockFaceBrightness_1", // UP (1)
        "VeilBlockFaceBrightness_2", // NORTH (2)
        "VeilBlockFaceBrightness_3", // SOUTH (3)
        "VeilBlockFaceBrightness_4", // WEST (4)
        "VeilBlockFaceBrightness_5"  // EAST (5)
    };

    /**
     * Inject after setDefaultUniforms to set scalar uniforms for Adreno compatibility.
     * This runs after Veil's setDefaultUniforms (which tries to set the array uniforms
     * and fails gracefully since our shader doesn't have them).
     */
    @Inject(
        method = "setDefaultUniforms",
        at = @At("TAIL"),
        remap = false
    )
    private void sable$setAdrenoCompatibleUniforms(CallbackInfo ci) {
        ShaderInstance self = (ShaderInstance) (Object) this;

        // Only set these if the shader has them - avoids errors on shaders without them
        // This also provides a way to disable: don't include the uniforms in shader
        boolean anyFound = false;
        for (String name : ADRENO$SCALAR_UNIFORM_NAMES) {
            Uniform uniform = self.getUniform(name);
            if (uniform != null) {
                anyFound = true;
                break;
            }
        }

        // If our scalar uniforms don't exist, this isn't an Adreno-compat shader
        if (!anyFound) {
            return;
        }

        // Get the current level for shade values
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        // Set scalar uniforms from level shade values
        for (int i = 0; i < ADRENO$DIRECTIONS.length; i++) {
            Direction dir = ADRENO$DIRECTIONS[i];
            Uniform uniform = self.getUniform(ADRENO$SCALAR_UNIFORM_NAMES[i]);
            if (uniform != null) {
                uniform.set(level.getShade(dir, true));
            }
        }
    }
}
