package dev.ryanhcode.sable.sublevel.storage.serialization;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk;
import dev.ryanhcode.sable.sublevel.storage.region.SubLevelRegionFile;
import dev.ryanhcode.sable.sublevel.storage.region.SubLevelStorageFile;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.FileUtil;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ExceptionCollector;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Manages the sub-level holding chunks and regions for a level
 */
public class SubLevelStorage implements AutoCloseable {
    public static int MAX_CACHE_SIZE = 128;
    private final Long2ObjectLinkedOpenHashMap<SubLevelRegionFile> regionCache = new Long2ObjectLinkedOpenHashMap<>();
    private final Long2ObjectLinkedOpenHashMap<SubLevelStorageFile> storageCache = new Long2ObjectLinkedOpenHashMap<>();

    private final Path folder;

    public SubLevelStorage(final Path folder) {
        this.folder = folder;
    }

    private static @NotNull String getFileName(final ChunkPos chunkPos) {
        return "r." + chunkPos.getRegionX() + "." + chunkPos.getRegionZ();
    }

    private static @NotNull String getFileName(final ChunkPos chunkPos, final int index) {
        return "r." + chunkPos.getRegionX() + "." + chunkPos.getRegionZ() + "." + index;
    }

    private SubLevelRegionFile getRegionFile(final ChunkPos chunkPos) throws IOException {
        final long longKey = ChunkPos.asLong(chunkPos.getRegionX(), chunkPos.getRegionZ());
        final SubLevelRegionFile existingFile = this.regionCache.getAndMoveToFirst(longKey);

        if (existingFile != null) {
            return existingFile;
        }

        if (this.regionCache.size() >= MAX_CACHE_SIZE) {
            this.regionCache.removeLast().close();
        }

        final Path path = this.getPath(chunkPos);
        final Path externalPath = this.getExternalPath(chunkPos);
        final SubLevelRegionFile loadedRegion = new SubLevelRegionFile(path, externalPath);
        this.regionCache.putAndMoveToFirst(longKey, loadedRegion);
        return loadedRegion;
    }

    private SubLevelStorageFile getRegionStorageFile(final ChunkPos chunkPos, final int index) throws IOException {
        final long longKey = SectionPos.asLong(chunkPos.getRegionX(), index, chunkPos.getRegionZ());
        final SubLevelStorageFile existingFile = this.storageCache.getAndMoveToFirst(longKey);

        if (existingFile != null) {
            return existingFile;
        }

        if (this.storageCache.size() >= MAX_CACHE_SIZE) {
            this.storageCache.removeLast().close();
        }

        FileUtil.createDirectoriesSafe(this.folder);
        final Path path = this.getPath(chunkPos, index);
        final Path externalPath = this.getExternalPath(chunkPos);

        FileUtil.createDirectoriesSafe(externalPath);

        final SubLevelStorageFile loadedRegion = new SubLevelStorageFile(path, externalPath);
        this.storageCache.putAndMoveToFirst(longKey, loadedRegion);
        return loadedRegion;
    }

    public SubLevelHoldingChunk attemptLoadHoldingChunk(final ChunkPos chunkPos) {
        try {
            final SubLevelRegionFile regionFile = this.getRegionFile(chunkPos);
            return regionFile.read(chunkPos);
        } catch (final IOException e) {
            Sable.LOGGER.error("Failed to load holding chunk for " + chunkPos, e);
            return null;
        }
    }

    public void attemptSaveHoldingChunk(final ChunkPos chunkPos, final SubLevelHoldingChunk holdingChunk) {
        try {
            final SubLevelRegionFile regionFile = this.getRegionFile(chunkPos);
            regionFile.trySave(chunkPos.getRegionLocalX(), chunkPos.getRegionLocalZ(), holdingChunk);
        } catch (final IOException e) {
            Sable.LOGGER.error("Failed to save holding chunk for {}", chunkPos, e);

        }
    }

    /**
     * Attempts to load a {@link SubLevelData} from a position and pointer
     *
     * @param chunkPos the chunk position to load the sub-level from
     * @param pointer  the pointer to the sub-level in the storage file
     */
    public SubLevelData attemptLoadSubLevel(final ChunkPos chunkPos, final SavedSubLevelPointer pointer) {
        try {
            final SubLevelStorageFile storageFile = this.getRegionStorageFile(chunkPos, pointer.storageIndex());
            final CompoundTag tag = storageFile.read(pointer.subLevelIndex());

            if (tag == null) {
                Sable.LOGGER.error("Couldn't find sub-level at index {} in storage file for chunk {}", pointer.subLevelIndex(), chunkPos);
                return null;
            }
            final SubLevelData subLevel = SubLevelSerializer.fromData(tag);

            if (subLevel != null) {
                subLevel.setOriginLoadedChunk(chunkPos);
            } else {
                Sable.LOGGER.error("Failed to load sub-level at index {} in storage file for chunk {}", pointer.subLevelIndex(), chunkPos);
            }

            return subLevel;
        } catch (final IOException e) {
            Sable.LOGGER.error("Failed to load sub-level for " + chunkPos, e);
            return null;
        }
    }

    /**
     * Attempts to save a {@link SubLevelData} to a position, finding a non-occupied pointer.
     * This method will create a new storage file if necessary.
     * This will NOT save the sub-level to the chunk in the region.
     *
     * @param chunkPos the chunk position to save the sub-level to
     * @param subLevel the serialized sub-level to save
     * @return a new {@link GlobalSavedSubLevelPointer} containing the chunk position, storage index, and sub-level index
     */
    public GlobalSavedSubLevelPointer attemptSaveSubLevel(final ChunkPos chunkPos, final SubLevelData subLevel) {
        try {
            // until we find a storage index that is not fully occupied in indices
            int storageIndex = 0;

            while (true) {
                final SubLevelStorageFile storageFile = this.getRegionStorageFile(chunkPos, storageIndex);
                final int subLevelIndex = storageFile.findFreeIndex();

                if (subLevelIndex != -1 && subLevelIndex < storageFile.getTotalIndexCapacity()) {
                    // we found a free index, save the sub-level
                    storageFile.write(subLevelIndex, subLevel.fullTag());
                    return new GlobalSavedSubLevelPointer(chunkPos, (short) storageIndex, (short) subLevelIndex);
                }

                // increment the storage index to try the next one
                storageIndex++;
            }
        } catch (final IOException e) {
            Sable.LOGGER.error("Failed to save sub-level for " + chunkPos, e);
        }
        return null;
    }

    /**
     * Saves a sub-level to an already existing global pointer.
     *
     * @param pointer  the global pointer containing the chunk position, storage index, and sub-level index
     * @param subLevel the serialized sub-level to save
     */
    public void attemptSaveSubLevel(final GlobalSavedSubLevelPointer pointer, final SubLevelData subLevel) {
        try {
            final SubLevelStorageFile storageFile = this.getRegionStorageFile(pointer.chunkPos(), pointer.storageIndex());
            storageFile.write(pointer.subLevelIndex(), subLevel != null ? subLevel.fullTag() : null);
        } catch (final IOException e) {
            Sable.LOGGER.error("Failed to save sub-level for " + pointer.chunkPos(), e);
        }
    }

    /**
     * Gets the external path for large storage files for a given chunk position.
     *
     * @param chunkPos the chunk position to get the storage file for
     * @return the storage file for the chunk position
     */
    private @NotNull Path getExternalPath(final ChunkPos chunkPos) {
        return this.folder.resolve(getFileName(chunkPos) + ".r");
    }

    /**
     * Gets the external path for large storage files for a given chunk position.
     *
     * @param chunkPos the chunk position to get the path for
     * @param index    the index of the storage file (used for multiple storage files in a region)
     * @return the path to the storage file
     */
    private @NotNull Path getExternalPath(final ChunkPos chunkPos, final int index) {
        return this.folder.resolve(getFileName(chunkPos, index) + ".s");
    }

    /**
     * Gets a storage file for a given chunk position.
     *
     * @param chunkPos the chunk position to get the storage file for
     * @return the storage file for the chunk position
     */
    private @NotNull Path getPath(final ChunkPos chunkPos) {
        return this.folder.resolve(getFileName(chunkPos) + SubLevelRegionFile.FILE_EXTENSION);
    }

    /**
     * Gets the path for a storage file for a given chunk position.
     *
     * @param chunkPos the chunk position to get the path for
     * @param index    the index of the storage file (used for multiple storage files in a region)
     * @return the path to the storage file
     */
    private @NotNull Path getPath(final ChunkPos chunkPos, final int index) {
        return this.folder.resolve(getFileName(chunkPos, index) + SubLevelStorageFile.FILE_EXTENSION);
    }

    @Override
    public void close() throws IOException {
        final ExceptionCollector<IOException> exceptionCollector = new ExceptionCollector();

        for (final SubLevelStorageFile storageFile : this.storageCache.values()) {
            try {
                storageFile.close();
            } catch (final IOException e) {
                exceptionCollector.add(e);
            }
        }

        for (final SubLevelRegionFile regionFile : this.regionCache.values()) {
            try {
                regionFile.close();
            } catch (final IOException e) {
                exceptionCollector.add(e);
            }
        }

        exceptionCollector.throwIfPresent();
    }

    @NotNull
    @ApiStatus.Internal
    public Path getFolder() {
        return this.folder;
    }

    /**
     * Flushes all cached region and storage files to disk.
     */
    public void flush() throws IOException {
        for (final SubLevelRegionFile regionFile : this.regionCache.values()) {
            regionFile.flush();
        }

        for (final SubLevelStorageFile regionFile : this.storageCache.values()) {
            regionFile.flush();
        }
    }
}
