package dev.ryanhcode.sable.mixinterface.udp;

import dev.ryanhcode.sable.network.udp.SableUDPServer;
import io.netty.channel.Channel;
import org.jetbrains.annotations.Nullable;

public interface ServerConnectionListenerExtension {
    void sable$setupUDPServer(Channel channel);

    @Nullable
    SableUDPServer sable$getServer();
}
