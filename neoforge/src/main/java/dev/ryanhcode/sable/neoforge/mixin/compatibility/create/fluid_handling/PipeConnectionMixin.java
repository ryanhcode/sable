package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.fluid_handling;

import com.simibubi.create.content.fluids.PipeConnection;
import dev.ryanhcode.sable.Sable;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Mixin into {@link com.simibubi.create.content.fluids.PipeConnection} so pipe render distance checks account for sub-levels.
 */

@Mixin(PipeConnection.class)
public class PipeConnectionMixin {
    @Redirect(method = "isRenderEntityWithinDistance", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;distanceTo(Lnet/minecraft/world/phys/Vec3;)D"))
    private static double sable$distanceIncludingSubLevels(final Vec3 instance, final Vec3 vec3) {
        return Math.sqrt(Sable.HELPER.distanceSquaredWithSubLevels(Minecraft.getInstance().level, instance, vec3));
    }
}
