package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.display_link;

import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlock;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Mixin into {@link com.simibubi.create.content.redstone.displayLink.DisplayLinkBlock} to preserve the display link target when moving into a sub-level.
 */

@Mixin(DisplayLinkBlock.class)
public class DisplayLinkBlockMixin implements BlockSubLevelAssemblyListener {

    @SuppressWarnings("AddedMixinMembersNamePattern")
    @Override
    public void afterMove(final ServerLevel originLevel, final ServerLevel resultingLevel, final BlockState newState, final BlockPos oldPos, final BlockPos newPos) {
        if (originLevel.getBlockEntity(oldPos) instanceof final DisplayLinkBlockEntity be && resultingLevel.getBlockEntity(newPos) instanceof final DisplayLinkBlockEntity newBe) {
            newBe.target(be.getTargetPosition());
        }
    }
}
