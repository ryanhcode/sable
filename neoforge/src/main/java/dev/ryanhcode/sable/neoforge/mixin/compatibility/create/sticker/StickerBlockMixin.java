package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.sticker;

import com.simibubi.create.content.contraptions.chassis.StickerBlock;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import dev.ryanhcode.sable.neoforge.mixinterface.compatibility.create.StickerBlockEntityExtension;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Mixin into {@link com.simibubi.create.content.contraptions.chassis.StickerBlock} to remove sticker constraints when moved into sub-levels.
 */

@Mixin(StickerBlock.class)
public class StickerBlockMixin implements BlockSubLevelAssemblyListener {

    @SuppressWarnings("AddedMixinMembersNamePattern")
    @Override
    public void afterMove(final ServerLevel originLevel, final ServerLevel resultingLevel, final BlockState newState, final BlockPos oldPos, final BlockPos newPos) {
        if (originLevel.getBlockEntity(oldPos) instanceof final StickerBlockEntityExtension extension) {
            extension.sable$removeConstraint();
        }
        if (resultingLevel.getBlockEntity(newPos) instanceof final StickerBlockEntityExtension extension) {
            extension.sable$removeConstraint();
        }
    }
}
