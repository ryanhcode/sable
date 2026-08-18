package dev.ryanhcode.sable.mixin.conduit;

import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ConduitBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ConduitBlockEntity.class)
public class ConduitBlockEntityMixin {
    @Redirect(method = "applyEffects", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;closerThan(Lnet/minecraft/core/Vec3i;D)Z"))
    private static boolean sable$closerThan(final BlockPos conduitPos, final Vec3i playerBlockPos, final double radius, @Local(argsOnly = true) final Level level) {
        return Sable.HELPER.distanceSquaredWithSubLevels(level, conduitPos.getCenter(), Vec3.atCenterOf(playerBlockPos)) < radius*radius;
    }
}
