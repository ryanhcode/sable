package dev.ryanhcode.sable.network.packets.udp;

import dev.ryanhcode.sable.network.udp.SableUDPPacket;
import dev.ryanhcode.sable.network.udp.SableUDPPacketType;
import dev.ryanhcode.sable.network.udp.SableUDPServer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;

import java.net.InetSocketAddress;

public record SableUDPServerboundAlivePacket() implements SableUDPPacket {
    public static final StreamCodec<RegistryFriendlyByteBuf, SableUDPServerboundAlivePacket> CODEC = StreamCodec.of((buf, value) -> {}, buf -> new SableUDPServerboundAlivePacket());

    @Override
    public SableUDPPacketType getType() {
        return SableUDPPacketType.ALIVE_SERVERBOUND;
    }

    @Override
    public void handleServer(final MinecraftServer server, final InetSocketAddress sender) {
        final SableUDPServer udpServer = SableUDPServer.getServer(server);

        if (udpServer != null) {
            udpServer.receiveAlivePacket(sender);
        }
    }
}
