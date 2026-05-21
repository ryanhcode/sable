package dev.ryanhcode.sable.sublevel.plot;

import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelReactionWheel;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.LevelChunkTicks;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3dc;

import java.util.*;

/**
 * An allocated & reserved space in a level belonging to a {@link SubLevel}, holding its own chunk grid.
 */
public abstract class LevelPlot {

    /**
     * The minimum chunk X and Z coordinates of the plot, in units of {@code 1 << logSize} chunks.
     * <p>
     * Ex. a plot with {@code pos = (0, 0)} and a log size of 4 has its chunks in the range {@code [0, 16)}.
     */
    public final ChunkPos plotPos;

    /**
     * The plotgrid containing this plot.
     */
    protected final SubLevelContainer container;

    /**
     * The log_2 of the side length of a plot.
     */
    protected final int logSize;

    /**
     * The chunk storage for this plot.
     */
    private final PlotChunkHolder[] chunks;

    /**
     * The sub-level using this plot.
     */
    private final @NotNull SubLevel subLevel;

    /**
     * All loaded chunkholders in this plot.
     */
    private final List<PlotChunkHolder> loadedChunks = new ObjectArrayList<>();

    /**
     * All block entity actors within this plot
     */
    protected final Object2ObjectOpenHashMap<BlockPos, BlockEntitySubLevelActor> blockEntityActors = new Object2ObjectOpenHashMap<>();

    /**
     * All block entity reaction wheels within this plot
     */
    private final Object2ObjectOpenHashMap<BlockPos, BlockEntitySubLevelReactionWheel> blockEntityReactionWheels = new Object2ObjectOpenHashMap<>();
    /**
     * If the plot should expand and add new chunks when blocks reach the edge of existing chunks.
     */
    protected boolean expandPlotIfNecessary = true;
    /**
     * The local, block-aligned, and inclusive bounding box of this sub-level.
     */
    @Nullable
    protected BoundingBox3i localBounds = null;
    /**
     * The biome for the whole plot
     */
    protected ResourceKey<Biome> biome = Biomes.PLAINS;

    /**
     * Creates a new plot at the given plot coordinate.
     *
     * @param container the parent plot container of this level plot
     * @param x             the global X coordinate of the plot, in units of {@code 1 << logSize} chunks
     * @param z             the global Z coordinate of the plot, in units of {@code 1 << logSize} chunks
     * @param logSize       the log_2 of the side length of a plot
     * @param subLevel      the sub-level using this plot
     */
    public LevelPlot(final SubLevelContainer container, final int x, final int z, final int logSize, final @NotNull SubLevel subLevel) {
        this.container = container;
        this.plotPos = new ChunkPos(x, z);
        this.logSize = logSize;
        this.chunks = new PlotChunkHolder[(1 << logSize) * (1 << logSize)];
        this.subLevel = subLevel;
    }

    /**
     * Ticks this plot, running lighting updates
     */
    public void tick() {

    }

    /**
     * @return a  {@link LevelAccessor} for this plot centered with 0, 0, 0 at {@link LevelPlot#getCenterBlock()}.
     */
    public EmbeddedPlotLevelAccessor getEmbeddedLevelAccessor() {
        return new EmbeddedPlotLevelAccessor(this);
    }

    /**
     * @return the center block position in the plot
     */
    public BlockPos getCenterBlock() {
        // TODO make this the actual center
        final ChunkPos centerChunk = this.getCenterChunk();
        return new BlockPos(centerChunk.getMinBlockX() + 8, 128, centerChunk.getMinBlockZ() + 8);
    }

    /**
     * Adds a chunk in the plotgrid at the given global chunk position.
     *
     * @param pos the global chunk position
     */
    protected void newChunk(final ChunkPos pos, final LevelChunk chunk, final boolean initializeLighting) {
        final ChunkPos local = this.toLocal(pos);

        if (this.getChunkHolder(local) != null) {
            throw new IllegalStateException("Chunk already exists at %s".formatted(pos));
        }

        final PlotChunkHolder holder = PlotChunkHolder.create(chunk.getLevel(), pos, this.getLightEngine(), chunk);
        this.addChunkHolder(local, holder, initializeLighting);
    }

    /**
     * Returns the lighting engine this sub-level should use.
     * This is done due to {@link dev.ryanhcode.sable.sublevel.ServerSubLevel ServerSubLevels} having their own lighting engine,
     * @return the lighting engine for this plot, or null if not set
     */
    public abstract LevelLightEngine getLightEngine();

    /**
     * Adds a new, empty chunk at the given global chunk position, and initializes lighting for it.
     */
    public void newEmptyChunk(final ChunkPos pos) {
        final Level level = this.container.getLevel();

        final int sectionCount = level.getSectionsCount();
        final LevelChunkSection[] sections = new LevelChunkSection[sectionCount];

        for (int i = 0; i < sectionCount; ++i) {
            final Registry<Biome> biomeRegistry = level.registryAccess().registryOrThrow(Registries.BIOME);
            final PalettedContainer<BlockState> states = new PalettedContainer(Block.BLOCK_STATE_REGISTRY, Blocks.AIR.defaultBlockState(), PalettedContainer.Strategy.SECTION_STATES);
            final PalettedContainer<Holder<Biome>> biomes = new PalettedContainer(biomeRegistry.asHolderIdMap(), biomeRegistry.getHolderOrThrow(this.biome), PalettedContainer.Strategy.SECTION_BIOMES);

            sections[i] = new LevelChunkSection(states, biomes);
        }

        final LevelChunk chunk = new LevelChunk(level, pos, UpgradeData.EMPTY, new LevelChunkTicks<>(), new LevelChunkTicks<>(), 0L, sections, null, null);
        this.newChunk(pos, chunk, true);
    }


    /**
     * @return the sub-level using this plot.
     */
    public SubLevel getSubLevel() {
        return this.subLevel;
    }

    /**
     * @return if a point is inside the plot.
     */
    public boolean contains(final double x, final double z) {
        final int logBlockSize = this.logSize + SectionPos.SECTION_BITS;
        return x >= this.plotPos.x << logBlockSize  && x < (this.plotPos.x + 1) << logBlockSize
                && z >= this.plotPos.z << logBlockSize  && z < (this.plotPos.z + 1) << logBlockSize;
    }

    /**
     * @return if a vector is inside the plot.
     */
    public boolean contains(final Vec3 point) {
        return this.contains(point.x(), point.z());
    }

    /**
     * @return if a vector is inside the plot.
     */
    public boolean contains(final Vector3dc point) {
        return this.contains(point.x(), point.z());
    }

    /**
     * @return the minimum chunk position of the plot.
     */
    public ChunkPos getChunkMin() {
        return new ChunkPos(this.plotPos.x << this.logSize, this.plotPos.z << this.logSize);
    }

    /**
     * @return the maximum chunk position of the plot.
     */
    public ChunkPos getChunkMax() {
        return new ChunkPos(((this.plotPos.x + 1) << this.logSize) - 1, ((this.plotPos.z + 1) << this.logSize) - 1);
    }

    /**
     * @return if the given chunk is within this plot.
     */
    public boolean contains(final ChunkPos chunk) {
        return chunk.x >> this.logSize == this.plotPos.x && chunk.z >> this.logSize == this.plotPos.z;
    }

    /**
     * @return the local chunk position inside the plot for a global chunk position
     */
    public ChunkPos toLocal(final ChunkPos global) {
        return new ChunkPos(global.x - (this.plotPos.x << this.logSize), global.z - (this.plotPos.z << this.logSize));
    }

    /**
     * @return the global chunk position for a local chunk position inside the plot
     */
    public ChunkPos toGlobal(final ChunkPos local) {
        return new ChunkPos(local.x + (this.plotPos.x << this.logSize), local.z + (this.plotPos.z << this.logSize));
    }

    /**
     * @return the chunk holder at the local position in the plot
     */
    public @Nullable PlotChunkHolder getChunkHolder(final ChunkPos local) {
        if (local.x < 0 || local.x >= 1 << this.logSize || local.z < 0 || local.z >= 1 << this.logSize) {
            return null;
        }

        return this.chunks[local.z << this.logSize | local.x];
    }

    /**
     * Sets a chunk at the local position in the plot
     *
     * @param localChunkPos      the local chunk position in the plot
     * @param holder             the chunk holder to set
     * @param initializeLighting if true, initializes lighting for the chunk
     */
    @ApiStatus.Internal
    public void addChunkHolder(final ChunkPos localChunkPos, final PlotChunkHolder holder, final boolean initializeLighting) {
        if (holder == null) {
            throw new IllegalArgumentException("Chunk cannot be null");
        }

        this.loadedChunks.add(holder);
        this.chunks[localChunkPos.z << this.logSize | localChunkPos.x] = holder;

        this.updateBoundingBox();
    }

    /**
     * @return the chunk at the local position in the plot
     */
    public LevelChunk getChunk(final ChunkPos local) {
        final PlotChunkHolder holder = this.getChunkHolder(local);
        return holder == null ? null : holder.getChunk();
    }

    /**
     * @return the chunk at the global position in the center of the plot
     */
    public ChunkPos getCenterChunk() {
        return new ChunkPos((this.plotPos.x << this.logSize) + (1 << (this.logSize - 1)), (this.plotPos.z << this.logSize) + (1 << (this.logSize - 1)));
    }

    public Collection<PlotChunkHolder> getLoadedChunks() {
        return this.loadedChunks;
    }

    /**
     * Updates & rebuilds the block bounding box of this plot.
     */
    public void updateBoundingBox() {
        if (this.subLevel.getLevel().isClientSide) {
            return;
        }

        final BoundingBox3i previousBounds = this.localBounds;
        this.localBounds = null;

        final BoundingBox3i temp = new BoundingBox3i(0, 0, 0, 0, 0, 0);
        for (final PlotChunkHolder chunk : this.loadedChunks) {
            final ChunkPos pos = chunk.getPos();

            final BoundingBox3ic chunkLocalBounds = chunk.getBoundingBox();

            if (chunkLocalBounds == null) {
                continue;
            }

            final BoundingBox3i chunkBounds = chunkLocalBounds.move(pos.getMinBlockX(), 0, pos.getMinBlockZ(), temp);

            if (chunkBounds != null) {
                if (this.localBounds == null) {
                    this.localBounds = new BoundingBox3i(chunkBounds);
                } else {
                    this.localBounds = this.localBounds.expandTo(chunkBounds, this.localBounds);
                }
            }
        }

        if (!Objects.equals(previousBounds, this.localBounds)) {
            this.subLevel.onPlotBoundsChanged();
        }
    }

    /**
     * @return the block-aligned, and inclusive bounding box of sub-level, or the empty box if not set.
     */
    public BoundingBox3ic getBoundingBox() {
        return this.localBounds != null ? this.localBounds : BoundingBox3i.EMPTY;
    }

    /**
     * Sets the local bounding box.
     *
     * @param bounds the new bounding box
     */
    public void setBoundingBox(final BoundingBox3ic bounds) {
        if (this.localBounds == null) {
            this.localBounds = new BoundingBox3i(bounds);
        } else {
            this.localBounds.set(bounds);
        }
    }

    /**
     * Removes all chunks from this plot.
     */
    public void onRemove() {
        for (final PlotChunkHolder chunk : this.loadedChunks) {
            final LevelChunk levelChunk = chunk.getChunk();
            assert levelChunk != null;

            // TODO: neo & fabric chunk unload events
            levelChunk.setLoaded(false);

            this.onRemoveChunkHolder(levelChunk);
        }

        this.loadedChunks.clear();
        this.localBounds = null;
    }

    protected abstract void onRemoveChunkHolder(final LevelChunk levelChunk);

    /**
     * Expands the plot if necessary to include the given block and adjacent neighbors
     */
    public void expandIfNecessary(final BlockPos blockPos) {
        if (!this.expandPlotIfNecessary) {
            return;
        }

        for (final Direction direction : Direction.values()) {
            // One block of margin to prevent black face lighting at the edges of chunks
            final BlockPos offsetPos = blockPos.relative(direction, 2);

            final ChunkPos globalChunk = new ChunkPos(offsetPos);

            if (this.getChunk(this.toLocal(globalChunk)) == null) {
                // Add the chunk if it's missing
                this.newEmptyChunk(globalChunk);
            }
        }
    }


    /**
     * Handles a change in block-state in the plot at global block position x, y, z.
     *
     * @param state the new block-state
     */
    public void onBlockChange(final BlockPos pos, final BlockState state) {
        final Level level = this.subLevel.getLevel();

        final BlockEntity blockEntity = level.getBlockEntity(pos);
        final BlockEntitySubLevelActor actor = blockEntity instanceof BlockEntitySubLevelActor ? (BlockEntitySubLevelActor) blockEntity : null;

        if (actor != null) {
            this.blockEntityActors.put(pos, actor);
        } else {
            this.blockEntityActors.remove(pos);
        }

        if (blockEntity instanceof final BlockEntitySubLevelReactionWheel reactionWheel) {
            this.blockEntityReactionWheels.put(pos, reactionWheel);

            if (this.subLevel instanceof final ServerSubLevel serverSubLevel)
                serverSubLevel.getReactionWheelManager().wheelChanged(pos, reactionWheel, true);
        } else {
            final BlockEntitySubLevelReactionWheel reactionWheel = this.blockEntityReactionWheels.remove(pos);

            if (reactionWheel != null && this.subLevel instanceof final ServerSubLevel serverSubLevel)
                serverSubLevel.getReactionWheelManager().wheelChanged(pos, reactionWheel, false);
        }
    }

    /**
     * Gets all active actors in the plot
     */
    public Iterable<BlockEntitySubLevelActor> getBlockEntityActors() {
        return this.blockEntityActors.values();
    }

    public Collection<BlockEntitySubLevelReactionWheel> getBlockEntityReactionWheels() {
        return this.blockEntityReactionWheels.values();
    }

    public Set<Map.Entry<BlockPos, BlockEntitySubLevelReactionWheel>> getBlockEntityReactionWheelMap() {
        return this.blockEntityReactionWheels.entrySet();
    }
}
