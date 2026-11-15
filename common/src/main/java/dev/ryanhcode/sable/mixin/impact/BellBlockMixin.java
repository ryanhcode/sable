package dev.ryanhcode.sable.mixin.impact;

import dev.ryanhcode.sable.api.block.BlockWithSubLevelCollisionCallback;
import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback;
import dev.ryanhcode.sable.physics.callback.BellBlockCallback;
import net.minecraft.world.level.block.BellBlock;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Mixin into {@link net.minecraft.world.level.block.BellBlock} to attach its sub-level collision callback.
 */
@Mixin(BellBlock.class)
public abstract class BellBlockMixin implements BlockWithSubLevelCollisionCallback {

    @Override
    public BlockSubLevelCollisionCallback sable$getCallback() {
        return BellBlockCallback.INSTANCE;
    }

}
