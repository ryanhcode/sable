package dev.ryanhcode.sable.mixin.entity.entity_rotations_and_riding;

import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.mixinhelpers.camera.camera_rotation.EntitySubLevelRotationHelper;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Mixin into {@link net.minecraft.client.renderer.entity.EntityRenderer} to correctly orient the name tags of entities inside sub-levels.
 */
@Mixin(EntityRenderer.class)
public class EntityRendererMixin {

    @Redirect(method = "renderNameTag", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;cameraOrientation()Lorg/joml/Quaternionf;"))
    private Quaternionf sable$renderNameTag(final EntityRenderDispatcher instance, @Local(argsOnly = true) final Entity entity) {
        if (!EntitySubLevelUtil.shouldKick(entity)) {
            return instance.cameraOrientation();
        }

        final float pt = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        final Quaterniond orientation = EntitySubLevelRotationHelper.getEntityOrientation(entity, x -> ((ClientSubLevel) x).renderPose(), pt, EntitySubLevelRotationHelper.Type.ENTITY);
        if (orientation == null) {
            return instance.cameraOrientation();
        }

        return new Quaternionf(orientation).conjugate().mul(instance.cameraOrientation());
    }

}
