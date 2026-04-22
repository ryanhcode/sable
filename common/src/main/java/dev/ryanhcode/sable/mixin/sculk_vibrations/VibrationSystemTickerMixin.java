package dev.ryanhcode.sable.mixin.sculk_vibrations;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gameevent.BlockPositionSource;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.level.gameevent.vibrations.VibrationInfo;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;

@Mixin(VibrationSystem.Ticker.class)
public interface VibrationSystemTickerMixin {

    @WrapOperation(method = "receiveVibration", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/gameevent/vibrations/VibrationInfo;pos()Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 sable$useGlobalPos(final VibrationInfo instance, final Operation<Vec3> original, @Local(argsOnly = true) final ServerLevel level) {
        return Sable.HELPER.projectOutOfSubLevel(level, original.call(instance));
    }

    @WrapOperation(method = {"receiveVibration", "lambda$trySelectAndScheduleVibration$0", "method_51408", "tryReloadVibrationParticle"}, expect = 3, require = 3,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/gameevent/vibrations/VibrationSystem$User;getPositionSource()Lnet/minecraft/world/level/gameevent/PositionSource;"))
    private static PositionSource sable$useGlobalDestPos(final VibrationSystem.User instance, final Operation<PositionSource> original, @Local(argsOnly = true) final ServerLevel level) {
        final PositionSource origSource = original.call(instance);
        final Optional<Vec3> optPos = origSource.getPosition(level);
        if (optPos.isPresent()) {
            return new BlockPositionSource(BlockPos.containing(Sable.HELPER.projectOutOfSubLevel(level, optPos.get())));
        }
        return origSource;
    }
}
