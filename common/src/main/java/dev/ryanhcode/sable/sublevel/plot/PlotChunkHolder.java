package dev.ryanhcode.sable.sublevel.plot;

import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * A chunk holder for chunks that live inside a {@link LevelPlot}.
 */
public class PlotChunkHolder extends ChunkHolder {
    private final LevelChunk chunk;
    private final HeatDataChunkSection[] heatSections;

    private @Nullable BoundingBox3i boundingBox;

    /**
     * Creates a new plot chunk holder with a level & chunk position.
     * Builds a bounding box for the chunk if non-empty.
     */
    public PlotChunkHolder(final LevelChunk chunk, final ChunkPos pos, final LevelHeightAccessor levelHeightAccessor, final LevelLightEngine levelLightEngine, final LevelChangeListener levelChangeListener, final PlayerProvider playerProvider) {
        super(pos, ChunkLevel.ENTITY_TICKING_LEVEL, levelHeightAccessor, levelLightEngine, levelChangeListener, playerProvider);

        if (chunk == null) {
            throw Util.pauseInIde(new IllegalStateException("Chunk not found in plot container"));
        }

        this.chunk = chunk;
        this.heatSections = new HeatDataChunkSection[chunk.getSectionsCount()];
        this.tickingChunkFuture = CompletableFuture.completedFuture(ChunkResult.of(chunk));
        this.entityTickingChunkFuture = CompletableFuture.completedFuture(ChunkResult.of(chunk));
        this.fullChunkFuture = CompletableFuture.completedFuture(ChunkResult.of(chunk));

        if (!this.chunk.isEmpty()) {
            this.buildBoundingBox();
        }
    }

    /**
     * Creates a new plot chunk holder with a level & chunk position.
     */
    public static PlotChunkHolder create(final Level level, final ChunkPos pos, final LevelLightEngine lightEngine, final LevelChunk chunk) {
        ChunkMap chunkMap = null;

        if (level.getChunkSource() instanceof final ServerChunkCache chunkCache) {
            chunkMap = chunkCache.chunkMap;
        }

        return new PlotChunkHolder(chunk, pos, level, lightEngine, null, chunkMap);
    }

    /**
     * Builds the bounding box of this chunk holder.
     * TODO: avoid bulk scans like this
     */
    protected void buildBoundingBox() {
        this.boundingBox = null;

        final LevelChunkSection[] sections = this.chunk.getSections();
        for (int i = 0; i < sections.length; i++) {
            final LevelChunkSection section = sections[i];
            final int sectionMinY = this.chunk.getSectionYFromSectionIndex(i) << 4;

            if (section != null && !section.hasOnlyAir()) {
                // the section has blocks. lets find them
                for (int x = 0; x < SectionPos.SECTION_SIZE; x++) {
                    for (int y = 0; y < SectionPos.SECTION_SIZE; y++) {
                        for (int z = 0; z < SectionPos.SECTION_SIZE; z++) {
                            if (!section.getBlockState(x, y, z).isAir()) {
                                if (this.boundingBox == null) {
                                    this.boundingBox = new BoundingBox3i(x, y + sectionMinY, z, x, y + sectionMinY, z);
                                } else {
                                    this.boundingBox = this.boundingBox.expandTo(x, y + sectionMinY, z, this.boundingBox);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Handles a block change to update bounding box and collision data
     */
    public void handleBlockChange(final int x, final int y, final int z, final BlockState oldState, final BlockState newState) {
        if (this.chunk.getLevel().isClientSide) return;
        if (oldState.isAir() && !newState.isAir()) {
            // block placed, expand or create bounding box
            if (this.boundingBox == null) {
                this.boundingBox = new BoundingBox3i(x, y, z, x, y, z);
            } else {
                this.boundingBox = this.boundingBox.expandTo(x, y, z, this.boundingBox);
            }
        } else if (!oldState.isAir() && newState.isAir()) {
            // block removed, shrink or remove bounding box
            if (this.boundingBox != null) {
                if (
                        this.boundingBox.minX == x ||
                                this.boundingBox.maxX == x ||
                                this.boundingBox.minY == y ||
                                this.boundingBox.maxY == y ||
                                this.boundingBox.minZ == z ||
                                this.boundingBox.maxZ == z
                ) {
                    // TODO: do a more optimized contraction
                    this.buildBoundingBox();
                }
            }
        }
    }

    @Override
    public void blockChanged(final BlockPos blockPos) {
        super.blockChanged(blockPos);
    }

    /**
     * We don't want normal future handling, so we let ourselves handle updating the futures.
     */
    @Override
    protected void updateFutures(final ChunkMap chunkMap, final Executor executor) {

    }

    @Override
    public boolean isReadyForSaving() {
        return false;
    }

    public LevelChunk getChunk() {
        return this.chunk;
    }

    /**
     * @return the bounding box of this chunk holder
     */
    public BoundingBox3ic getBoundingBox() {
        return this.boundingBox;
    }

    @Override
    public void rescheduleChunkTask(final ChunkMap chunkMap, @Nullable final ChunkStatus chunkStatus) {
        // no-op, don't make generation tasks
    }

    /**
     * Disables saving of this chunk through the normal chunk map.
     */
    @Override
    public boolean wasAccessibleSinceLastSave() {
        return false;
    }

    @Override
    public @NotNull LevelChunk getTickingChunk() {
        return this.chunk;
    }

    /**
     * @param y the section Y
     * @return the heat section at that section Y
     */
    public @Nullable HeatDataChunkSection getHeatSection(final int y) {
        final int index = y - this.chunk.getMinSection();

        if (index < 0 || index >= this.heatSections.length) {
            return null;
        }

        return this.heatSections[index];
    }

    /**
     * @param y the section Y
     * @param section the heat section to set
     */
    public void setHeatSection(final int y, final HeatDataChunkSection section) {
        final int index = y - this.chunk.getMinSection();

        if (index < 0 || index >= this.heatSections.length) {
            return;
        }

        this.heatSections[index] = section;
    }
}
