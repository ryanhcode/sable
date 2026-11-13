package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.entity_falls_on_block;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.millstone.MillstoneBlock;
import com.tterrag.registrate.util.entry.BlockEntry;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Mixin into {@link com.simibubi.create.content.kinetics.belt.BeltBlock} and {@link com.simibubi.create.content.kinetics.millstone.MillstoneBlock} to find block entities in sub-levels when entities land on them.
 */

@Mixin({ BeltBlock.class, MillstoneBlock.class })
public class BeltMillstoneBlocksMixin extends Block {

    public BeltMillstoneBlocksMixin(final Properties pProperties) {
        super(pProperties);
    }

	@WrapOperation(method = "updateEntityAfterFallOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;blockPosition()Lnet/minecraft/core/BlockPos;"))
	public BlockPos sable$checkForSubLevels(final Entity instance, final Operation<BlockPos> original) {
		final Level level = instance.level();

		BlockEntry<?> entry;
		if ((Object) this instanceof BeltBlock) {
			entry = AllBlocks.BELT;
		} else {
			entry = AllBlocks.MILLSTONE;
		}

        final ActiveSableCompanion helper = Sable.HELPER;
        final BlockPos gatheredBeltPos = helper.runIncludingSubLevels(level, instance.position(), true, null, (subLevel, internalPos) -> {
			if (entry.has(level.getBlockState(internalPos))) {
				return internalPos;
			} else if (entry.has(level.getBlockState(internalPos.below()))) {
				return internalPos.below();
			}

			return null;
		});

		if (gatheredBeltPos != null) {
			return gatheredBeltPos;
		}

		return original.call(instance);
	}
}
