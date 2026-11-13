package dev.ryanhcode.sable.mixin.entity.bee;

import dev.ryanhcode.sable.mixinhelpers.entity.bee.BeeSableHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.world.entity.animal.Bee;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.world.entity.animal.Bee$BeeEnterHiveGoal")
public abstract class BeeEnterHiveGoalMixin {
    @Shadow
    @Final
    private Bee this$0;

    @Redirect(method = "canBeeUse", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;closerToCenterThan(Lnet/minecraft/core/Position;D)Z"))
    private boolean sable$canEnterHive(final BlockPos hivePos, final Position position, final double distance) {
        return BeeSableHooks.closerThan(this.this$0, hivePos, distance);
    }
}
