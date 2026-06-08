package dev.ryanhcode.sable.mixin.compatibility.waystones;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps a waystone on a held (unloaded) sub-level valid. {@code isValidInLevel} checks the block at
 * the stored plot-yard position, which is empty while the contraption is unloaded; treat a
 * reserved-but-unloaded sub-level plot as valid so the warp ({@link WaystoneTeleportManagerMixin})
 * can re-activate it.
 */
@Mixin(targets = "net.blay09.mods.waystones.core.WaystoneImpl", remap = false)
public abstract class WaystoneImplMixin {

    @Shadow
    private BlockPos pos;

    @Inject(method = "isValidInLevel", at = @At("HEAD"), cancellable = true)
    private void sable$validOnHeldSubLevel(final ServerLevel level, final CallbackInfoReturnable<Boolean> cir) {
        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return;
        }

        final int chunkX = this.pos.getX() >> SectionPos.SECTION_BITS;
        final int chunkZ = this.pos.getZ() >> SectionPos.SECTION_BITS;

        // Reserved plot, no loaded sub-level = held contraption; when loaded, let the vanilla block check run.
        if (container.getLastKnownPose(chunkX, chunkZ) != null && Sable.HELPER.getContaining(level, chunkX, chunkZ) == null) {
            cir.setReturnValue(true);
        }
    }
}
