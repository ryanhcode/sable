package dev.ryanhcode.sable.fabric.mixin.block_outline_render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinhelpers.block_outline_render.SubLevelCamera;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniondc;
import org.joml.Quaternionf;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Debug;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Transforms block hover outlines for sublevels.
 */
@Debug(export = true)
@Mixin(value = LevelRenderer.class, priority = 400)
// Make sure this applies first so the camera can be modified
public abstract class LevelRendererMixin {

    // Storage vectors to avoid repeated allocation
    private final @Unique Quaternionf sable$orientationStorage = new Quaternionf();
    private final @Unique SubLevelCamera sable$sublevelCamera = new SubLevelCamera();

    @Shadow
    @Nullable
    private ClientLevel level;

    @Inject(method = "renderLevel", at = @At("HEAD"))
    public void modifyCamera(final CallbackInfo ci, @Local(argsOnly = true) final LocalRef<Camera> cameraRef) {
        this.sable$sublevelCamera.setCamera(cameraRef.get());
        this.sable$sublevelCamera.setPose(null);
        cameraRef.set(this.sable$sublevelCamera);
    }

    @Inject(method = "renderLevel", at = @At("TAIL"))
    public void clearCamera(final CallbackInfo ci, @Local(argsOnly = true) final LocalRef<Camera> cameraRef) {
        // This is important to make sure events fired after this mixin still have access to the camera
        cameraRef.set(this.sable$sublevelCamera.getRenderCamera());
        this.sable$sublevelCamera.clear();
    }

    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderHitOutline(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/entity/Entity;DDDLnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V"))
    private void sable$preRenderHitOutline(final LevelRenderer instance, final PoseStack poseStack, final VertexConsumer consumer, final Entity entity, final double camX, final double camY, final double camZ, final BlockPos pos, final BlockState state, final Operation<Void> original, @Local(argsOnly = true) final Camera camera) {
        final ClientSubLevel subLevel = (ClientSubLevel) Sable.HELPER.getContaining(this.level, pos);

        if (subLevel == null) {
            original.call(instance, poseStack, consumer, entity, camX, camY, camZ, pos, state);
            return;
        }

        poseStack.pushPose();

        final Pose3dc pose = subLevel.renderPose();

        this.sable$sublevelCamera.setPose(pose);
        final Vec3 cameraPosition = this.sable$sublevelCamera.getPosition();

        final Vector3dc position = pose.position();
        final Vector3dc rotationPoint = pose.rotationPoint();
        final Quaterniondc orientation = pose.orientation();
        final Vector3dc scale = pose.scale();

        poseStack.translate(
                (float) (position.x() - camX),
                (float) (position.y() - camY),
                (float) (position.z() - camZ)
        );
        poseStack.mulPose(this.sable$orientationStorage.set(orientation));
        poseStack.translate(
                (float) -(rotationPoint.x() - cameraPosition.x),
                (float) -(rotationPoint.y() - cameraPosition.y),
                (float) -(rotationPoint.z() - cameraPosition.z)
        );
        poseStack.scale((float) scale.x(), (float) scale.y(), (float) scale.z());

        original.call(instance, poseStack, consumer, entity, cameraPosition.x, cameraPosition.y, cameraPosition.z, pos, state);

        poseStack.popPose();

        this.sable$sublevelCamera.setPose(null);
    }
}
