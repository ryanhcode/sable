package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.render_fixes;

import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringRenderer;
import dev.ryanhcode.sable.Sable;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Mixin into {@link com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringRenderer} so filter render distances account for sub-levels.
 */

@Mixin(FilteringRenderer.class)
public class FilteringRendererMixin {

    @Redirect(method = "renderOnBlockEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"))
    private static double sable$distanceToSqr(final Vec3 instance, final Vec3 pVec) {
        return Sable.HELPER.distanceSquaredWithSubLevels(Minecraft.getInstance().level, instance, pVec);
    }
}
