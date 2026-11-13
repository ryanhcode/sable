package dev.ryanhcode.sable.mixin.entity.bee;

import dev.ryanhcode.sable.mixinhelpers.entity.bee.BeeSableHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.Bee;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(targets = "net.minecraft.world.entity.animal.Bee$BeeLocateHiveGoal")
public abstract class BeeLocateHiveGoalMixin {
    @Shadow
    @Final
    private Bee this$0;

    @Inject(method = "findNearbyHivesWithSpace", at = @At("HEAD"), cancellable = true)
    private void sable$findNearbyHivesWithSpace(final CallbackInfoReturnable<List<BlockPos>> cir) {
        cir.setReturnValue(BeeSableHooks.findNearbyHivesWithSpace(this.this$0));
    }
}
