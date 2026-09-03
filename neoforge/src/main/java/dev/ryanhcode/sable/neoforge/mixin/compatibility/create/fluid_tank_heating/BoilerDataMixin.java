package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.fluid_tank_heating;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.fluids.tank.BoilerData;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into {@link com.simibubi.create.content.fluids.tank.BoilerData} so the boiler can find heat sources in sub-levels and force temperature refreshes.
 */

@Mixin(BoilerData.class)
public class BoilerDataMixin {

    @Shadow
    public boolean needsHeatLevelUpdate;

    @Unique
    private int sable$ticksUntilUpdate = 20;


    @Inject(method = "tick", at = @At(value = "FIELD", target = "Lcom/simibubi/create/content/fluids/tank/BoilerData;ticksUntilNextSample:I", ordinal = 0))
    public void sable$forceUpdateHeatIfDisconnected(final FluidTankBlockEntity controller, final CallbackInfo ci) {
        if (this.sable$ticksUntilUpdate-- <= 0) {
            this.sable$ticksUntilUpdate = 20;
            this.needsHeatLevelUpdate = true;
        }
    }

    @WrapOperation(method = "updateTemperature", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/api/boiler/BoilerHeater;findHeat(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)F"))
    public float sable$subLevelHeating(final Level level, final BlockPos pos, final BlockState state, final Operation<Float> original) {
        final Float originalHeat = original.call(level, pos, state);
        if (originalHeat != -1) {
            return originalHeat;
        }

        final ActiveSableCompanion helper = Sable.HELPER;
        final Float gatheredHeat = helper.runIncludingSubLevels(level, pos.getCenter(), false, helper.getContaining(level, pos), (subLevel, internalPos) -> {
            final Float internalHeat = original.call(level, internalPos, level.getBlockState(internalPos));

            if (internalHeat != -1) {
                return internalHeat;
            }

            return null;

        });

        if (gatheredHeat != null) {
            return gatheredHeat;
        }

        return -1;
    }
}
