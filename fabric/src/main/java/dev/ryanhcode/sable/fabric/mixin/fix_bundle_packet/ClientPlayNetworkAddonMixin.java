package dev.ryanhcode.sable.fabric.mixin.fix_bundle_packet;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.fabric.mixinhelper.fix_bundle_packet.FabricBundlePacketHack;
import net.fabricmc.fabric.impl.networking.client.ClientPlayNetworkAddon;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientPlayNetworkAddon.class)
public abstract class ClientPlayNetworkAddonMixin {

    @WrapOperation(method = "receive(Lnet/fabricmc/fabric/api/client/networking/v1/ClientPlayNetworking$PlayPayloadHandler;Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;execute(Ljava/lang/Runnable;)V"))
    private void sable$receiveFabricPacket(final Minecraft instance, final Runnable runnable, final Operation<Void> original) {
        if (FabricBundlePacketHack.IS_HANDLING_BUNDLE_PACKET.get() == Boolean.TRUE && instance.isSameThread()) {
            runnable.run();
        } else {
            original.call(instance, runnable);
        }
    }

}
