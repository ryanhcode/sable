package dev.ryanhcode.sable.fabric.platform;

import dev.ryanhcode.sable.platform.SableChunkEventPlatform;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.server.level.ServerLevel;import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class SableChunkEventPlatformImpl implements SableChunkEventPlatform {

    @Override
    public void onChunkPacketReplaced(final LevelChunk chunk) {
        ClientChunkEvents.CHUNK_LOAD.invoker().onChunkLoad((ClientLevel) chunk.getLevel(), chunk);
    }

    @Override
    public void onOldChunkInvalid(final LevelChunk chunk) {
        ClientChunkEvents.CHUNK_UNLOAD.invoker().onChunkUnload((ClientLevel) chunk.getLevel(), chunk);
    }

    @Override
    public void onServerChunkLoad(final LevelChunk chunk) {
        ServerChunkEvents.CHUNK_LOAD.invoker().onChunkLoad((ServerLevel) chunk.getLevel(), chunk);
    }
}
