package dev.ryanhcode.sable.mixin.loaded_chunk_debug;

import dev.ryanhcode.sable.mixinterface.loaded_chunk_debug.DebugLevelChunkExtension;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LevelChunk.class)
public class LevelChunkMixin implements DebugLevelChunkExtension {

    @Unique
    private long sable$lastUpdate;

    @Override
    public void sable$setUpdated() {
        this.sable$lastUpdate = System.currentTimeMillis();
    }

    @Override
    public long sable$getLastUpdate() {
        return this.sable$lastUpdate;
    }
}
