package dev.ryanhcode.sable.mixin.impact;

import dev.ryanhcode.sable.api.block.BlockWithSubLevelCollisionCallback;
import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback;
import dev.ryanhcode.sable.physics.callback.BeehiveBlockCallback;
import net.minecraft.world.level.block.BeehiveBlock;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Mixin into {@link net.minecraft.world.level.block.BeehiveBlock} to attach its sub-level collision callback.
 */
@Mixin(BeehiveBlock.class)
public abstract class BeehiveBlockMixin implements BlockWithSubLevelCollisionCallback {

    @Override
    public BlockSubLevelCollisionCallback sable$getCallback() {
        return BeehiveBlockCallback.INSTANCE;
    }

}
