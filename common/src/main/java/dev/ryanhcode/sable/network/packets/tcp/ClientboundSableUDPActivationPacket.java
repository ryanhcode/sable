package dev.ryanhcode.sable.network.packets.tcp;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.mixinterface.udp.ConnectionExtension;
import dev.ryanhcode.sable.network.packets.udp.SableUDPAuthenticationPacket;
import dev.ryanhcode.sable.network.tcp.SableTCPPacket;
import dev.ryanhcode.sable.network.udp.AddressedSableUDPPacket;
import dev.ryanhcode.sable.network.udp.SableUDPServer;
import foundry.veil.api.network.handler.PacketContext;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.net.InetSocketAddress;
import java.util.UUID;

public record ClientboundSableUDPActivationPacket(UUID uuid) implements SableTCPPacket {

    public static final Type<ClientboundSableUDPActivationPacket> TYPE = new Type<>(Sable.sablePath("udp_activation"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundSableUDPActivationPacket> CODEC = StreamCodec.of((buf, value) -> value.write(buf), ClientboundSableUDPActivationPacket::read);

    private void write(final FriendlyByteBuf buf) {
        buf.writeUUID(this.uuid);
    }

    private static ClientboundSableUDPActivationPacket read(final FriendlyByteBuf buf) {
        return new ClientboundSableUDPActivationPacket(buf.readUUID());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public void handle(final PacketContext context) {
        final Connection connection = Minecraft.getInstance().getConnection().getConnection();
        final ConnectionExtension connectionExtension = (ConnectionExtension) connection;
        final Channel channel = connectionExtension.sable$getUDPChannel();

        final InetSocketAddress baseAddress = ((InetSocketAddress) connection.getRemoteAddress());
        final InetSocketAddress remoteAddress = new InetSocketAddress(
                baseAddress.getAddress(),
                SableUDPServer.getUDPPort(baseAddress.getPort())
        );

        Sable.LOGGER.info("Received authentication request, sending response over UDP to {}", remoteAddress);

        channel.eventLoop().execute(() -> {
            final SableUDPAuthenticationPacket packet = new SableUDPAuthenticationPacket(this.uuid.toString());

            final AddressedSableUDPPacket envelope = new AddressedSableUDPPacket(packet, remoteAddress);
            final ChannelFuture writeFuture = channel.writeAndFlush(envelope);

            writeFuture.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
        });
    }
}