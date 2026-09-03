package dev.ryanhcode.sable.mixin.entity.bee;

import dev.ryanhcode.sable.mixinhelpers.entity.bee.BeeSableHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.function.Predicate;

@Mixin(targets = "net.minecraft.world.entity.animal.Bee$BeePollinateGoal")
public abstract class BeePollinateGoalMixin {
    @Shadow
    @Final
    private Bee this$0;

    @Shadow
    @Final
    private Predicate<BlockState> VALID_POLLINATION_BLOCKS;

    @Inject(method = "findNearbyFlower", at = @At("HEAD"), cancellable = true)
    private void sable$findNearbyFlower(final CallbackInfoReturnable<Optional<BlockPos>> cir) {
        cir.setReturnValue(BeeSableHooks.findNearestFlower(this.this$0, this.VALID_POLLINATION_BLOCKS, 5.0));
    }

    @Inject(method = "canBeeContinueToUse", at = @At("HEAD"), cancellable = true)
    private void sable$stopPollinatingAtNight(final CallbackInfoReturnable<Boolean> cir) {
        if (this.this$0.level().isNight()) {
            cir.setReturnValue(false);
        }
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;atBottomCenterOf(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 sable$projectFlowerHoverTarget(final Vec3i pos) {
        return BeeSableHooks.projectHoverTarget(this.this$0, new BlockPos(pos.getX(), pos.getY(), pos.getZ()));
    }
}
