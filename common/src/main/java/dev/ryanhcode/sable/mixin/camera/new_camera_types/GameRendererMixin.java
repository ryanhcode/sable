package dev.ryanhcode.sable.mixin.camera.new_camera_types;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.mixinhelpers.camera.new_camera_types.SableCameraTypes;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fall back to first-person view when rendering the level if the sub-level view is invalid.
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setup(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V", shift = At.Shift.BEFORE))
    public void sable$setupCamera(final DeltaTracker deltaTracker, final CallbackInfo ci) {
        final CameraType cameraType = this.minecraft.options.getCameraType();

        if (cameraType == SableCameraTypes.SUB_LEVEL_VIEW || cameraType == SableCameraTypes.SUB_LEVEL_VIEW_UNLOCKED) {
            final Entity vehicle = this.minecraft.cameraEntity.getVehicle();

            if (vehicle != null) {
                final SubLevel subLevel = Sable.HELPER.getContaining(this.minecraft.level, vehicle.position());

                if (subLevel != null) {
                    return;
                }
            }

            this.minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        }
    }
}
