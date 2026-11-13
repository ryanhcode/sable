package dev.ryanhcode.sable.fabric.mixin.camera_rotation;

import dev.ryanhcode.sable.mixinhelpers.camera.camera_rotation.EntitySubLevelRotationHelper;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fabric mixin into {@link Camera} to rotate the player camera with the sub-level they are riding or standing on.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    @Final
    private Quaternionf rotation;

    @Shadow
    @Final
    private static Vector3f FORWARDS;

    @Shadow
    @Final
    private static Vector3f UP;

    @Shadow
    @Final
    private static Vector3f LEFT;

    @Shadow
    @Final
    private Vector3f left;

    @Shadow
    @Final
    private Vector3f up;

    @Shadow
    @Final
    private Vector3f forwards;

    @Shadow
    private float yRot;

    @Shadow
    private float xRot;

    @Shadow private Entity entity;

    @Shadow @Deprecated protected abstract void setRotation(float f, float g);

    @Redirect(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V", ordinal = 1))
    private void sable$redirectSetRotation(final Camera camera, final float f, final float g) {
        this.setRotation(this.entity.getViewYRot(f) + 180.0f, -this.entity.getViewXRot(f));
    }

    @Inject(method = "setRotation", at = @At(value = "INVOKE", target = "Lorg/joml/Quaternionf;rotationYXZ(FFF)Lorg/joml/Quaternionf;", shift = At.Shift.AFTER, remap = false))
    public void sable$rotateView(final float f, final float g, final CallbackInfo ci) {
        final float pt = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        final Quaterniond ridingOrientation = EntitySubLevelRotationHelper.getEntityOrientation(this.entity, (x) -> ((ClientSubLevel) x).renderPose(), pt, EntitySubLevelRotationHelper.Type.CAMERA);

        if (ridingOrientation != null) {
            this.rotation.premul(new Quaternionf(ridingOrientation));
            FORWARDS.rotate(this.rotation, this.forwards);
            UP.rotate(this.rotation, this.up);
            LEFT.rotate(this.rotation, this.left);

            final Vector3f euler = this.rotation.getEulerAnglesYXZ(new Vector3f());
            this.yRot = (float) Math.toDegrees(euler.y);
            this.xRot = (float) Math.toDegrees(euler.x);
        }
    }
}
