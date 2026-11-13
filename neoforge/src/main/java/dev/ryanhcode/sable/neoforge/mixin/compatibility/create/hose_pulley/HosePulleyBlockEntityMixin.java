package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.hose_pulley;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.fluids.hosePulley.HosePulleyBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Mixin into {@link com.simibubi.create.content.fluids.hosePulley.HosePulleyBlockEntity} so the hose pulley can find fluids and blocks in sub-levels.
 */

@Mixin(HosePulleyBlockEntity.class)
public abstract class HosePulleyBlockEntityMixin extends SmartBlockEntity {

	public HosePulleyBlockEntityMixin(final BlockEntityType<?> type, final BlockPos pos, final BlockState state) {
        super(type, pos, state);
    }

    @WrapOperation(method = "lazyTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    public BlockState sable$checkForCollisions1(final Level instance, final BlockPos blockPos, final Operation<BlockState> original) {
	    return this.sable$getBlockState(instance, blockPos, original, true);
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    public BlockState sable$checkForCollisions2(final Level instance, final BlockPos blockPos, final Operation<BlockState> original) {
        return this.sable$getBlockState(instance, blockPos, original, false);
    }

    @WrapOperation(method = "onSpeedChanged", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    public BlockState sable$checkForCollisions3(final Level instance, final BlockPos blockPos, final Operation<BlockState> original) {
        return this.sable$getBlockState(instance, blockPos, original, false);
    }

    @Unique
    private BlockState sable$getBlockState(final Level level, final BlockPos blockPos, final Operation<BlockState> original, boolean inverseReplaceCheck) {
        final ActiveSableCompanion helper = Sable.HELPER;
        final BlockState gatheredState = helper.runIncludingSubLevels(level, blockPos.getCenter(), true, helper.getContaining(level, this.getBlockPos()), (sublevel, pos) -> {
            final BlockState innerState = original.call(level, pos);
            if (inverseReplaceCheck ^ innerState.canBeReplaced()) {
                return innerState;
            }

            return null;
        });

        if (gatheredState != null) {
            return gatheredState;
        }

        return original.call(level, blockPos);
    }
}
