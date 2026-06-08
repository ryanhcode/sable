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
 * Keeps a waystone on an unloaded sub-level valid as a teleport target.
 *
 * <p>{@code WaystoneImpl#isValidInLevel} checks {@code level.getBlockState(pos)} at the stored
 * position, which for a waystone on an assembled sub-level is the plot-yard coordinate. While the
 * contraption is unloaded its block isn't in the world, so the check fails and Waystones refuses with
 * "currently being moved or has gone missing". If the position belongs to a reserved-but-unloaded
 * sub-level plot, treat the waystone as valid - the warp re-activates the sub-level and the player is
 * frozen onto it (see {@link WaystoneTeleportManagerMixin}).
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

        // Reserved plot with no loaded sub-level == the waystone is on a held (unloaded) contraption.
        // When loaded, fall through to the vanilla block check.
        if (container.getLastKnownPose(chunkX, chunkZ) != null && Sable.HELPER.getContaining(level, chunkX, chunkZ) == null) {
            cir.setReturnValue(true);
        }
    }
}
