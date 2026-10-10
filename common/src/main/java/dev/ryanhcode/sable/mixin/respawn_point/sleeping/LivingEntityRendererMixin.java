package dev.ryanhcode.sable.mixin.respawn_point.sleeping;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/**
 * Mixin into {@link net.minecraft.client.renderer.entity.LivingEntityRenderer} to rotate a sleeping entity's render pose into the sub-level orientation.
 */
@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getBedOrientation()Lnet/minecraft/core/Direction;"))
    private void sable$setupRotations(final LivingEntity livingEntity, final float f, final float g, final PoseStack poseStack, final MultiBufferSource multiBufferSource, final int i, final CallbackInfo ci) {
        if (livingEntity.getBedOrientation() == null) {
            return;
        }

        final Optional<BlockPos> sleepingPos = livingEntity.getSleepingPos();

        if (sleepingPos.isPresent()) {
            final BlockPos blockPos = sleepingPos.get();

            final SubLevel subLevel = Sable.HELPER.getContaining(livingEntity.level(), blockPos);

            if (subLevel instanceof final ClientSubLevel clientSubLevel) {
                poseStack.mulPose(new Quaternionf(clientSubLevel.renderPose().orientation()));
            }
        }
    }

}
