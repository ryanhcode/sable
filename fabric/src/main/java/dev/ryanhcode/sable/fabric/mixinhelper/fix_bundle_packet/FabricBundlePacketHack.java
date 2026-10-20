package dev.ryanhcode.sable.fabric.mixinhelper.fix_bundle_packet;

import io.netty.util.concurrent.FastThreadLocal;

public class FabricBundlePacketHack {

    public static final FastThreadLocal<Boolean> IS_HANDLING_BUNDLE_PACKET = new FastThreadLocal<>();

}
