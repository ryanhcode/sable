package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.display_link;

import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;
import com.simibubi.create.content.redstone.displayLink.LinkWithBulbBlockEntity;
import com.simibubi.create.infrastructure.config.AllConfigs;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin into {@link com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity} so display link distance checks account for sub-levels.
 */

@Mixin(DisplayLinkBlockEntity.class)
public abstract class DisplayLinkBlockEntityMixin extends LinkWithBulbBlockEntity {

    private DisplayLinkBlockEntityMixin(final BlockEntityType<?> type, final BlockPos pos, final BlockState state) {
        super(type, pos, state);
    }

    @Inject(method = "getTargetPosition", at = @At("TAIL"), cancellable = true)
    public void sable$accountForSubLevels(final CallbackInfoReturnable<BlockPos> cir) {
        final BlockPos target = cir.getReturnValue();
        final int range = AllConfigs.server().logistics.displayLinkRange.get();
        final BlockPos pos = this.getBlockPos();
        if (Sable.HELPER.distanceSquaredWithSubLevels(this.level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5) >= range * range) {
            cir.setReturnValue(BlockPos.ZERO);
        }
    }
}
