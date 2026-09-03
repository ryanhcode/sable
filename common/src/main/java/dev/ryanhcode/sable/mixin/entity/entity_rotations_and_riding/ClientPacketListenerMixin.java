package dev.ryanhcode.sable.mixin.entity.entity_rotations_and_riding;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into {@link net.minecraft.client.multiplayer.ClientPacketListener} to project the player position out of the sub-level when handling position correction packets.
 */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

    @Inject(method = "handleMovePlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;send(Lnet/minecraft/network/protocol/Packet;)V", ordinal = 0, shift = At.Shift.BEFORE))
    private void sable$onHandleMovePlayer(final ClientboundPlayerPositionPacket clientboundPlayerPositionPacket, final CallbackInfo ci) {
        final Player player = Minecraft.getInstance().player;

        final SubLevel subLevel = Sable.HELPER.getContaining(player);
        if (subLevel != null) {
            player.setPos(subLevel.logicalPose().transformPosition(player.position()));
            EntitySubLevelUtil.setOldPosNoMovement(player);
        }
    }

}
