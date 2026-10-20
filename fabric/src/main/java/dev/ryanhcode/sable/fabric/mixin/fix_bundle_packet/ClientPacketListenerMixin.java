package dev.ryanhcode.sable.fabric.mixin.fix_bundle_packet;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.ryanhcode.sable.fabric.mixinhelper.fix_bundle_packet.FabricBundlePacketHack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

    @WrapMethod(method = "handleBundlePacket")
    private void sable$handleBundlePacket(final ClientboundBundlePacket packet, final Operation<Void> original) {
        if (!Minecraft.getInstance().isSameThread()) {
            original.call(packet);
            return;
        }

        FabricBundlePacketHack.IS_HANDLING_BUNDLE_PACKET.set(true);

        try {
            original.call(packet);
        } finally {
            FabricBundlePacketHack.IS_HANDLING_BUNDLE_PACKET.set(false);
        }
    }
}
