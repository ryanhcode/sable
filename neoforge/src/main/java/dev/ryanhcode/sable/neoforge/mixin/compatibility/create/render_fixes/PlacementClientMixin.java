package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.render_fixes;


import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import net.createmod.catnip.placement.PlacementClient;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Mixin into {@link net.createmod.catnip.placement.PlacementClient} to project the placement direction indicator out of sub-levels.
 */

@Mixin(PlacementClient.class)
public class PlacementClientMixin {

    @Redirect(method = "drawDirectionIndicator",
            at = @At(value = "INVOKE", target = "Lnet/createmod/catnip/math/VecHelper;getCenterOf(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/Vec3;"),
            remap = false)
    private static Vec3 sable$projectLastTargetedPos(final Vec3i pos) {
        return JOMLConversion.toMojang(Sable.HELPER.projectOutOfSubLevel(Minecraft.getInstance().level, JOMLConversion.atCenterOf(pos)));
    }
}
