package dev.ryanhcode.sable.mixin.entity.entities_stick_sublevels.player;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.mixinterface.entity.entities_stick_sublevels.player.ServerboundMovePlayerPacketExtension;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public class ServerGamePacketListenerImplMixin {

    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleMovePlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V", shift = At.Shift.AFTER))
    public void handleMovePlayer(final ServerboundMovePlayerPacket packet, final CallbackInfo ci) {
        ((ServerboundMovePlayerPacketExtension) packet).sable$handle(this.player);
    }

    /**
     * FIXME: Don't just disable this check to handle sub-level freezing
     */
    @WrapOperation(method = "handleMovePlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;isChangingDimension()Z"))
    private boolean sable$disableMovedTooQuicklyCheck(final ServerPlayer instance, final Operation<Boolean> original) {
        if (Sable.HELPER.getTrackingSubLevel(instance) != null) {
            return true;
        }

        return original.call(instance);
    }

}
