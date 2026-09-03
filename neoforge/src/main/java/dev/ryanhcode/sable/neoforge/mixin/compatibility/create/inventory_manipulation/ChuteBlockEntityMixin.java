package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.inventory_manipulation;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.content.logistics.chute.ChuteBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Mixin into {@link com.simibubi.create.content.logistics.chute.ChuteBlockEntity} so chutes can grab inventory capabilities across sub-levels.
 */

@Mixin(ChuteBlockEntity.class)
public abstract class ChuteBlockEntityMixin extends SmartBlockEntity {

	public ChuteBlockEntityMixin(final BlockEntityType<?> type, final BlockPos pos, final BlockState state) {
		super(type, pos, state);
	}

	@WrapMethod(method = "grabCapability")
	public IItemHandler sable$grabCap(final Direction side, final Operation<IItemHandler> original) {
		final IItemHandler handler = original.call(side);
		if (handler != null) {
			return handler;
		}

		// anything past this, we don't really need a cache... It has the potential to constantly move as it's not local
		final Level level = this.getLevel();
		assert level != null;

		final BlockPos checkPos = this.worldPosition.relative(side);
		final Direction opposite = side.getOpposite();
		final Vector3d mut = new Vector3d(opposite.getStepX(), opposite.getStepY(), opposite.getStepZ());

        final ActiveSableCompanion helper = Sable.HELPER;
        final SubLevel parentSublevel = helper.getContaining(level, checkPos);
		if (parentSublevel != null) {
			parentSublevel.logicalPose().transformNormalInverse(mut);
		}

		final Vector3d includSublevelDir = new Vector3d(mut);
		return helper.runIncludingSubLevels(
				level,
				checkPos.getCenter(),
				false,
				parentSublevel,
				(sublevel, pos) -> {
					includSublevelDir.set(mut);
					if (sublevel != null) {
						sublevel.logicalPose().transformNormal(includSublevelDir);
					}

					return level.getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.getNearest(includSublevelDir.x, includSublevelDir.y, includSublevelDir.z));
				}
		);
	}
}