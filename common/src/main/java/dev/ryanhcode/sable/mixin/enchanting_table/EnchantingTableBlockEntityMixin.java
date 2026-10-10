package dev.ryanhcode.sable.mixin.enchanting_table;

import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Mixin into {@link net.minecraft.world.level.block.entity.EnchantingTableBlockEntity} to transform player coordinates into sub-level local space for the book animation.
 */
@Mixin(EnchantingTableBlockEntity.class)
public class EnchantingTableBlockEntityMixin {

    @Redirect(method  = "bookAnimationTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getX()D"))
    private static double sable$getPlayerX(final Player instance, @Local(argsOnly = true) final BlockPos blockPos) {
        final SubLevel subLevel = Sable.HELPER.getContaining(instance.level(), blockPos);

        if (subLevel != null) {
            return subLevel.logicalPose().transformPositionInverse(instance.getEyePosition()).x();
        }

        return instance.getX();
    }

    @Redirect(method  = "bookAnimationTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getZ()D"))
    private static double sable$getPlayerZ(final Player instance, @Local(argsOnly = true) final BlockPos blockPos) {
        final SubLevel subLevel = Sable.HELPER.getContaining(instance.level(), blockPos);

        if (subLevel != null) {
            return subLevel.logicalPose().transformPositionInverse(instance.getEyePosition()).z();
        }

        return instance.getZ();
    }

}
