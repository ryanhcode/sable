package dev.ryanhcode.sable.sublevel.storage.holding;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.system.ticket.PhysicsChunkTicketManager;
import it.unimi.dsi.fastutil.objects.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * A chunk-sized container of unloaded sub-level data in memory
 */
@ApiStatus.Internal
public class SubLevelHoldingChunk {
    private final ObjectList<HoldingSubLevel> alsoLoad = new ObjectArrayList<>();
    private final ObjectList<SavedSubLevelPointer> pointers = new ObjectArrayList<>();
    private final Object2ObjectMap<UUID, HoldingSubLevel> loadedHoldingSubLevels = new Object2ObjectOpenHashMap<>();
    private final ChunkPos pos;
    final ObjectOpenHashSet<UUID> visitedSet = new ObjectOpenHashSet<>();
    private boolean keepLoaded = false;

    public SubLevelHoldingChunk(final ChunkPos pos) {
        this.pos = pos;
    }

    public void acceptHoldingSubLevel(final HoldingSubLevel subLevelData) {
        this.loadedHoldingSubLevels.put(subLevelData.data().uuid(), subLevelData);
    }

    public Iterable<HoldingSubLevel> getLoadedHoldingSubLevels() {
        return this.loadedHoldingSubLevels.values();
    }

    /**
     * Snatches a sub-level by ID, alongside all of its loading dependencies
     */
    @Nullable
    protected Collection<HoldingSubLevel> snatch(final UUID subLevelId) {
        final HoldingSubLevel holdingSubLevel = this.loadedHoldingSubLevels.remove(subLevelId);

        if (holdingSubLevel == null) {
            return null;
        }

        final SubLevelData data = holdingSubLevel.data();
        final List<UUID> relations = data.dependencies();

        final ObjectList<HoldingSubLevel> snatchedSubLevels = new ObjectArrayList<>();
        snatchedSubLevels.add(holdingSubLevel);

        for (final UUID uuid : relations) {
            final HoldingSubLevel dependencySubLevel = this.loadedHoldingSubLevels.remove(uuid);

            if (dependencySubLevel == null) {
                Sable.LOGGER.error("Sub-level dependency does not exist in chunk when loading force-loaded holding sub-level. Something has gone terribly wrong.");
            } else {
                snatchedSubLevels.add(dependencySubLevel);
            }
        }

        return snatchedSubLevels;
    }

    /**
     * Collects all sub-levels that are completely ready for loading.
     * In this process, these ready holding sub-levels will be removed.
     */
    public void collectReadySubLevels(final ServerLevel level, final Object2ObjectMap<UUID, HoldingSubLevel> readySubLevels) {
        // Don't bother allocating an iterator if there's nothing to collect
        if (this.loadedHoldingSubLevels.isEmpty()) {
            return;
        }

        this.visitedSet.clear();
        final Iterator<Map.Entry<UUID, HoldingSubLevel>> iter = this.loadedHoldingSubLevels.entrySet().iterator();

        checkingLoop: while (iter.hasNext()) {
            final Map.Entry<UUID, HoldingSubLevel> entry = iter.next();
            if (this.visitedSet.contains(entry.getKey())) {
                continue;
            }

            final HoldingSubLevel holdingSubLevel = entry.getValue();
            final SubLevelData data = holdingSubLevel.data();
            final List<UUID> relations = data.dependencies();

            this.visitedSet.add(entry.getKey());
            this.visitedSet.addAll(relations);

            for (final UUID uuid : relations) {
                final HoldingSubLevel dependencySubLevel = this.loadedHoldingSubLevels.get(uuid);

                if (dependencySubLevel == null) {
                    Sable.LOGGER.error("Sub-level dependency does not exist in chunk. Something has gone terribly wrong.");
                    iter.remove();
                    continue checkingLoop;
                }

                if (!canLoadSubLevel(level, dependencySubLevel.data())) {
                    continue checkingLoop;
                }
            }

            final boolean allChunksLoaded = canLoadSubLevel(level, data);

            if (allChunksLoaded) {
                readySubLevels.put(data.uuid(), holdingSubLevel);
                iter.remove();

                for (final UUID uuid : relations) {
                    final HoldingSubLevel dependencySubLevel = this.loadedHoldingSubLevels.get(uuid);
                    if (dependencySubLevel != null) {
                        this.alsoLoad.add(dependencySubLevel);
                    }
                }
            }
        }

        for (final HoldingSubLevel holdingSubLevel : this.alsoLoad) {
            final UUID uuid = holdingSubLevel.data().uuid();
            this.loadedHoldingSubLevels.remove(uuid);
            readySubLevels.put(uuid, holdingSubLevel);
        }
        this.alsoLoad.clear();
    }

    private static boolean canLoadSubLevel(final ServerLevel level, final SubLevelData data) {
        final BoundingBox3dc bounds = data.bounds();

        final BoundingBox3i chunkBounds = new BoundingBox3i(
                Mth.floor(bounds.minX() - 1.0) >> 4,
                Mth.floor(bounds.minY() - 1.0) >> 4,
                Mth.floor(bounds.minZ() - 1.0) >> 4,
                Mth.floor(bounds.maxX() + 1.0) >> 4,
                Mth.floor(bounds.maxY() + 1.0) >> 4,
                Mth.floor(bounds.maxZ() + 1.0) >> 4
        );

        boolean allChunksLoaded = true;
        xLoop:
        for (int x = chunkBounds.minX(); x <= chunkBounds.maxX(); x++) {
            for (int z = chunkBounds.minZ(); z <= chunkBounds.maxZ(); z++) {
                if (!PhysicsChunkTicketManager.isChunkLoadedEnough(level, x, z)) {
                    allChunksLoaded = false;
                    break xLoop;
                }
            }
        }
        return allChunksLoaded;
    }

    public static SubLevelHoldingChunk from(final ChunkPos pos, final CompoundTag tag) {
        final SubLevelHoldingChunk chunk = new SubLevelHoldingChunk(pos);

        final int[] pointer = tag.getIntArray("pointers");
        chunk.pointers.addAll(Arrays.stream(pointer).mapToObj(SavedSubLevelPointer::unpack).toList());

        return chunk;
    }

    public void writeTo(final CompoundTag tag) {
        final int[] pointers = this.pointers.stream().mapToInt(SavedSubLevelPointer::packed).toArray();
        tag.putIntArray("pointers", pointers);
    }

    public ChunkPos getChunkPos() {
        return this.pos;
    }

    public List<SavedSubLevelPointer> getSubLevelPointers() {
        return this.pointers;
    }

    public void markKeepLoaded() {
        this.keepLoaded = true;
    }

    public boolean shouldKeepLoaded() {
        return this.keepLoaded;
    }

    @Override
    public String toString() {
        return "SubLevelHoldingChunk{" +
                "pos=" + this.pos +
                ", keepLoaded=" + this.keepLoaded +
                ", pointers=" + this.pointers +
                '}';
    }
}
