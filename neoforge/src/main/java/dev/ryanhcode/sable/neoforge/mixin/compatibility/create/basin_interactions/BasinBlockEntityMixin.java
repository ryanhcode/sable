package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.basin_interactions;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin into {@link com.simibubi.create.content.processing.basin.BasinBlockEntity} so the basin can find heat sources in sub-levels.
 */

@Mixin(BasinBlockEntity.class)
public class BasinBlockEntityMixin extends BlockEntity {

    @Shadow @Nullable private BlazeBurnerBlock.@Nullable HeatLevel cachedHeatLevel;

    public BasinBlockEntityMixin(final BlockEntityType<?> arg, final BlockPos arg2, final BlockState arg3) {
        super(arg, arg2, arg3);
    }

    @Inject(method = "getHeatLevel", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/processing/basin/BasinBlockEntity;getHeatLevelOf(Lnet/minecraft/world/level/block/state/BlockState;)Lcom/simibubi/create/content/processing/burner/BlazeBurnerBlock$HeatLevel;", shift = At.Shift.AFTER), cancellable = true)
    private void sable$accountForSubLevels(final CallbackInfoReturnable<BlazeBurnerBlock.HeatLevel> cir) {
        if (this.cachedHeatLevel != null && this.cachedHeatLevel != BlazeBurnerBlock.HeatLevel.NONE) {
            return;
        }

        final Level level = this.getLevel();
        final BlockPos originalPos = this.getBlockPos().below();
        final ActiveSableCompanion helper = Sable.HELPER;
        final BlazeBurnerBlock.HeatLevel heatLevel = helper.runIncludingSubLevels(level, originalPos.getCenter(), false, helper.getContaining(level, originalPos), (subLevel, pos) -> {
            final BlazeBurnerBlock.HeatLevel internalHeat = BasinBlockEntity.getHeatLevelOf(level.getBlockState(pos));

            if (internalHeat != BlazeBurnerBlock.HeatLevel.NONE) {
                return internalHeat;
            }

            return null;
        });

        if (heatLevel != null) {
            cir.setReturnValue(heatLevel);
        }
    }

    @WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;"))
    public BlockEntity sable$accountForSubLevels(final Level level, final BlockPos pos, final Operation<BlockEntity> original) {
        final ActiveSableCompanion helper = Sable.HELPER;
        return helper.runIncludingSubLevels(level, pos.getCenter(), true, helper.getContaining(level, pos), (subLevel, internalPos) -> original.call(level, internalPos));
    }

}
