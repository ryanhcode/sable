package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.hose_pulley;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.fluids.hosePulley.HosePulleyFluidHandler;
import com.simibubi.create.content.fluids.transfer.FluidDrainingBehaviour;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Supplier;

/**
 * Mixin into {@link com.simibubi.create.content.fluids.hosePulley.HosePulleyFluidHandler} so the hose pulley can drain fluids from sub-levels.
 */

@Mixin(HosePulleyFluidHandler.class)
public abstract class HosePulleyFluidHandlerMixin {

	@Shadow
	private FluidDrainingBehaviour drainer;

	@Shadow
	private Supplier<BlockPos> rootPosGetter;
	@Unique
	private BlockPos sable$lastValidPos = null;

	@Inject(method = "drainInternal", at = @At("HEAD"))
	public void sable$updateLastValidPos(final int maxDrain, final FluidStack resource, final IFluidHandler.FluidAction action, final CallbackInfoReturnable<FluidStack> cir) {
        final ActiveSableCompanion helper = Sable.HELPER;
		final Level level = this.drainer.getWorld();
		final float distance = 1.5f;

        this.sable$lastValidPos = helper.runIncludingSubLevels(level, this.rootPosGetter.get().getCenter(), true, helper.getContaining(level, this.drainer.getPos()), (sublevel, pos) -> {
			if (sable$hasFluid(level, pos)) {
				//add some leniency to the fluid gathering, while keeping large jumps (local -> other sublevel etc) possible
				if (this.sable$lastValidPos == null || this.sable$lastValidPos.distSqr(pos) > distance * distance) {
					return pos;
				}

				//no changes needed
				return this.sable$lastValidPos;
			}

			return null;
		});
	}

	@WrapOperation(method = "drainInternal", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/fluids/transfer/FluidDrainingBehaviour;getDrainableFluid(Lnet/minecraft/core/BlockPos;)Lnet/neoforged/neoforge/fluids/FluidStack;"))
	public FluidStack sable$modifyGetDrainableFluid(final FluidDrainingBehaviour instance, final BlockPos rootPos, final Operation<FluidStack> original) {
		if (this.sable$lastValidPos != null) {
			return original.call(instance, this.sable$lastValidPos);
		}

		return original.call(instance, rootPos);
	}

	@WrapOperation(method = "drainInternal", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/fluids/transfer/FluidDrainingBehaviour;pullNext(Lnet/minecraft/core/BlockPos;Z)Z"))
	public boolean sable$modifyPullNext(final FluidDrainingBehaviour instance, final BlockPos root, final boolean simulate, final Operation<Boolean> original) {
		if (this.sable$lastValidPos != null) {
			return original.call(instance, this.sable$lastValidPos, simulate);
		}

		return original.call(instance, root, simulate);
	}

	@WrapOperation(method = "getFluidInTank", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/fluids/transfer/FluidDrainingBehaviour;getDrainableFluid(Lnet/minecraft/core/BlockPos;)Lnet/neoforged/neoforge/fluids/FluidStack;"))
	public FluidStack sable$modifyGetFluidInTank(final FluidDrainingBehaviour instance, final BlockPos rootPos, final Operation<FluidStack> original) {
		if (this.sable$lastValidPos != null) {
			return original.call(instance, this.sable$lastValidPos);
		}

		return original.call(instance, rootPos);
	}

	@Unique
	private static boolean sable$hasFluid(final Level level, final BlockPos pos) {
		return !level.getFluidState(pos).isEmpty();
	}
}
