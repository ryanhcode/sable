package dev.ryanhcode.sable.mixin.chunk_container_replacement;

import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Mixin into {@link net.minecraft.world.level.chunk.LevelChunkSection} to support replacing chunk sections inside sub-level plots.
 */
@Mixin(LevelChunkSection.class)
public class LevelChunkSectionMixin {
}
