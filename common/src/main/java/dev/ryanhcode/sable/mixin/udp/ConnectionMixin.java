package dev.ryanhcode.sable.mixin.udp;

import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.SableClient;
import dev.ryanhcode.sable.SableConfig;
import dev.ryanhcode.sable.mixinterface.udp.ConnectionExtension;
import dev.ryanhcode.sable.network.udp.SableUDPPacket;
import dev.ryanhcode.sable.network.udp.handler.SableUDPChannelHandlerClient;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.protocol.PacketFlow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;

@Mixin(Connection.class)
public abstract class ConnectionMixin implements ConnectionExtension {

    @Unique
    private Channel sable$udpChannel = null;

    @Override
    public void sable$setUDPChannel(final Channel channel) {
        this.sable$udpChannel = channel;
    }

    @Inject(method = "disconnect(Lnet/minecraft/network/DisconnectionDetails;)V", at = @At("TAIL"))
    private void sable$onDisconnect(final DisconnectionDetails disconnectionDetails, final CallbackInfo ci) {
        final Channel channel = this.sable$udpChannel;
        if (this.sable$udpChannel != null && this.sable$udpChannel.isOpen()) {
            this.sable$udpChannel = null;

            Sable.LOGGER.debug("[sable-udp] closing UDP channel on disconnect, reason={}",
                    disconnectionDetails != null ? disconnectionDetails.reason().getString() : "<no details>");

            channel.close().awaitUninterruptibly().addListener((x) -> {
                if (x.isSuccess()) {
                    Sable.LOGGER.info("Closed UDP channel!");
                } else {
                    Sable.LOGGER.warn("Failed to close UDP channel", x.cause());
                }
            });
        }
    }

    @Override
    public Channel sable$getUDPChannel() {
        return this.sable$udpChannel;
    }

    @Unique
    private static final long SABLE$UDP_CONNECT_TIMEOUT_MS = 5_000L;

    @Inject(method = "connect", at = @At("TAIL"))
    private static void sable$connect(final InetSocketAddress inetSocketAddress, final boolean bl, final Connection connection, final CallbackInfoReturnable<ChannelFuture> cir) {
        if (SableConfig.DISABLE_UDP_PIPELINE.get()) {
            Sable.LOGGER.debug("[sable-udp] client UDP pipeline disabled via config; skipping bootstrap for {}", inetSocketAddress);
            return;
        }

        final long startNs = System.nanoTime();
        final boolean useNativeTransport = SableClient.useNativeTransport();

        final Class<? extends Channel> channelClass;
        final EventLoopGroup eventLoopGroup;

        if (Epoll.isAvailable() && useNativeTransport) {
            channelClass = EpollDatagramChannel.class;
            eventLoopGroup = Connection.NETWORK_EPOLL_WORKER_GROUP.get();
        } else {
            channelClass = NioDatagramChannel.class;
            eventLoopGroup = Connection.NETWORK_WORKER_GROUP.get();
        }

        Sable.LOGGER.info("Starting remote client UDP channel future (remote={}, transport={})",
                inetSocketAddress, channelClass.getSimpleName());

        final ChannelFuture channelFuture;
        try {
            channelFuture = new Bootstrap().group(eventLoopGroup).handler(new ChannelInitializer<>() {
                        @Override
                        protected void initChannel(final Channel channel) {
                            channel.config().setOption(ChannelOption.SO_KEEPALIVE, true);
                            SableUDPPacket.configureSerialization(channel.pipeline(), PacketFlow.CLIENTBOUND, false, null);
                            sable$setupChannel(channel, connection);
                        }
                    })
                    .channel(channelClass)
                    .connect(inetSocketAddress.getAddress(), inetSocketAddress.getPort());
        } catch (final Throwable t) {
            Sable.LOGGER.error("[sable-udp] Bootstrap.connect threw for {} (elapsedMs={}); continuing without UDP",
                    inetSocketAddress, (System.nanoTime() - startNs) / 1_000_000L, t);
            return;
        }

        final boolean completed = channelFuture.awaitUninterruptibly(SABLE$UDP_CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        final long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;

        if (!completed) {
            Sable.LOGGER.warn("[sable-udp] UDP connect did not complete within {}ms for remote={} (elapsedMs={}); cancelling future and falling back to TCP-only. This usually indicates the OS-level UDP connect() call is blocking - check antivirus/firewall outbound UDP rules.",
                    SABLE$UDP_CONNECT_TIMEOUT_MS, inetSocketAddress, elapsedMs);
            channelFuture.cancel(true);
            channelFuture.addListener((ChannelFutureListener) f -> {
                if (f.isCancelled()) {
                    return;
                }
                if (f.cause() != null) {
                    Sable.LOGGER.warn("[sable-udp] late UDP connect failure for {}: {}", inetSocketAddress, f.cause().toString());
                } else if (f.isSuccess() && f.channel() != null) {
                    Sable.LOGGER.debug("[sable-udp] late UDP connect success for {} after timeout; closing channel", inetSocketAddress);
                    f.channel().close();
                }
            });
            return;
        }

        if (!channelFuture.isSuccess()) {
            Sable.LOGGER.warn("[sable-udp] UDP connect failed for remote={} (elapsedMs={}); falling back to TCP-only. cause={}",
                    inetSocketAddress, elapsedMs, channelFuture.cause() != null ? channelFuture.cause().toString() : "<no cause>");
            return;
        }

        Sable.LOGGER.debug("[sable-udp] UDP connect succeeded for remote={} (elapsedMs={})", inetSocketAddress, elapsedMs);
    }

    @Inject(method = "connectToLocalServer", at = @At("TAIL"))
    private static void sable$connectToLocalServer(final SocketAddress socketAddress, final CallbackInfoReturnable<Connection> cir, @Local final Connection connection) {
        if (SableConfig.DISABLE_UDP_PIPELINE.get()) {
            Sable.LOGGER.debug("[sable-udp] local UDP pipeline disabled via config; skipping bootstrap for {}", socketAddress);
            return;
        }

        final long startNs = System.nanoTime();
        Sable.LOGGER.info("Starting local client UDP channel future");

        final ChannelFuture channelFuture;
        try {
            channelFuture = new Bootstrap().group(Connection.LOCAL_WORKER_GROUP.get()).handler(new ChannelInitializer<>() {
                @Override
                protected void initChannel(final Channel channel) {
                    SableUDPPacket.configureInMemoryPipeline(channel.pipeline(), PacketFlow.CLIENTBOUND);
                    sable$setupChannel(channel, connection);
                }
            }).channel(LocalChannel.class).connect(socketAddress);
        } catch (final Throwable t) {
            Sable.LOGGER.error("[sable-udp] local Bootstrap.connect threw for {} (elapsedMs={})",
                    socketAddress, (System.nanoTime() - startNs) / 1_000_000L, t);
            return;
        }

        final boolean completed = channelFuture.awaitUninterruptibly(SABLE$UDP_CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        final long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;
        if (!completed) {
            Sable.LOGGER.warn("[sable-udp] local UDP connect timed out after {}ms for {} (elapsedMs={}); cancelling",
                    SABLE$UDP_CONNECT_TIMEOUT_MS, socketAddress, elapsedMs);
            channelFuture.cancel(true);
            return;
        }
        if (!channelFuture.isSuccess()) {
            Sable.LOGGER.warn("[sable-udp] local UDP connect failed for {} (elapsedMs={}): {}",
                    socketAddress, elapsedMs, channelFuture.cause() != null ? channelFuture.cause().toString() : "<no cause>");
            return;
        }
        Sable.LOGGER.debug("[sable-udp] local UDP connect succeeded for {} (elapsedMs={})", socketAddress, elapsedMs);
    }

    @Unique
    private static void sable$setupChannel(final Channel channel, final Connection connection) {
        final ChannelPipeline pipeline = channel.pipeline();
        pipeline.addLast(new SableUDPChannelHandlerClient(connection));
    }

}
