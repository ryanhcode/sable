package dev.ryanhcode.sable.mixin.clip_overwrite;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.LevelPoseProviderExtension;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.Position;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Changes the block picking distance check to take into account sublevels
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Redirect(method = "filterHitResult", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;closerThan(Lnet/minecraft/core/Position;D)Z"))
    private static boolean sable$closerThan(final Vec3 a, final Position b, final double d) {
        return Sable.HELPER.distanceSquaredWithSubLevels(Minecraft.getInstance().level, a, new Vec3(b.x(), b.y(), b.z())) < d * d;
    }

    @Redirect(method = "pick(Lnet/minecraft/world/entity/Entity;DDF)Lnet/minecraft/world/phys/HitResult;", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"))
    private double sable$distanceToSqr(final Vec3 instance, final Vec3 other) {
        return Sable.HELPER.distanceSquaredWithSubLevels(this.minecraft.level, instance, other);
    }

    @Redirect(method = "pick(Lnet/minecraft/world/entity/Entity;DDF)Lnet/minecraft/world/phys/HitResult;", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getEyePosition(F)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 sable$getEyePosition(final Entity instance, final float partialTicks) {
        return Sable.HELPER.getEyePositionInterpolated(instance, partialTicks);
    }

    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;pick(F)V"))
    private void sable$renderLevel(final GameRenderer instance, final float f, final Operation<Void> original) {
        final LevelPoseProviderExtension extension = ((LevelPoseProviderExtension) this.minecraft.level);

        extension.sable$pushPoseSupplier((subLevel) -> ((ClientSubLevel) subLevel).renderPose(f));
        original.call(instance, f);
        extension.sable$popPoseSupplier();
    }

}
