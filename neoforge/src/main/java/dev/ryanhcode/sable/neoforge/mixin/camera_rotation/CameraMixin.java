package dev.ryanhcode.sable.neoforge.mixin.camera_rotation;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinhelpers.camera.camera_rotation.EntitySubLevelRotationHelper;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ViewportEvent;
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

@Mixin(Camera.class)
public abstract class CameraMixin {

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
    private Quaternionf rotation;
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

    @Shadow
    private Entity entity;

    @Shadow protected abstract void setRotation(float f, float g, float roll);

    @Redirect(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FFF)V", ordinal = 1))
    private void sable$redirectSetRotation(final Camera camera, final float f, final float g, final float roll, @Local final ViewportEvent.ComputeCameraAngles event) {
        this.setRotation(event.getYaw() + 180.0f, -event.getPitch(), roll);
    }

    @WrapMethod(method = "setPosition(Lnet/minecraft/world/phys/Vec3;)V")
    private void sable$setPosition(final Vec3 arg, final Operation<Void> original) {
        if (this.entity == null) {
            original.call(arg);
            return;
        }
        final Level level = this.entity.level();

        final ClientSubLevel subLevel = (ClientSubLevel) Sable.HELPER.getContaining(level, arg);
        if(subLevel == null) {
            original.call(arg);
            return;
        }

        final Pose3dc pose = subLevel.renderPose();
        final Vec3 pos = pose.transformPosition(arg);
        original.call(pos);
    }

    @Inject(method = "setRotation(FFF)V", at = @At(value = "INVOKE", target = "Lorg/joml/Quaternionf;rotationYXZ(FFF)Lorg/joml/Quaternionf;", shift = At.Shift.AFTER))
    public void sable$rotateView(final float f, final float g, final float roll, final CallbackInfo ci) {
        final float pt = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        final Quaterniond ridingOrientation = EntitySubLevelRotationHelper.getEntityOrientation(this.entity, (x) -> ((ClientSubLevel) x).renderPose(), pt, EntitySubLevelRotationHelper.Type.CAMERA);

        if (ridingOrientation != null) {
            this.rotation.premul(new Quaternionf(ridingOrientation));
            FORWARDS.rotate(this.rotation, this.forwards);
            UP.rotate(this.rotation, this.up);
            LEFT.rotate(this.rotation, this.left);

            final Vector3f euler = this.rotation.getEulerAnglesYXZ(new Vector3f());
            this.yRot = -180.0f - (float) Math.toDegrees(euler.y);
            this.xRot = (float) -Math.toDegrees(euler.x);
        }
    }
}
