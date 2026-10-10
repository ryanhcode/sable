package dev.ryanhcode.sable.mixin.compatibility.exposure;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.Sable;
import io.github.mortuusars.exposure.client.animation.CameraPoses;
import io.github.mortuusars.exposure.world.entity.CameraStandEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Exposure compatibility: project the camera stand's eye position out of the sub-level so camera poses stay correct.
 */
@Mixin(CameraPoses.class)
public class CameraPosesMixin {

	@WrapOperation(method = "applyStand", at = @At(value = "INVOKE", target = "Lio/github/mortuusars/exposure/world/entity/CameraStandEntity;getEyePosition()Lnet/minecraft/world/phys/Vec3;"))
	private Vec3 sable$applyStand(final CameraStandEntity instance, final Operation<Vec3> original) {
		final Vec3 pos = original.call(instance);
		return Sable.HELPER.projectOutOfSubLevel(instance.level(), pos);
	}
}
