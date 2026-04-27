package dev.ryanhcode.sable.mixin.udp;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.network.udp.SableUDPServer;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Send a player a UDP authentication packet when they join the server in hopes for an {@link dev.ryanhcode.sable.network.packets.udp.SableUDPAuthenticationPacket} back
 */
@Mixin(PlayerList.class)
public class PlayerListMixin {

    @Inject(method = "placeNewPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V", ordinal = 0, shift = At.Shift.AFTER))
    private void onPlayerJoin(final Connection connection, final ServerPlayer serverPlayer, final CommonListenerCookie commonListenerCookie, final CallbackInfo ci) {
        final SableUDPServer server = SableUDPServer.getServer(serverPlayer.server);

        if (server == null) {
            return;
        }

        Sable.LOGGER.info("Beginning attempted authentication with player {}", serverPlayer.getName().getString());
        server.beginAuthentication(serverPlayer);
    }

}
