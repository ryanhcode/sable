package dev.ryanhcode.sable.mixin.udp;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.SableConfig;
import dev.ryanhcode.sable.mixinterface.udp.ServerConnectionListenerExtension;
import dev.ryanhcode.sable.network.udp.SableUDPPacket;
import dev.ryanhcode.sable.network.udp.SableUDPServer;
import dev.ryanhcode.sable.network.udp.handler.SableUDPChannelHandlerServer;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalServerChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerConnectionListener;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.InetAddress;
import java.net.SocketAddress;
import java.util.List;

@Mixin(ServerConnectionListener.class)
public class ServerConnectionListenerMixin implements ServerConnectionListenerExtension {

    @Shadow
    @Final
    private List<ChannelFuture> channels;

    @Shadow
    @Final
    private MinecraftServer server;

    @Unique
    private SableUDPServer sable$server = null;

    @Inject(method = "startTcpServerListener", at = @At("HEAD"))
    private void sable$startTcpServerListener(final InetAddress inetAddress, final int port, final CallbackInfo ci) {
        if (SableConfig.DISABLE_UDP_PIPELINE.get()) {
            return;
        }

        synchronized (this.channels) {
            final Class<? extends Channel> channelClass;
            final EventLoopGroup eventLoopGroup;

            if (Epoll.isAvailable() && this.server.isEpollEnabled()) {
                channelClass = EpollDatagramChannel.class;
                eventLoopGroup = ServerConnectionListener.SERVER_EPOLL_EVENT_GROUP.get();
            } else {
                channelClass = NioDatagramChannel.class;
                eventLoopGroup = ServerConnectionListener.SERVER_EVENT_GROUP.get();
            }

            Sable.LOGGER.info("Adding UDP server channel future");

            this.channels.add(new Bootstrap()
                    .channel(channelClass)
                    .option(ChannelOption.SO_BROADCAST, true)
                    .handler(new ChannelInitializer<>() {
                        @Override
                        protected void initChannel(final Channel channel) {
                            SableUDPPacket.configureSerialization(channel.pipeline(), PacketFlow.SERVERBOUND, false, null);
                            ServerConnectionListenerMixin.this.sable$setupChannel(channel);
                        }
                    })
                    .group(eventLoopGroup)
                    .localAddress(inetAddress, port)
                    .bind()
                    .syncUninterruptibly());
        }
    }

    @Inject(method = "startMemoryChannel", at = @At("TAIL"))
    private void sable$startMemoryChannel(final CallbackInfoReturnable<SocketAddress> cir) {
        if (SableConfig.DISABLE_UDP_PIPELINE.get()) {
            return;
        }

        synchronized (this.channels) {
            Sable.LOGGER.info("Adding local UDP server channel future");

            this.channels.add(new Bootstrap()
                    .channel(LocalServerChannel.class)
                    .option(ChannelOption.SO_BROADCAST, true)
                    .handler(new ChannelInitializer<>() {
                        @Override
                        protected void initChannel(final Channel channel) {
                            SableUDPPacket.configureInMemoryPipeline(channel.pipeline(), PacketFlow.SERVERBOUND);
                            ServerConnectionListenerMixin.this.sable$setupChannel(channel);
                        }
                    })
                    .group(ServerConnectionListener.SERVER_EVENT_GROUP.get())
                    .localAddress(LocalAddress.ANY)
                    .bind()
                    .syncUninterruptibly());
        }
    }

    @Unique
    private void sable$setupChannel(final Channel channel) {
        final ChannelPipeline pipeline = channel.pipeline();

        pipeline.addLast(new SableUDPChannelHandlerServer(this.server, (ServerConnectionListener) (Object) this));
    }

    @Inject(method = "stop", at = @At("TAIL"))
    private void sable$stop(final CallbackInfo ci) {
        this.sable$server = null;
    }

    @Override
    public void sable$setupUDPServer(final Channel channel) {
        this.sable$server = new SableUDPServer(this.server, channel);
    }

    @Override
    @Nullable
    public SableUDPServer sable$getServer() {
        return this.sable$server;
    }
}
