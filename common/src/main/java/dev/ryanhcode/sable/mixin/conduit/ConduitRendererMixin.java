package dev.ryanhcode.sable.mixin.conduit;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.ConduitRenderer;
import net.minecraft.world.level.block.entity.ConduitBlockEntity;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ConduitRenderer.class)
public class ConduitRendererMixin {
    @Inject(method = "render(Lnet/minecraft/world/level/block/entity/ConduitBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionf;)V", ordinal = 5))
    private void sable$reorientEye(final ConduitBlockEntity blockEntity, final float partialTick, final PoseStack poseStack, final MultiBufferSource bufferSource, final int packedLight, final int packedOverlay, final CallbackInfo ci) {
        final SubLevel subLevel = Sable.HELPER.getContaining(blockEntity);
        if (subLevel != null) {
            poseStack.mulPose(((ClientSubLevel)subLevel).renderPose(partialTick).orientation().get(new Quaternionf()).invert());
        }
    }
}
