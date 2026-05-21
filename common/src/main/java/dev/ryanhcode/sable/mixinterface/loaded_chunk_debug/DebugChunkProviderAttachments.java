package dev.ryanhcode.sable.mixinterface.loaded_chunk_debug;

import net.minecraft.world.level.chunk.LevelChunk;
import java.util.Collection;

public interface DebugChunkProviderAttachments {

    Collection<LevelChunk> sable$loadedChunks();
}
