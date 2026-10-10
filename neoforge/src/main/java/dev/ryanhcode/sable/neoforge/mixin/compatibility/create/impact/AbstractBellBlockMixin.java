package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.impact;

import com.simibubi.create.content.equipment.bell.AbstractBellBlock;
import dev.ryanhcode.sable.api.block.BlockWithSubLevelCollisionCallback;
import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback;
import dev.ryanhcode.sable.neoforge.physics.callback.AbstractBellBlockCallback;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Mixin into {@link com.simibubi.create.content.equipment.bell.AbstractBellBlock} to register a collision callback so bells ring when hit in sub-levels.
 */

@Mixin(AbstractBellBlock.class)
public class AbstractBellBlockMixin implements BlockWithSubLevelCollisionCallback {

    @Override
    public BlockSubLevelCollisionCallback sable$getCallback() {
        return AbstractBellBlockCallback.INSTANCE;
    }

}
