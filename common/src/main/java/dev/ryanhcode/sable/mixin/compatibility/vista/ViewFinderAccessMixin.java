package dev.ryanhcode.sable.mixin.compatibility.vista;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.Sable;
import net.mehvahdjukaar.vista.common.view_finder.ViewFinderAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Vista compatibility: make the view finder block validity distance calculation account for sub-levels.
 */
@Mixin(ViewFinderAccess.Block.class)
public class ViewFinderAccessMixin {

    @WrapOperation(method = "stillValid", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;distToCenterSqr(Lnet/minecraft/core/Position;)D"))
    private static double sable$distToCenterSqr(final BlockPos instance, final Position position, final Operation<Double> original, @Local(argsOnly = true) final Player player) {
        return Sable.HELPER.distanceSquaredWithSubLevels(player.level(), position, instance.getX() + 0.5, instance.getY() + 0.5, instance.getZ() + 0.5);
    }
}
