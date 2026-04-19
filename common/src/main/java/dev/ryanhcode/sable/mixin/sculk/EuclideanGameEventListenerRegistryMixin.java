package dev.ryanhcode.sable.mixin.sculk;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gameevent.EuclideanGameEventListenerRegistry;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EuclideanGameEventListenerRegistry.class)
public abstract class EuclideanGameEventListenerRegistryMixin {
    @WrapOperation(method = "getPostableListenerPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;distSqr(Lnet/minecraft/core/Vec3i;)D"))
    private static double replaceDistance(final BlockPos from, final Vec3i to, final Operation<Double> original, @Local(argsOnly = true) final ServerLevel level) {
        return Sable.HELPER.distanceSquaredWithSubLevels(level, Vec3.atLowerCornerOf(from), Vec3.atLowerCornerOf(to));
    }
}
