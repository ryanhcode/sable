package dev.ryanhcode.sable.neoforge.platform;

import dev.ryanhcode.sable.platform.SableChunkEventPlatform;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class SableChunkEventPlatformImpl implements SableChunkEventPlatform {

    @Override
    public void onChunkPacketReplaced(final LevelChunk chunk) {
        NeoForge.EVENT_BUS.post(new ChunkEvent.Load(chunk, false));
    }

    @Override
    public void onOldChunkInvalid(final LevelChunk chunk) {
        // no-op
    }

    @Override
    public void onServerChunkLoad(final LevelChunk chunk) {
        NeoForge.EVENT_BUS.post(new ChunkEvent.Load(chunk, false));
    }
}
