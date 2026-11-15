package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.frogports;

import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.logistics.packagePort.PackagePortTarget;
import com.simibubi.create.infrastructure.config.AllConfigs;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin into PackagePortTarget.ChainConveyorFrogportTarget to account for sub-level distances when exporting from package ports.
 */

@Mixin(PackagePortTarget.ChainConveyorFrogportTarget.class)
public class PackagePortTargetMixin {

    @Shadow
    public float chainPos;

    @Shadow
    @Nullable
    public BlockPos connection;

    @Inject(method = "export", at = @At(value = "INVOKE", target = "Ljava/util/Set;contains(Ljava/lang/Object;)Z"), cancellable = true)
    public void sable$testSublevelDistance(final LevelAccessor level, final BlockPos portPos, final ItemStack box, final boolean simulate, final CallbackInfoReturnable<Boolean> cir, @Local final ChainConveyorBlockEntity cbe) {
        final Vec3 targetPos = cbe.getPackagePosition(this.chainPos, this.connection);

        final int maxRange = AllConfigs.server().logistics.packagePortRange.get() + 2;
        if (Sable.HELPER.distanceSquaredWithSubLevels((Level) level, targetPos, portPos.getX() + 0.5, portPos.getY() + 0.5, portPos.getZ() + 0.5) > maxRange * maxRange) {
            cir.setReturnValue(false);
        }
    }
}
