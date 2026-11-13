package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.nozzle.block_entity;

import com.simibubi.create.content.kinetics.fan.NozzleBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.ryanhcode.sable.neoforge.mixinterface.compatibility.create.NozzleBlockEntityExtension;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.EnumSet;

/**
 * Mixin into {@link com.simibubi.create.content.kinetics.fan.NozzleBlockEntity} to maintain the set of valid output directions for the nozzle.
 */

@Mixin(NozzleBlockEntity.class)
public abstract class ValidNozzledirectionMixin extends SmartBlockEntity implements NozzleBlockEntityExtension {

	@Unique
	private final EnumSet<Direction> sable$validDirections = EnumSet.noneOf(Direction.class);

	public ValidNozzledirectionMixin(final BlockEntityType<?> type, final BlockPos pos, final BlockState state) {
		super(type, pos, state);
	}

	@Override
	public EnumSet<Direction> sable$getValidDirections() {
		return this.sable$validDirections;
	}

	@Inject(method = "tick", at = @At("HEAD"))
	public void sable$updateValidDirections(final CallbackInfo ci) {
		this.sable$validDirections.clear();

		if (this.getLevel() != null) {
			for (final Direction value : Direction.values()) {
				final BlockState state = this.getLevel().getBlockState(this.getBlockPos().relative(value));
				if (state.canBeReplaced()) {
					this.sable$validDirections.add(value);
				}
			}
		}
	}

}
