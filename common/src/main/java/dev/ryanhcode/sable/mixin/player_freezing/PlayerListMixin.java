package dev.ryanhcode.sable.mixin.player_freezing;

import dev.ryanhcode.sable.mixinterface.player_freezing.PlayerFreezeExtension;
import dev.ryanhcode.sable.mixinterface.respawn_point.ServerPlayerRespawnExtension;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundFreezePlayerPacket;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * Mixin into {@link net.minecraft.server.players.PlayerList} to send the freeze packet on join and carry the frozen state across respawns.
 */
@Mixin(PlayerList.class)
public class PlayerListMixin {

    @Inject(method = "placeNewPlayer", at = @At("TAIL"))
    private void sable$player(final Connection connection, final ServerPlayer serverPlayer, final CommonListenerCookie commonListenerCookie, final CallbackInfo ci) {
        if (serverPlayer instanceof final PlayerFreezeExtension extension) {
            final UUID uuid = extension.sable$getFrozenToSubLevel();

            if (uuid != null) {
                serverPlayer.connection.send(new ClientboundCustomPayloadPacket(new ClientboundFreezePlayerPacket(uuid, extension.sable$getFrozenToSubLevelAnchor())));
            }
        }
    }

    @Inject(method = "respawn", at = @At("TAIL"))
    private void sable$respawn(final ServerPlayer oldPlayer, final boolean bl, final Entity.RemovalReason removalReason, final CallbackInfoReturnable<ServerPlayer> cir) {
        final ServerPlayer newPlayer = cir.getReturnValue();
        ((ServerPlayerRespawnExtension) newPlayer).sable$takeQueuedFreezeFrom(oldPlayer);
    }
}
