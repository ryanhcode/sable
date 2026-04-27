package dev.ryanhcode.sable.mixin.block_decal_render;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Changes the distance block damage is rendered from, and transforms block damage rendering for sublevels.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    // Storage vectors to avoid repeated allocation
    private final @Unique Quaternionf sable$orientationStorage = new Quaternionf();

    @Shadow
    @Nullable
    private ClientLevel level;

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;last()Lcom/mojang/blaze3d/vertex/PoseStack$Pose;", shift = At.Shift.BEFORE))
    private void sable$preRenderBlockDamage(final DeltaTracker deltaTracker, final boolean bl, final Camera camera, final GameRenderer gameRenderer, final LightTexture lightTexture, final Matrix4f matrix4f, final Matrix4f matrix4f2, final CallbackInfo ci, @Local(ordinal = 0) final PoseStack ps, @Local(ordinal = 0) final BlockPos pos) {

        final Vec3 plotPos = new Vec3(pos.getX(), pos.getY(), pos.getZ());
        final ClientSubLevel subLevel = (ClientSubLevel) Sable.HELPER.getContaining(this.level, plotPos);

        if (subLevel == null) {
            return;
        }

        final Pose3dc renderPose = subLevel.renderPose();
        final Vec3 cameraPos = camera.getPosition();
        final Vec3 projectedPos = renderPose.transformPosition(plotPos);

        ps.popPose();
        ps.pushPose();

        ps.translate(projectedPos.x - cameraPos.x, projectedPos.y - cameraPos.y, projectedPos.z - cameraPos.z);
        ps.mulPose(this.sable$orientationStorage.set(renderPose.orientation()));
    }

    @ModifyConstant(method = "renderLevel", constant = @Constant(doubleValue = 1024.0, ordinal = 0))
    private double sable$blockDamageDistance(final double originalBlockDamageDistanceConstant) {
        return Double.MAX_VALUE;
    }
}
