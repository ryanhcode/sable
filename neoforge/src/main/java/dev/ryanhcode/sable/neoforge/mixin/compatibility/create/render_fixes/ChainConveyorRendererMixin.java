package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.render_fixes;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorRenderer;
import dev.ryanhcode.sable.Sable;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Position;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Mixin into {@link com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorRenderer} so chain render distances account for sub-levels.
 */

@Mixin(ChainConveyorRenderer.class)
public class ChainConveyorRendererMixin {

    @Redirect(method = "renderChains", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;closerThan(Lnet/minecraft/core/Position;D)Z"))
    public boolean sable$fixMipDistance(final Vec3 instance, final Position position, final double d) {
        return Sable.HELPER.distanceSquaredWithSubLevels(Minecraft.getInstance().level, instance, position.x(), position.y(), position.z()) < d * d;
    }
}
