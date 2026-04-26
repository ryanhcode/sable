package dev.ryanhcode.sable.mixin.debug_render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DebugRenderer.class)
public class DebugRendererMixin {

    @Inject(method = "renderFilledBox(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/core/BlockPos;FFFFF)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getPosition()Lnet/minecraft/world/phys/Vec3;"))
    private static void getSubLevel(PoseStack poseStack, MultiBufferSource bufferSource, BlockPos pos, float scale, float red, float green, float blue, float alpha, CallbackInfo ci, @Share("subLevel") LocalRef<ClientSubLevel> sb) {
        sb.set(Sable.HELPER.getContainingClient(pos));
    }

    @WrapOperation(method = "renderFilledBox(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/core/BlockPos;FFFFF)V",
        at = @At(value = "NEW", target = "(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/AABB;"))
    private static AABB returnEmptyAABB(BlockPos pos, Operation<AABB> original, @Share("subLevel") LocalRef<ClientSubLevel> sb) {
        if (sb.get() != null)
            return new AABB(0, 0, 0, 0, 0, 0);
        return original.call(pos);
    }

    @WrapOperation(method = "renderFilledBox(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/core/BlockPos;FFFFF)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/AABB;move(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/AABB;"))
    private static AABB inflateEmptyAABB(AABB instance, Vec3 vec, Operation<AABB> original, @Share("subLevel") LocalRef<ClientSubLevel> sb) {
        if (sb.get() != null)
            return instance.inflate(0.5);
        return original.call(instance, vec);
    }

    @WrapOperation(method = "renderFilledBox(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/core/BlockPos;FFFFF)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/debug/DebugRenderer;renderFilledBox(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/phys/AABB;FFFF)V"))
    private static void handleModifyingPoseStack(PoseStack poseStack, MultiBufferSource bufferSource, AABB boundingBox, float red, float green, float blue, float alpha, Operation<Void> original, @Share("subLevel") LocalRef<ClientSubLevel> sb, @Local(argsOnly = true) BlockPos blockPos) {
        if (sb.get() != null) {
            final Pose3dc renderPose = sb.get().renderPose();
            final Vec3 pos = renderPose.transformPosition(blockPos.getCenter()).subtract(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
            poseStack.translate(pos.x, pos.y, pos.z);
            poseStack.mulPose(new Quaternionf(renderPose.orientation()));
            original.call(poseStack, bufferSource, boundingBox, red, green, blue, alpha);
            poseStack.popPose();
        }
        original.call(poseStack, bufferSource, boundingBox, red, green, blue, alpha);
    }
}
