package dev.ryanhcode.sable.network.packets.udp;

import dev.ryanhcode.sable.mixinterface.udp.ConnectionExtension;
import dev.ryanhcode.sable.network.udp.AddressedSableUDPPacket;
import dev.ryanhcode.sable.network.udp.SableUDPPacket;
import dev.ryanhcode.sable.network.udp.SableUDPPacketType;
import dev.ryanhcode.sable.network.udp.SableUDPServer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.Level;

import java.net.InetSocketAddress;

public record SableUDPClientboundKeepAlivePacket() implements SableUDPPacket {
    public static final StreamCodec<RegistryFriendlyByteBuf, SableUDPClientboundKeepAlivePacket> CODEC = StreamCodec.of((buf, value) -> {}, buf -> new SableUDPClientboundKeepAlivePacket());

    @Override
    public SableUDPPacketType getType() {
        return SableUDPPacketType.KEEP_ALIVE_CLIENTBOUND;
    }

    @Override
    public void handleClient(final Level level) {
        final Connection connection = Minecraft.getInstance().getConnection().getConnection();
        final ConnectionExtension connectionExtension = (ConnectionExtension) connection;
        final Channel channel = connectionExtension.sable$getUDPChannel();

        final InetSocketAddress baseAddress = ((InetSocketAddress) connection.getRemoteAddress());
        final InetSocketAddress remoteAddress = new InetSocketAddress(
                baseAddress.getAddress(),
                SableUDPServer.getUDPPort(baseAddress.getPort()));

        channel.eventLoop().execute(() -> {
            final SableUDPServerboundAlivePacket packet = new SableUDPServerboundAlivePacket();

            final AddressedSableUDPPacket envelope = new AddressedSableUDPPacket(packet, remoteAddress);
            final ChannelFuture writeFuture = channel.writeAndFlush(envelope);

            writeFuture.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
        });
    }
}
