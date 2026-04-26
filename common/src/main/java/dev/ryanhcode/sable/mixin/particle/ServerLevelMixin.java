package dev.ryanhcode.sable.mixin.particle;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Makes the particle distance check take into account sub-levels
 */
@Mixin(ServerLevel.class)
public class ServerLevelMixin {

    @WrapOperation(method = "sendParticles(Lnet/minecraft/server/level/ServerPlayer;ZDDDLnet/minecraft/network/protocol/Packet;)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;closerToCenterThan(Lnet/minecraft/core/Position;D)Z"))
    private boolean sable$sendParticlesCloserToCenterThan(final BlockPos blockPos, final Position pos, final double distance, Operation<Boolean> original) {
        return Sable.HELPER.distanceSquaredWithSubLevels((ServerLevel) (Object) this, pos, blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5) < distance * distance;
    }
}
