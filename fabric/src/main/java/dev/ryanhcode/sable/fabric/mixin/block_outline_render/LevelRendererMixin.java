package dev.ryanhcode.sable.fabric.mixin.block_outline_render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Transforms block hover outlines for sublevels.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    // Storage vectors to avoid repeated allocation
    private final @Unique Vector3d sable$localTranslationStorage = new Vector3d();
    private final @Unique Vector3d sable$globalTranslationStorage = new Vector3d();
    private final @Unique Quaternionf sable$orientationStorage = new Quaternionf();

    @Shadow
    @Nullable
    private ClientLevel level;

    @Shadow
    protected static void renderShape(final PoseStack arg, final VertexConsumer arg2, final VoxelShape arg3, final double d, final double e, final double f, final float g, final float h, final float i, final float j) {
    }

    @Inject(method = "renderHitOutline", at = @At("HEAD"), cancellable = true)
    private void sable$preRenderHitOutline(final PoseStack ps, final VertexConsumer pConsumer, final Entity pEntity, final double pCamX, final double pCamY, final double pCamZ, final BlockPos blockPos, final BlockState blockState, final CallbackInfo ci) {
        final ClientSubLevel subLevel = (ClientSubLevel) Sable.HELPER.getContaining(this.level, blockPos);

        if (subLevel == null) {
            return;
        }

        ps.pushPose();

        final Pose3dc pose = subLevel.renderPose();

        final Vec3 cameraPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();

        final Vector3d globalTranslation = pose.position().sub(cameraPos.x, cameraPos.y, cameraPos.z, this.sable$globalTranslationStorage);
        final Vector3d localTranslation = this.sable$localTranslationStorage.set(blockPos.getX(), blockPos.getY(), blockPos.getZ()).sub(pose.rotationPoint());

        // apply transforms
        ps.translate(globalTranslation.x, globalTranslation.y, globalTranslation.z);
        ps.mulPose(this.sable$orientationStorage.set(pose.orientation()));
        ps.translate(localTranslation.x, localTranslation.y, localTranslation.z);

        renderShape(ps, pConsumer, blockState.getShape(this.level, blockPos, CollisionContext.of(pEntity)), 0, 0, 0, 0.0F, 0.0F, 0.0F, 0.4F);

        ps.popPose();
        ci.cancel();
    }
}
