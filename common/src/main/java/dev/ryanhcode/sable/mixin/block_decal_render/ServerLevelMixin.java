package dev.ryanhcode.sable.mixin.block_decal_render;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Fixes {@link net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket ClientboundBlockDestructionPackets} not being sent to players outside of a hardcoded range
 */
@Mixin(ServerLevel.class)
public class ServerLevelMixin {

    @ModifyExpressionValue(method = "destroyBlockProgress", at = @At(value = "CONSTANT", args = "doubleValue=1024.0"))
    private double sable$blockDamageDistance(final double originalBlockDamageDistanceConstant) {
        return Double.MAX_VALUE;
    }

}
