package dev.ryanhcode.sable.mixin.compatibility.vista;

import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.mehvahdjukaar.vista.client.ViewFinderController;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vista compatibility: apply the sub-level orientation to the camera rotation when setting up the camera.
 */
@Mixin(ViewFinderController.class)
public class ViewFinderControllerMixin {

	@Unique
	private static final Quaternionf sable$orientation = new Quaternionf();

	@Inject(method = "setupCamera", at = @At("TAIL"))
	private static void sable$setupCamera(final Camera camera, final BlockGetter level, final Entity entity, final boolean detached, final boolean thirdPersonReverse, final float partialTick, final CallbackInfoReturnable<Boolean> cir, @Local(ordinal = 0) final Vec3 centerCannonPos) {
		final ClientSubLevel subLevel = (ClientSubLevel) Sable.HELPER.getContaining(entity.level(), centerCannonPos);
		if(subLevel != null) {
			final Quaternionf rotation = camera.rotation();
			sable$orientation.set(subLevel.renderPose().orientation());

			rotation.premul(sable$orientation, rotation);
		}
	}
}
