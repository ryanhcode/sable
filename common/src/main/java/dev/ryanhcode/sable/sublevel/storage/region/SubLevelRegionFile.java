package dev.ryanhcode.sable.sublevel.storage.region;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;

/**
 * A sub-level region file, mapping local chunk positions
 * to collections of sub-level file pointers.
 * </br>
 * Region files are always formatted (x_z.slvlr), with x and z
 * representing the coordinates of the region in chunks.
 * Sub-level file pointers are a single byte to represent the
 * index of the storage file, and a short (max 1024) to represent
 * the index of the sub-level in the storage file.
 * </br>
 * Each region is sized 32x32 chunks
 */
public class SubLevelRegionFile extends SubLevelStorageFile {
    public static final String FILE_EXTENSION = ".slvlr";
    public static int SECTOR_SIZE = 128;
    public static int SIDE_LENGTH = 32;
    public static int LOG_SIDE_LENGTH = 5; // log2(32) = 5

    public SubLevelRegionFile(final Path path, final Path externalFilePath) throws IOException {
        super(path, externalFilePath, SECTOR_SIZE);
    }

    public void trySave(final int localX, final int localZ, final SubLevelHoldingChunk chunk) {
        final CompoundTag tag = new CompoundTag();

        try {
            chunk.writeTo(tag);
            this.write(this.getIndex(localX, localZ), tag);
        } catch (final IOException e) {
            Sable.LOGGER.error("Failed to write sub-level holding chunk at ({}, {})", localX, localZ, e);
        }
    }

    public void tryRemove(final int localX, final int localZ) {
        try {
            this.write(this.getIndex(localX, localZ), (CompoundTag) null);
        } catch (final IOException e) {
            Sable.LOGGER.error("Failed to remove sub-level holding chunk at ({}, {})", localX, localZ, e);
        }
    }

    @Nullable
    public SubLevelHoldingChunk read(final ChunkPos chunkPos) {
        final int localX = chunkPos.getRegionLocalX();
        final int localZ = chunkPos.getRegionLocalZ();
        try {
            final CompoundTag tag = this.read(this.getIndex(localX, localZ));
            if (tag == null) {
                return null;
            }
            return SubLevelHoldingChunk.from(chunkPos, tag);
        } catch (final IOException e) {
            Sable.LOGGER.error("Failed to read sub-level holding chunk at ({}, {})", localX, localZ, e);
            return null;
        }
    }

    public int getIndex(final int localX, final int localZ) {
        return localX | (localZ << LOG_SIDE_LENGTH);
    }
}
