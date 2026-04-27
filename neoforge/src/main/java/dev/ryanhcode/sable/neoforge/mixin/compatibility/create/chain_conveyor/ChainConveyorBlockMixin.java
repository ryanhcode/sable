package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.chain_conveyor;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlock;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ChainConveyorBlock.class)
public class ChainConveyorBlockMixin implements BlockSubLevelAssemblyListener {

    @Override
    public void beforeMove(final ServerLevel originLevel, final ServerLevel resultingLevel, final BlockState newState, final BlockPos oldPos, final BlockPos newPos) {
        if (originLevel.getBlockEntity(oldPos) instanceof final ChainConveyorBlockEntity be) {
            // This tells all connected conveyors to re-check and detach if necessary next tick
            be.notifyConnectedToValidate();
        }
    }

    @Override
    public void afterMove(final ServerLevel originLevel, final ServerLevel resultingLevel, final BlockState newState, final BlockPos oldPos, final BlockPos newPos) {
        if (resultingLevel.getBlockEntity(newPos) instanceof final ChainConveyorBlockEntity be) {
            be.checkInvalid = true;
        }
    }
}
