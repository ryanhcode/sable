package dev.ryanhcode.sable.mixin.udp;

import dev.ryanhcode.sable.network.udp.SableUDPServer;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {

    @Unique
    private long sable$lastPingTime = 0;

    @Inject(method = "tickServer", at = @At("TAIL"))
    private void sable$keepUdpSocketsAlive(final BooleanSupplier booleanSupplier, final CallbackInfo ci) {
        final SableUDPServer server = SableUDPServer.getServer((MinecraftServer) (Object) this);
        if (server == null) {
            return;
        }

        final long time = System.currentTimeMillis();

        if (time - this.sable$lastPingTime > SableUDPServer.PING_INTERVAL) {
            server.sendPings();

            this.sable$lastPingTime = time;
        }
    }
}
