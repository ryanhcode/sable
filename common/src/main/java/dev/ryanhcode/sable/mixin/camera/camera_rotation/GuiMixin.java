package dev.ryanhcode.sable.mixin.camera.camera_rotation;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import dev.ryanhcode.sable.mixinhelpers.camera.camera_rotation.EntitySubLevelRotationHelper;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fix f3 crosshair when riding entity in sub-level
 */
@Mixin(Gui.class)
public class GuiMixin {

    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "renderCrosshair", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;getModelViewStack()Lorg/joml/Matrix4fStack;"))
    private void sable$onRenderCrosshair(final CallbackInfo ci, @Share("mountedOrientation") final LocalRef<Quaterniond> mountedOrientation) {
        final Camera camera = this.minecraft.gameRenderer.getMainCamera();
        final Entity entity = camera.getEntity();

        final float pt = this.minecraft.getTimer().getGameTimeDeltaPartialTick(true);
        final Quaterniond ridingOrientation = EntitySubLevelRotationHelper.getEntityOrientation(entity, (x) -> ((ClientSubLevel) x).renderPose(), pt, EntitySubLevelRotationHelper.Type.CAMERA);
        mountedOrientation.set(ridingOrientation);
    }

    @ModifyArg(method = "renderCrosshair", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4fStack;rotateX(F)Lorg/joml/Matrix4f;"), index = 0)
    private float sable$modifyRotateXAngle(final float angle, @Share("mountedOrientation") final LocalRef<Quaterniond> mountedOrientation) {
        if (mountedOrientation.get() != null) {
            final float pt = this.minecraft.getTimer().getGameTimeDeltaPartialTick(true);
            final Camera camera = this.minecraft.gameRenderer.getMainCamera();
            final Entity entity = camera.getEntity();

            return -entity.getViewXRot(pt) * (float) (Math.PI / 180.0);
        }

        return angle;
    }

    @ModifyArg(method = "renderCrosshair", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4fStack;rotateY(F)Lorg/joml/Matrix4f;"), index = 0)
    private float sable$modifyRotateYAngle(final float angle, @Share("mountedOrientation") final LocalRef<Quaterniond> mountedOrientation) {
        if (mountedOrientation.get() != null) {
            final float pt = this.minecraft.getTimer().getGameTimeDeltaPartialTick(true);
            final Camera camera = this.minecraft.gameRenderer.getMainCamera();
            final Entity entity = camera.getEntity();

            return entity.getViewYRot(pt) * (float) (Math.PI / 180.0);
        }

        return angle;
    }

    @ModifyExpressionValue(method = "renderCrosshair", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4fStack;rotateY(F)Lorg/joml/Matrix4f;"))
    private Matrix4f sable$modifyRotateY(final Matrix4f original, @Share("mountedOrientation") final LocalRef<Quaterniond> mountedOrientation) {
        if (mountedOrientation.get() != null) {
            return original.rotate(new Quaternionf(mountedOrientation.get()).conjugate());
        }

        return original;
    }

}
