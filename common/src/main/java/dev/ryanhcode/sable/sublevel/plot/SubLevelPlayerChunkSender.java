package dev.ryanhcode.sable.sublevel.plot;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;

import java.util.function.Consumer;

public class SubLevelPlayerChunkSender {

    /**
     * A version of {@link net.minecraft.server.network.PlayerChunkSender} that uses the plots light engine
     */
    public static void sendChunk(final Consumer<Packet<? super ClientGamePacketListener>> listener, final LevelLightEngine lightEngine, final LevelChunk chunk) {
        listener.accept(new ClientboundLevelChunkWithLightPacket(chunk, lightEngine, null, null));
    }

    /**
     * A version of {@link net.minecraft.server.network.PlayerChunkSender} that uses the plots light engine
     */
    public static void sendChunkPoiData(final ServerLevel level, final LevelChunk chunk) {
        final ChunkPos chunkPos = chunk.getPos();
        DebugPackets.sendPoiPacketsForChunk(level, chunkPos);
    }

    /**
     * Sends only light data for a chunk, without resending block data.
     */
    public static void sendLightUpdate(final Consumer<Packet<? super ClientGamePacketListener>> listener, final LevelLightEngine lightEngine, final ChunkPos pos) {
        listener.accept(new ClientboundLightUpdatePacket(pos, lightEngine, null, null));
    }

}
