package dev.ryanhcode.sable.platform;

import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public interface SableChunkEventPlatform {
    SableChunkEventPlatform INSTANCE = SablePlatformUtil.load(SableChunkEventPlatform.class);

    void onChunkPacketReplaced(final LevelChunk chunk);

    void onOldChunkInvalid(final LevelChunk chunk);
}
