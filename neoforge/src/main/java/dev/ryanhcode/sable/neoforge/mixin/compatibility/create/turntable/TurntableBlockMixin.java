package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.turntable;

import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.kinetics.turntable.TurntableBlock;
import com.simibubi.create.content.kinetics.turntable.TurntableBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Mixin into {@link com.simibubi.create.content.kinetics.turntable.TurntableBlock} to account for sub-level positions when rotating entities.
 */

@Mixin(TurntableBlock.class)
public class TurntableBlockMixin {

    @Redirect(method = "lambda$entityInside$0", at = @At(value = "INVOKE", target = "Lnet/createmod/catnip/math/VecHelper;getCenterOf(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 sable$fixPos(final Vec3i pos, @Local(argsOnly = true) final TurntableBlockEntity be) {
        final Level level = be.getLevel();
        return JOMLConversion.toMojang(Sable.HELPER.projectOutOfSubLevel(level, JOMLConversion.atCenterOf(pos)));
    }

    @Redirect(method = "entityInside", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;getY()I"))
    private int sable$fixPosY(final BlockPos instance, @Local(argsOnly = true) final Level level) {
        return (int) Sable.HELPER.projectOutOfSubLevel(level, JOMLConversion.atLowerCornerOf(instance)).y;
    }
}
