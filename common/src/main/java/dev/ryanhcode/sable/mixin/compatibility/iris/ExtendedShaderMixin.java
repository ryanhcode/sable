package dev.ryanhcode.sable.mixin.compatibility.iris;

import com.mojang.blaze3d.shaders.Uniform;
import dev.ryanhcode.sable.mixinterface.compatibility.iris.ExtendedShaderExtension;
import net.irisshaders.iris.pipeline.programs.ExtendedShader;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ExtendedShader.class)
public class ExtendedShaderMixin implements ExtendedShaderExtension {

    @Shadow
    @Final
    private Uniform modelViewInverse;

    @Shadow
    @Final
    private Uniform normalMatrix;

    @Shadow
    @Final
    private Matrix4f tempMatrix4f;

    @Shadow
    @Final
    private Matrix3f tempMatrix3f;

    @Shadow
    @Final
    private float[] tempFloats;

    @Shadow
    @Final
    private float[] tempFloats2;

    @Unique
    @Override
    public void sable$refreshModelMatrices() {
        final var modelView = ((ShaderInstance) (Object) this).MODEL_VIEW_MATRIX;

        if (modelView != null) {
            if (this.modelViewInverse != null) {
                this.modelViewInverse.set(this.tempMatrix4f.set(modelView.getFloatBuffer()).invert().get(this.tempFloats));
                this.modelViewInverse.upload();
            }

            if (this.normalMatrix != null) {
                this.normalMatrix.set(this.tempMatrix3f.set(this.tempMatrix4f.set(modelView.getFloatBuffer())).invert().transpose().get(this.tempFloats2));
                this.normalMatrix.upload();
            }
        }
    }
}
