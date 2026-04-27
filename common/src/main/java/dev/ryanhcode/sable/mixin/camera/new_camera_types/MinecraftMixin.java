package dev.ryanhcode.sable.mixin.camera.new_camera_types;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.mixinhelpers.camera.new_camera_types.SableCameraTypes;
import dev.ryanhcode.sable.mixinterface.camera.camera_zoom.CameraZoomExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Shadow
    @Final
    public Options options;

    @Shadow
    @Nullable
    public ClientLevel level;

    @Shadow
    @Nullable
    public Entity cameraEntity;

    @Shadow
    @Nullable
    public LocalPlayer player;

    @Shadow @Final public GameRenderer gameRenderer;

    @Inject(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;setCameraType(Lnet/minecraft/client/CameraType;)V", shift = At.Shift.BEFORE))
    private void sable$preCycleCameraType(final CallbackInfo ci) {
        if (this.options.getCameraType() == SableCameraTypes.SUB_LEVEL_VIEW_UNLOCKED) {
            final Camera camera = this.gameRenderer.getMainCamera();
            ((CameraZoomExtension) camera).sable$setZoomAmount(0.0f);

            final SubLevel subLevel = Sable.HELPER.getVehicleSubLevel(this.cameraEntity);

            if (subLevel != null) {
                final Vec3 globalLookDir = subLevel.logicalPose().transformNormalInverse(this.player.getLookAngle());
                this.player.lookAt(EntityAnchorArgument.Anchor.FEET, this.player.position().add(globalLookDir));
            }
        }
    }

    @Inject(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;setCameraType(Lnet/minecraft/client/CameraType;)V", shift = At.Shift.AFTER))
    public void sable$postCycleCameraType(final CallbackInfo ci) {
        while (this.options.getCameraType() == SableCameraTypes.SUB_LEVEL_VIEW || this.options.getCameraType() == SableCameraTypes.SUB_LEVEL_VIEW_UNLOCKED) {
            final SubLevel subLevel = Sable.HELPER.getVehicleSubLevel(this.cameraEntity);
            if (subLevel != null) break;

            this.options.setCameraType(this.options.getCameraType().cycle());
        }

        final CameraType cameraType = this.options.getCameraType();

        if (cameraType == SableCameraTypes.SUB_LEVEL_VIEW) {
            this.player.displayClientMessage(Component.translatable("camera_type.sub_level_view").withColor(0xffaaaaaa), true);
        } else if (cameraType == SableCameraTypes.SUB_LEVEL_VIEW_UNLOCKED) {
            final SubLevel subLevel = Sable.HELPER.getVehicleSubLevel(this.cameraEntity);

            // View view orientation
            if (subLevel != null) {
                final Vec3 globalLookDir = subLevel.logicalPose().transformNormal(this.player.getLookAngle());
                this.player.lookAt(EntityAnchorArgument.Anchor.FEET, this.player.position().add(globalLookDir));
            }

            this.player.displayClientMessage(Component.translatable("camera_type.sub_level_view_unlocked").withColor(0xffaaaaaa), true);
        }
    }
}
