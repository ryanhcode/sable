package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.display_link;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.redstone.displayLink.ClickToLinkBlockItem;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Mixin into {@link com.simibubi.create.content.redstone.displayLink.ClickToLinkBlockItem} so the click-to-link item's distance check accounts for sub-levels.
 */

@Mixin(ClickToLinkBlockItem.class)
public class ClickToLinkBlockItemMixin {

    @WrapOperation(method = "useOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;closerThan(Lnet/minecraft/core/Vec3i;D)Z"))
    public boolean sable$accountForSubLevels(final BlockPos instance, final Vec3i pos, final double v, final Operation<Boolean> original, @Local final Level level) {
        return Sable.HELPER.distanceSquaredWithSubLevels(level, instance.getX() + 0.5, instance.getY() + 0.5, instance.getZ() + 0.5, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < v * v;
    }
}
