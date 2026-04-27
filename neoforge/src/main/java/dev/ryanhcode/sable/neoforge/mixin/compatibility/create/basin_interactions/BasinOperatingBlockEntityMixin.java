package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.basin_interactions;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.processing.basin.BasinOperatingBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.simple.DeferralBehaviour;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BasinOperatingBlockEntity.class)
public abstract class BasinOperatingBlockEntityMixin {
    @Shadow
    public DeferralBehaviour basinChecker;

    @Unique
    private int sable$forceUpdateTicks = 0;

    @Inject(method = "tick", at = @At("HEAD"), remap = false)
    private void sable$forceUpdate(final CallbackInfo ci) {
        if (this.sable$forceUpdateTicks == 5) { //only update every 5 ticks
            this.basinChecker.scheduleUpdate();

            this.sable$forceUpdateTicks = 0;
        }

        this.sable$forceUpdateTicks++;
    }

    @WrapOperation(method = "getBasin", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;"))
    private BlockEntity sable$accountForSubLevels(final Level level, final BlockPos pos, Operation<BlockEntity> original) {
        final ActiveSableCompanion helper = Sable.HELPER;
        return helper.runIncludingSubLevels(level, pos.getCenter(), true, helper.getContaining(level, pos), (subLevel, internalPos) -> original.call(level, internalPos));
    }
}
