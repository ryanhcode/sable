package dev.ryanhcode.sable.fabric.mixin.fix_bundle_packet;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.util.thread.BlockableEventLoop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Fabric mixin into {@link ClientPacketListener} that disables the main-thread re-dispatch of bundle packets, as the
 * sub-level packet handling already runs on the correct thread.
 */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

    @Redirect(method = "handleBundlePacket", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/util/thread/BlockableEventLoop;)V"))
    public <T extends PacketListener> void handleOnCorrectThread(final Packet<T> packet, final T processor, final BlockableEventLoop<?> executor) {
        // FIXME this sucks, implement actual fix into fabric API
    }
}
