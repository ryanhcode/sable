package dev.ryanhcode.sable.mixin.level_accelerator;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Invoker exposing the private getVisibleChunkIfPresent method of {@link net.minecraft.server.level.ServerChunkCache}.
 */
@Mixin(ServerChunkCache.class)
public interface ServerChunkCacheAccessor {

    @Invoker
    ChunkHolder invokeGetVisibleChunkIfPresent(long p_140328_);

}
