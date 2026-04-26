package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.block_breakers;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.kinetics.base.BlockBreakingKineticBlockEntity;
import com.simibubi.create.content.kinetics.saw.SawBlock;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.neoforge.mixinhelper.compatibility.create.block_breakers.SubLevelBlockBreakingUtility;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Makes blocks such as drills and saws work between sublevels and the main level
 */
@Mixin(BlockBreakingKineticBlockEntity.class)
public abstract class BlockBreakingKineticBlockEntityMixin extends BlockEntity {

	public BlockBreakingKineticBlockEntityMixin(final BlockEntityType<?> pType, final BlockPos pPos, final BlockState pBlockState) {
		super(pType, pPos, pBlockState);
	}

	@Shadow
	public abstract boolean canBreak(BlockState stateToBreak, float blockHardness);

	@WrapOperation(remap = false, method = "tick", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/kinetics/base/BlockBreakingKineticBlockEntity;getBreakingPos()Lnet/minecraft/core/BlockPos;"))
	private BlockPos sable$preGetBlockToBreak(BlockBreakingKineticBlockEntity instance, Operation<BlockPos> original) {
		assert this.level != null;

		final BlockPos breakingPos = original.call(instance);
		final BlockState originalStateToBreak = this.level.getBlockState(breakingPos);

		if (!this.canBreak(originalStateToBreak, originalStateToBreak.getDestroySpeed(this.level, breakingPos))) {
			return SubLevelBlockBreakingUtility.findBreakingPos((pos, state) -> this.canBreak(state, state.getDestroySpeed(this.level, pos)),
					Sable.HELPER.getContaining(this),
					this.getLevel(),
					Vec3.atLowerCornerOf(this.getBlockState().getValue(SawBlock.FACING).getNormal()),
					this.getBlockPos().getCenter(),
					breakingPos);
		} else {
			return breakingPos;
		}
	}
}
