package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.lectern_controller;

import com.simibubi.create.content.redstone.link.controller.LecternControllerBlock;
import com.simibubi.create.content.redstone.link.controller.LecternControllerBlockEntity;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import dev.ryanhcode.sable.neoforge.mixinterface.compatibility.create.LecternControllerBlockEntityExtension;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LecternControllerBlock.class)
public class LecternControllerBlockMixin implements BlockSubLevelAssemblyListener {

    @Override
    public void afterMove(final ServerLevel originLevel, final ServerLevel resultingLevel, final BlockState newState, final BlockPos oldPos, final BlockPos newPos) {
        if (originLevel.getBlockEntity(oldPos) instanceof final LecternControllerBlockEntity be) {
            ((LecternControllerBlockEntityExtension) be).sable$setNoDrop();
        }
    }
}
