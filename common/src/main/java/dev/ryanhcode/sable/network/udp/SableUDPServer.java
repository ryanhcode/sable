package dev.ryanhcode.sable.network.udp;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.SableClient;
import dev.ryanhcode.sable.SableConfig;
import dev.ryanhcode.sable.mixinterface.udp.ServerConnectionListenerExtension;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundSableUDPActivationPacket;
import dev.ryanhcode.sable.network.packets.udp.SableUDPClientboundKeepAlivePacket;
import dev.ryanhcode.sable.util.SableDistUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.local.LocalAddress;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;
import java.util.*;

/**
 * Handles UDP authentication and communication
 *
 * @author RyanH, Ocelot
 */
public class SableUDPServer {
    public static final long PING_INTERVAL = 2500;
    private static final int MISSED_PINGS_ALLOWED = 10;

    private final Channel channel;
    private final Map<Connection, SableUDPAuthenticationState> udpAuthStates;
    private final MinecraftServer server;
    private int pingIndex = 0;

    public SableUDPServer(final MinecraftServer server, final Channel channel) {
        this.server = server;
        this.channel = channel;
        this.udpAuthStates = new WeakHashMap<>();
    }

    /**
     * Retrieves the current instance of the UDP server from a {@link MinecraftServer}
     */
    @ApiStatus.Internal
    @Nullable
    public static SableUDPServer getServer(final MinecraftServer server) {
        return (((ServerConnectionListenerExtension) server.getConnection())).sable$getServer();
    }

    /*@Override
    public void flushUDP() {
        if (this.udpChannel.eventLoop().inEventLoop())
            throw new IllegalStateException("Cannot flush from event loop");

        this.udpChannel.eventLoop().execute(() -> {
            this.udpChannel.flush();
        });
    }*/

    /**
     * Checks if UDP packets can be sent to a player
     *
     * @param player the player to check
     * @return if UDP packets can be sent to the player
     */
    public boolean isConnectedTo(final ServerPlayer player) {
        if (!SableConfig.ATTEMPT_UDP_NETWORKING.get()) {
            return false;
        }

        if (player.connection.getRemoteAddress() instanceof LocalAddress) {
            if (player.server.isSingleplayer() && player.server.isSingleplayerOwner(player.getGameProfile()))
                return true;
        }

        final Connection connection = player.connection.connection;
        final SableUDPAuthenticationState authState = this.udpAuthStates.get(connection);
        return authState != null && authState.getState() == SableUDPAuthenticationState.State.AUTHENTICATED;
    }

    /**
     * Sends a UDP packet to a server player, flushing if requested
     *
     * @param player the player to send the packet to
     * @param packet the packet to send
     * @param flush  whether to flush the packet immediately
     * @return if the packet has been successfully sent
     */
    public boolean sendUDPPacket(final ServerPlayer player, final SableUDPPacket packet, final boolean flush) {
        if (this.channel.eventLoop().inEventLoop())
            throw new IllegalStateException("Cannot send packet from event loop");

        final Connection connection = player.connection.connection;

        if (connection.getRemoteAddress() instanceof LocalAddress) {
            // We can't turn a local address into an InetSocketAddress, because there's no net communication
            // Let's instead locally send the packet.

            this.sendUDPPacketLocal(packet);
            return true;
        }

        final SableUDPAuthenticationState authenticationState = this.udpAuthStates.get(connection);

        if (authenticationState == null) {
            Sable.LOGGER.error("Attempted to send packet to player \"{}\" without authentication state", player.getName().getString());
            return false;
        }

        final InetSocketAddress inetSocketAddress = authenticationState.getActiveAddress();
        if (inetSocketAddress == null) {
            Sable.LOGGER.error("No UDP address in authentication state for player \"{}\"", player.getName().getString());
            return false;
        }

        this.channel.eventLoop().execute(() -> {
            final AddressedSableUDPPacket envelope = new AddressedSableUDPPacket(packet, inetSocketAddress);
            final ChannelFuture writeFuture = flush ? this.channel.writeAndFlush(envelope) : this.channel.write(envelope);

            writeFuture.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
        });

        return true;
    }

    /**
     * Sends a UDP packet to the locally connected player
     *
     * @param packet the packet to send
     */
    private void sendUDPPacketLocal(final SableUDPPacket packet) {
        final RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), this.server.registryAccess());
        packet.getType().write(buffer, packet);
        final SableUDPPacket decodedPacket = packet.getType().create(buffer);
        SableClient.NETWORK_EVENT_LOOP.tell(() -> decodedPacket.handleClient(SableDistUtil.getClientLevel()));
    }

    /**
     * Begins the authentication process for a {@link ServerPlayer}, assigning and sending them a token
     *
     * @param player the player to authenticate
     */
    @ApiStatus.Internal
    public void beginAuthentication(final ServerPlayer player) {
        if (player.connection.getRemoteAddress() instanceof LocalAddress) {
            // No point in sending a UDP activation packet to a local player - local communication will be used anyway
            // instead of the UDP channel
            return;
        }

        // Generate and store a token
        final UUID token = UUID.randomUUID();
        final SableUDPAuthenticationState authState = new SableUDPAuthenticationState(token);

        this.udpAuthStates.put(player.connection.connection, authState);

        // Send the token to the client
        if (SableConfig.ATTEMPT_UDP_NETWORKING.get()) {
            player.connection.send(new ClientboundCustomPayloadPacket(new ClientboundSableUDPActivationPacket(token)));
        }
    }

    /**
     * Called on reception of an authentication packet from a client
     *
     * @param uuid              the UUID token received from the client
     * @param inetSocketAddress the client address that broadcasted the token
     */
    @ApiStatus.Internal
    public void receiveAuthenticationPacket(final UUID uuid, final InetSocketAddress inetSocketAddress) {
        for (final Map.Entry<Connection, SableUDPAuthenticationState> entry : this.udpAuthStates.entrySet()) {
            final SableUDPAuthenticationState state = entry.getValue();

            if (state.isExpectedToken(uuid)) {
                state.assignAddress(inetSocketAddress);
                state.setLastAlivePingIndex(this.pingIndex);
                Sable.LOGGER.info("UDP authentication complete with {}, UDP routing to {}", entry.getKey().getRemoteAddress(), inetSocketAddress);
                return;
            }
        }
    }

    /**
     * Sends keep-alive packets to all authenticated & connected clients
     */
    public void sendPings() {
        final Iterator<Map.Entry<Connection, SableUDPAuthenticationState>> iter = this.udpAuthStates.entrySet().iterator();

        while (iter.hasNext()) {
            final Map.Entry<Connection, SableUDPAuthenticationState> entry = iter.next();
            final Connection connection = entry.getKey();
            final SableUDPAuthenticationState state = entry.getValue();

            if (!connection.isConnected()) {
                iter.remove();
                continue;
            }

            if (state.getState() != SableUDPAuthenticationState.State.AUTHENTICATED) {
                continue;
            }

            if (this.pingIndex - state.getLastAlivePingIndex() > MISSED_PINGS_ALLOWED) {
                Sable.LOGGER.warn("UDP connection with {} failed to respond to any keep-alive packets after ~{}ms, kicking them to TCP", connection.getRemoteAddress(), MISSED_PINGS_ALLOWED * PING_INTERVAL);
                iter.remove();
                continue;
            }

            final InetSocketAddress inetSocketAddress = state.getActiveAddress();

            if (inetSocketAddress == null) {
                continue;
            }

            final SableUDPClientboundKeepAlivePacket packet = new SableUDPClientboundKeepAlivePacket();

            this.channel.eventLoop().execute(() -> {
                final AddressedSableUDPPacket envelope = new AddressedSableUDPPacket(packet, inetSocketAddress);
                final ChannelFuture writeFuture = this.channel.writeAndFlush(envelope);

                writeFuture.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
            });
        }

        this.pingIndex ++;
    }

    /**
     * Marks a client as alive
     */
    public void receiveAlivePacket(final InetSocketAddress sender) {
        for (final SableUDPAuthenticationState state : this.udpAuthStates.values()) {
            if (state.getState() != SableUDPAuthenticationState.State.AUTHENTICATED) {
                continue;
            }

            if (Objects.equals(state.getActiveAddress(), sender)) {
                state.setLastAlivePingIndex(this.pingIndex);
                return;
            }
        }
    }
}
