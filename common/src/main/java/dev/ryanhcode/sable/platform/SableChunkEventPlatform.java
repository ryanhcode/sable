package dev.ryanhcode.sable.platform;

import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.ApiStatus;

/**
 * Loader-specific events fired around chunk loading/unloading for sub-level plots.
 */
@ApiStatus.Internal
public interface SableChunkEventPlatform {
    SableChunkEventPlatform INSTANCE = SablePlatformUtil.load(SableChunkEventPlatform.class);

    void onClientChunkPacketReplaced(final LevelChunk chunk);

    void onOldChunkInvalid(final LevelChunk chunk);

    void onPlotChunkLoaded(final LevelChunk chunk);

}
