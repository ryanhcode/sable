package dev.ryanhcode.sable.neoforge.mixin.compatibility.flywheel;

import dev.engine_room.flywheel.backend.engine.embed.EmbeddedEnvironment;
import dev.engine_room.flywheel.backend.gl.shader.GlProgram;
import dev.engine_room.flywheel.lib.util.ExtraMemoryOps;
import dev.ryanhcode.sable.neoforge.compatibility.flywheel.SableFlywheelEmbeddingUniforms;
import dev.ryanhcode.sable.neoforge.compatibility.flywheel.SableFlywheelLightStorage;
import dev.ryanhcode.sable.neoforge.mixinterface.compatibility.flywheel.EmbeddedEnvironmentExtension;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into {@link dev.engine_room.flywheel.backend.engine.embed.EmbeddedEnvironment} to write scene and sky light uniforms for sub-level embeddings.
 */

@Mixin(EmbeddedEnvironment.class)
public class EmbeddedEnvironmentMixin implements EmbeddedEnvironmentExtension {

    @Shadow
    @Final
    private Matrix4f poseComposed;

    @Unique
    private final Matrix4f sable$scene = new Matrix4f();

    @Unique
    private int sable$sceneId = SableFlywheelLightStorage.STATIC_SCENE_ID;

    @Unique
    private float sable$skyLightScale = 1.0f;

    @Override
    public void sable$setLightingInfo(final Matrix4fc sceneMatrix, final int scene, final float skyLightScale) {
        this.sable$scene.set(sceneMatrix);
        this.sable$sceneId = scene;
        this.sable$skyLightScale = skyLightScale;
    }

    @Inject(method = "setupDraw", at = @At("TAIL"))
    private void sable$setupDraw(final GlProgram program, final CallbackInfo ci) {
        program.setUInt(SableFlywheelEmbeddingUniforms.SCENE, this.sable$sceneId);
        program.setFloat(SableFlywheelEmbeddingUniforms.SKY_LIGHT_SCALE, this.sable$skyLightScale);

        if (this.sable$sceneId == 0) {
            program.setMat4(SableFlywheelEmbeddingUniforms.SCENE_MATRIX, this.poseComposed);
        } else {
            program.setMat4(SableFlywheelEmbeddingUniforms.SCENE_MATRIX, this.sable$scene);
        }
    }

    @Inject(method = "flush", at = @At("TAIL"))
    public void sable$flush(final long ptr, final CallbackInfo ci) {
        MemoryUtil.memPutFloat(ptr + 28 * Float.BYTES, this.sable$skyLightScale);
        MemoryUtil.memPutInt(ptr + 29 * Float.BYTES, this.sable$sceneId);
        MemoryUtil.memPutFloat(ptr + 30 * Float.BYTES, 0);
        MemoryUtil.memPutFloat(ptr + 31 * Float.BYTES, 0);

        final long sceneMatrixOffset = ptr + 32 * Float.BYTES;

        if (this.sable$sceneId == 0) {
            ExtraMemoryOps.putMatrix4f(sceneMatrixOffset, this.poseComposed);
        } else {
            ExtraMemoryOps.putMatrix4f(sceneMatrixOffset, this.sable$scene);
        }
    }
}
