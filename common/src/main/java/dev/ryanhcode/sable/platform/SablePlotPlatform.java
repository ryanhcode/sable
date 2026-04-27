package dev.ryanhcode.sable.platform;

import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.chunk.LevelChunk;

public interface SablePlotPlatform {
    SablePlotPlatform INSTANCE = SablePlatformUtil.load(SablePlotPlatform.class);

    void readLightData(final CompoundTag tag, final RegistryAccess registryAccess, final LevelChunk chunk);

    void readChunkAttachments(final CompoundTag tag, final RegistryAccess registryAccess, final LevelChunk chunk);

    void postLoad(final CompoundTag tag, final LevelChunk chunk);

    void writeLightData(final CompoundTag tag, final RegistryAccess registryAccess, final LevelChunk chunk);

    void writeChunkAttachments(final CompoundTag tag, final RegistryAccess registryAccess, final LevelChunk chunk);
}
