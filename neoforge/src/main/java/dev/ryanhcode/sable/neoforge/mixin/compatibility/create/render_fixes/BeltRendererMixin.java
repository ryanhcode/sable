package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.render_fixes;

import com.simibubi.create.content.kinetics.belt.BeltRenderer;
import dev.ryanhcode.sable.Sable;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Mixin into {@link com.simibubi.create.content.kinetics.belt.BeltRenderer} so belt item render distances account for sub-levels.
 */

@Mixin(BeltRenderer.class)
public class BeltRendererMixin {

    @Redirect(method = "renderItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;distanceTo(Lnet/minecraft/world/phys/Vec3;)D"))
    public double sable$projectDistanceTo(final Vec3 eyePos, final Vec3 itemPos) {
        return Math.sqrt(Sable.HELPER.distanceSquaredWithSubLevels(Minecraft.getInstance().level, eyePos, itemPos));
    }
}
