package dev.ryanhcode.sable.mixin.entity.sublevels_block_sky;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.mixinhelpers.entity.sublevels_block_sky.SubLevelsBlockSkyMixinHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.FleeSunGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Mixin into {@link net.minecraft.world.entity.Mob}, {@link net.minecraft.world.entity.ai.goal.FleeSunGoal} and {@link net.minecraft.world.entity.ai.navigation.GroundPathNavigation} to make sub-levels block the sky, affecting the relevant logic.
 */
@Mixin({Mob.class, FleeSunGoal.class, GroundPathNavigation.class})
public class SubLevelsBlockSkyMixin {
    @WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;canSeeSky(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean sable$subLevelsBlockSky(final Level instance, final BlockPos pos, final Operation<Boolean> original) {
        final boolean canSeeOriginal = original.call(instance, pos);

        if (canSeeOriginal && pos.getY() < instance.getMaxBuildHeight()) {
            //I can't think of a better way to approach this right now... --cyvack
            if (SubLevelsBlockSkyMixinHelper.checkSkyWithSublevels(instance, pos)) {
                return false;
            }
        }

        return canSeeOriginal;
    }
}
