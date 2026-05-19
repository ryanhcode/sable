package dev.ryanhcode.sable.mixin.compatibility.shouldersurfing;

import com.github.exopandora.shouldersurfing.client.ShoulderSurfingCamera;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.Sable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ShoulderSurfingCamera.class)
public class ShoulderSurfingCameraMixin {

    @Redirect(method = "maxZoom", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;distanceTo(Lnet/minecraft/world/phys/Vec3;)D"))
    private static double distanceTo(final Vec3 instance, final Vec3 vec, @Local(argsOnly = true) final BlockGetter level) {
        return level instanceof Level ? Math.sqrt(Sable.HELPER.distanceSquaredWithSubLevels((Level) level, instance, vec)) : instance.distanceTo(vec);
    }
}
