package dev.ryanhcode.sable.network.udp;

import dev.ryanhcode.sable.Sable;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageDecoder;
import net.minecraft.network.ProtocolSwapHandler;
import net.minecraft.network.RegistryFriendlyByteBuf;

import java.io.IOException;
import java.util.List;

public class SableUDPPacketDecoder extends MessageToMessageDecoder<DatagramPacket> implements ProtocolSwapHandler {

    public SableUDPPacketDecoder() {
        super(DatagramPacket.class);
    }

    /**
     * Decode from one message to an other. This method will be called for each written message that can be handled
     * by this decoder.
     *
     * @param ctx the {@link ChannelHandlerContext} which this {@link MessageToMessageDecoder} belongs to
     * @param msg the message to decode to an other one
     * @param out the {@link List} to which decoded messages should be added
     * @throws Exception is thrown if an error occurs
     */
    @Override
    protected void decode(final ChannelHandlerContext ctx, final DatagramPacket msg, final List<Object> out) throws Exception {
        final ByteBuf byteBuf = msg.content();
        final int i = byteBuf.readableBytes();
        if (i != 0) {
            final short packetID = byteBuf.readUnsignedByte();

            if (packetID >= SableUDPPacketType.VALUES.length) {
                throw new IOException("Received an invalid packet ID: " + packetID);
            }

            final SableUDPPacketType packetType = SableUDPPacketType.VALUES[packetID];
            final SableUDPPacket packet;

            try {
                packet = packetType.create(new RegistryFriendlyByteBuf(byteBuf, null));
            } catch (final Exception e) {
                Sable.LOGGER.error("Failed to decode UDP packet of type {} from {}", packetType, msg.sender(), e);
                return;
            }

            if (byteBuf.readableBytes() > 0) {
                Sable.LOGGER.error("SableUDPPacket {} ({}) was larger than expected, found {} bytes extra", packetType, packet.getClass().getSimpleName(), byteBuf.readableBytes());
                return;
            }

            out.add(new AddressedSableUDPPacket(packet, msg.sender()));
        }
    }
}
