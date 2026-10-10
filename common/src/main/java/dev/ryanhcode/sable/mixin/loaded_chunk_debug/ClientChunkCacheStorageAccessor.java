package dev.ryanhcode.sable.mixin.loaded_chunk_debug;

import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Accessor exposing the private chunks array of {@link net.minecraft.client.multiplayer.ClientChunkCache.Storage}.
 */
@Mixin(ClientChunkCache.Storage.class)
public interface ClientChunkCacheStorageAccessor {

    @Accessor
    AtomicReferenceArray<LevelChunk> getChunks();
}
