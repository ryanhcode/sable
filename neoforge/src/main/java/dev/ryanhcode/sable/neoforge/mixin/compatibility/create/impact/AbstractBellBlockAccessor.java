package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.impact;

import com.simibubi.create.content.equipment.bell.AbstractBellBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Mixin into {@link com.simibubi.create.content.equipment.bell.AbstractBellBlock} to expose the ring method for sub-level collision callbacks.
 */

@Mixin(AbstractBellBlock.class)
public interface AbstractBellBlockAccessor {

    @Invoker
    boolean invokeRing(Level world, BlockPos pos, Direction direction, Player player);

}
