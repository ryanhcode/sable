package dev.ryanhcode.sable.mixin.entity.entity_leashing;

import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Mixin into {@link net.minecraft.client.renderer.entity.EntityRenderer} so leash rendering correctly handles entities inside sub-levels.
 */
@Mixin(EntityRenderer.class)
public class EntityRendererMixin {

    @Redirect(method = "renderLeash", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getRopeHoldPosition(F)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 sable$getRopeHoldPosition(final Entity instance, final float f, @Local(argsOnly = true, ordinal = 0) final Entity leashedEntity){
        final ActiveSableCompanion helper = Sable.HELPER;
        final SubLevel leashedSubLevel = helper.getContaining(leashedEntity);

        final Vector3d ropeHoldPosition = JOMLConversion.toJOML(instance.getRopeHoldPosition(f));
        final SubLevel holdingSubLevel = helper.getContaining(leashedEntity.level(), ropeHoldPosition);

        if (holdingSubLevel != null) {
            holdingSubLevel.logicalPose().transformPosition(ropeHoldPosition);
        }

        if (leashedSubLevel != null) {
            leashedSubLevel.logicalPose().transformPositionInverse(ropeHoldPosition);
        }

        return JOMLConversion.toMojang(ropeHoldPosition);
    }

    @Redirect(method = "renderLeash", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getEyePosition(F)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 sable$getEyePosition(final Entity instance, final float f, @Local(argsOnly = true, ordinal = 0) final Entity leashedEntity){
        final ActiveSableCompanion helper = Sable.HELPER;
        final SubLevel leashedSubLevel = helper.getContaining(leashedEntity);

        final Vector3d eyePosition = JOMLConversion.toJOML(instance.getEyePosition(f));
        final SubLevel holdingSubLevel = helper.getContaining(leashedEntity.level(), eyePosition);

        if (holdingSubLevel != null) {
            holdingSubLevel.logicalPose().transformPosition(eyePosition);
        }

        if (leashedSubLevel != null) {
            leashedSubLevel.logicalPose().transformPositionInverse(eyePosition);
        }

        return JOMLConversion.toMojang(eyePosition);
    }
}
