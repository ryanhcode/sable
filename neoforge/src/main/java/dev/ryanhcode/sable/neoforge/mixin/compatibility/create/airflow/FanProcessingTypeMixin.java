package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.airflow;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Mixin into {@link com.simibubi.create.content.kinetics.fan.processing.FanProcessingType} so fan processing can resolve blocks in sub-levels.
 */

@Mixin(FanProcessingType.class)
public interface FanProcessingTypeMixin {
	@WrapMethod(method = "getAt")
	private static FanProcessingType getAt(Level level, BlockPos pos, Operation<FanProcessingType> original) {
		ActiveSableCompanion helper = Sable.HELPER;
		return helper.runIncludingSubLevels(level, pos.getCenter(), true, helper.getContaining(level, pos),
				(subLevel, relativePos) -> original.call(level, relativePos));
	}
}
