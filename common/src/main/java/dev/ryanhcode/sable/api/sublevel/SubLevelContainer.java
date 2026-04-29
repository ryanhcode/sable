package dev.ryanhcode.sable.api.sublevel;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.mixinterface.plot.SubLevelContainerHolder;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import dev.ryanhcode.sable.sublevel.storage.SubLevelOccupancySavedData;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.util.iterator.ListBackedFilterIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2i;

import java.util.*;

/**
 * Holds all sub-levels and plots in a {@link Level}
 */
public abstract class SubLevelContainer {

    public static int DEFAULT_LOG_SIZE_LENGTH = 7;
    public static int DEFAULT_LOG_PLOT_SIZE = 7;

    /**
     * The origin of the plotyard in plots.
     * We want the plotyard to be over 30 million blocks out.
     */
    public static final int DEFAULT_ORIGIN = 10000;//Mth.ceil(30_000_000.0 / (1 << DEFAULT_LOG_PLOT_SIZE));
    /**
     * The plotgrid storage for all loaded sub-levels
     */
    protected final SubLevel[] subLevels;
    /**
     * All of the loaded sub-levels in the plotgrid
     */
    private final List<SubLevel> allSubLevels = new ObjectArrayList<>();
    /**
     * All of the loaded sub-levels in the plotgrid, by uuid
     */
    private final Map<UUID, SubLevel> subLevelsByUUID = new HashMap<>();
    /**
     * The occupancy of the plotgrid, including loaded and unloaded plots
     */
    private final BitSet occupancy;
    /**
     * All observers/listeners for the plotgrid
     */
    private final List<SubLevelObserver> observers = new ObjectArrayList<>();

    /**
     * A per-tick cache of every chunk position that is occupied by at least one sub-level.
     * Rebuilt from scratch each tick in {@link #tick()} so that moving sub-levels
     * are tracked correctly. Used by {@link #isChunkOccupied(int, int)} for O(1) lookups.
     *
     * @see #rebuildOccupiedChunksCache()
     * @see #isChunkOccupied(int, int)
     */
    private final LongSet occupiedChunks = new LongOpenHashSet();

    /**
     * The level of the plotgrid
     */
    private final Level level;
    /**
     * The log_2 of the side length of the plotgrid in plots
     */
    private final int logSideLength;
    /**
     * The log_2 of the amount of chunks in the side of a plot
     */
    private final int logPlotSize;

    /**
     * The X origin of the plotgrid in plot coordinates
     */
    private final int originX;
    /**
     * The Z origin of the plotgrid in plot coordinates
     */
    private final int originZ;

    /**
     * @param level the level
     * @return the plot container in a level
     */
    public static @Nullable SubLevelContainer getContainer(final Level level) {
        if (level instanceof final SubLevelContainerHolder holder) {
            return holder.sable$getPlotContainer();
        }
        return null;
    }

    /**
     * @param level the level
     * @return the plot container in a level
     */
    public static @Nullable ServerSubLevelContainer getContainer(final ServerLevel level) {
        if (level instanceof final SubLevelContainerHolder holder) {
            return (ServerSubLevelContainer) holder.sable$getPlotContainer();
        }
        return null;
    }

    /**
     * @param level the level
     * @return the plot container in a level
     */
    public static @Nullable ClientSubLevelContainer getContainer(final ClientLevel level) {
        if (level instanceof final SubLevelContainerHolder holder) {
            return (ClientSubLevelContainer) holder.sable$getPlotContainer();
        }
        return null;
    }

    /**
     * Creates a new sub-level container with the given side length and plot size.
     *
     * @param level         the level of the plotgrid
     * @param logSideLength the log_2 of the amount of chunks in the side of the plotgrid
     * @param logPlotSize   the log_2 of the amount of chunks in the side of a plot
     * @param originX       the X coordinate in plots of the origin of the plotgrid
     * @param originZ       the Z coordinate in plots of the origin of the plotgrid
     */
    public SubLevelContainer(final Level level, final int logSideLength, final int logPlotSize, final int originX, final int originZ) {
        this.level = level;
        this.logSideLength = logSideLength;
        this.logPlotSize = logPlotSize;
        this.originX = originX;
        this.originZ = originZ;
        this.subLevels = new SubLevel[(1 << logSideLength) * (1 << logSideLength)];
        this.occupancy = new BitSet(this.subLevels.length);
    }

    /**
     * Called every tick for the plotgrid.
     */
    public void tick() {
        this.allSubLevels.forEach(SubLevel::tick);
        this.processSubLevelRemovals();

        this.observers.forEach(observer -> observer.tick(this));

        // Rebuild the occupied-chunk cache so that entity-getter fast-paths
        // always see up-to-date positions (sub-levels can move every tick).
        this.rebuildOccupiedChunksCache();
    }

    /**
     * Processes & follows through on queued sub-level removals
     */
    public void processSubLevelRemovals() {
        for (final SubLevel subLevel : this.allSubLevels) {
            if (subLevel instanceof final ServerSubLevel serverSubLevel) {
                if (!serverSubLevel.isRemoved() && serverSubLevel.getMassTracker().isInvalid()) {
                    serverSubLevel.getPlot().destroyAllBlocks();
                    serverSubLevel.markRemoved();
                }
            }

            if (subLevel.isRemoved()) {
                final LevelPlot plot = subLevel.getPlot();
                final ChunkPos plotPos = plot.plotPos;
                this.removeSubLevel(plotPos.x - this.originX, plotPos.z - this.originZ, SubLevelRemovalReason.REMOVED);
            }
        }
    }

    /**
     * Adds an observer to the plotgrid.
     */
    public void addObserver(final SubLevelObserver observer) {
        this.observers.add(observer);
    }

    /**
     * @return the first empty plot coordinate in the grid, using occupancy data
     */
    private Vector2i getFirstEmptyPlot() {
        for (int x = 0; x < (1 << this.logSideLength); x++) {
            for (int z = 0; z < (1 << this.logSideLength); z++) {
                if (!this.occupancy.get(this.getIndex(x, z))) {
                    return new Vector2i(x, z);
                }
            }
        }
        return null;
    }

    /**
     * @return the index of the plot at the given plot coordinates.
     */
    @ApiStatus.Internal
    public int getIndex(final int x, final int z) {
        return x + (z << this.logSideLength);
    }

    /**
     * @return the plot at the given local plot coordinates.
     */
    private @Nullable LevelPlot getLocalPlot(final int x, final int z) {
        if (x < 0 || x >= (1 << this.logSideLength) || z < 0 || z >= (1 << this.logSideLength)) {
            return null; // out of bounds
        }

        final SubLevel subLevel = this.subLevels[this.getIndex(x, z)];

        if (subLevel == null) {
            return null;
        }

        return subLevel.getPlot();
    }

    /**
     * @return the sub-level at the given local plot coordinates.
     */
    public @Nullable SubLevel getSubLevel(final int x, final int z) {
        if (x < 0 || x >= (1 << this.logSideLength) || z < 0 || z >= (1 << this.logSideLength)) {
            return null; // out of bounds
        }

        return this.subLevels[this.getIndex(x, z)];
    }

    /**
     * Allocates a new plot at the given local plot coordinates.
     *
     * @return the allocated plot
     */
    public SubLevel allocateNewSubLevel(final Pose3d pose) {
        final Vector2i firstEmptyPlot = this.getFirstEmptyPlot();

        if (firstEmptyPlot == null) {
            throw new IllegalStateException("No empty plots left in the plotgrid");
        }

        return this.allocateSubLevel(UUID.randomUUID(), firstEmptyPlot.x, firstEmptyPlot.y, pose);
    }

    /**
     * Allocates a new plot at the given local plot coordinates.
     *
     * @return the allocated plot
     */
    public SubLevel allocateSubLevel(final UUID uuid, final int x, final int z, final Pose3d pose) {
        if (this.getLocalPlot(x, z) != null) {
            throw new IllegalArgumentException("Plot already exists at " + x + ", " + z);
        }

        if (x < 0 || x >= (1 << this.logSideLength) || z < 0 || z >= (1 << this.logSideLength)) {
            throw new IllegalArgumentException("Plot coordinates out of bounds: " + x + ", " + z);
        }

        final SubLevel subLevel;

        // Create a new sub-level based on the level type
        subLevel = this.createSubLevel(x + this.originX, z + this.originZ, pose, uuid);

        final int index = this.getIndex(x, z);
        this.subLevels[index] = subLevel;
        this.getOccupancy().set(index);
        this.allSubLevels.add(subLevel);
        this.subLevelsByUUID.put(subLevel.getUniqueId(), subLevel);
        this.observers.forEach(observer -> observer.onSubLevelAdded(subLevel));

        if (this.level instanceof final ServerLevel serverLevel) {
            SubLevelOccupancySavedData.getOrLoad(serverLevel).setDirty();
        }

        return subLevel;
    }

    /**
     * Creates a new sub-level with the given global plot coordinates and pose.
     *
     * @param globalPlotX the global plot X coordinate
     * @param globalPlotZ the global plot Z coordinate
     * @param pose        the initialization pose of the sub-level
     * @param uuid        the unique ID of the sub-level
     * @return a new {@link SubLevel} instance
     */
    protected abstract SubLevel createSubLevel(int globalPlotX, int globalPlotZ, Pose3d pose, UUID uuid);

    /**
     * Gets a chunk from the plotgrid.
     *
     * @param pos the global chunk position
     */
    public @Nullable LevelChunk getChunk(final ChunkPos pos) {
        if (!this.inBounds(pos)) {
            return null;
        }

        final LevelPlot plot = this.getPlot(pos);
        if (plot == null) {
            return null;
        }

        final ChunkPos local = plot.toLocal(pos);
        return plot.getChunk(local);
    }

    /**
     * Gets a chunk holder from the plotgrid.
     *
     * @param pos the global chunk position
     */
    public @Nullable PlotChunkHolder getChunkHolder(final ChunkPos pos) {
        if (!this.inBounds(pos)) {
            return null;
        }

        final LevelPlot plot = this.getPlot(pos);
        if (plot == null) {
            return null;
        }

        final ChunkPos local = plot.toLocal(pos);
        return plot.getChunkHolder(local);
    }

    /**
     * Gets the plot at the given global chunk position.
     *
     * @param chunkX the global chunk X position
     * @param chunkZ the global chunk Z position
     */
    public @Nullable LevelPlot getPlot(final int chunkX, final int chunkZ) {
        final int plotX = (chunkX >> this.logPlotSize) - this.originX;
        final int plotZ = (chunkZ >> this.logPlotSize) - this.originZ;

        return this.getLocalPlot(plotX, plotZ);
    }

    /**
     * Gets the plot at the given global chunk position.
     *
     * @param pos the global chunk position
     */
    public @Nullable LevelPlot getPlot(final ChunkPos pos) {
        final int plotX = (pos.x >> this.logPlotSize) - this.originX;
        final int plotZ = (pos.z >> this.logPlotSize) - this.originZ;

        return this.getLocalPlot(plotX, plotZ);
    }

    /**
     * @return if a global chunk position is within the plotgrid.
     */
    public boolean inBounds(final ChunkPos pos) {
        return this.inBounds(pos.x, pos.z);
    }

    /**
     * @return if a global block position is within the plotgrid.
     */
    public boolean inBounds(final BlockPos pos) {
        return this.inBounds(pos.getX() >> SectionPos.SECTION_BITS, pos.getZ() >> SectionPos.SECTION_BITS);
    }

    /**
     * @return if a global chunk position is within the plotgrid.
     */
    public boolean inBounds(final int x, final int z) {
        final int plotX = (x >> this.logPlotSize) - this.originX;
        final int plotZ = (z >> this.logPlotSize) - this.originZ;

        final int sideLength = 1 << this.logSideLength;
        return (plotX >= 0 && plotX < sideLength && plotZ >= 0 && plotZ < sideLength);
    }

    /**
     * Adds a populated chunk in the plotgrid at the given global chunk position.
     *
     * @param pos the global chunk position
     */
    public void newPopulatedChunk(final ChunkPos pos, final LevelChunk chunk) {
        if (!this.inBounds(pos)) {
            return;
        }

        final int plotX = (pos.x >> this.logPlotSize) - this.originX;
        final int plotZ = (pos.z >> this.logPlotSize) - this.originZ;

        final LevelPlot plot = this.getLocalPlot(plotX, plotZ);

        if (plot == null) {
            Sable.LOGGER.error("Cannot add chunk at {}, {} in nonexistent sub-level plot", plotX, plotZ);
            return;
        }

        final ChunkPos local = plot.toLocal(pos);

        if (plot.getChunkHolder(local) != null) {
            throw new IllegalStateException("Chunk already exists at " + pos);
        }

        final PlotChunkHolder holder = PlotChunkHolder.create(chunk.getLevel(), pos, plot.getLightEngine(), chunk);

        plot.addChunkHolder(local, holder, false);
    }

    /**
     * Gets the players tracking a plot chunk.
     *
     * @return the players tracking the chunk
     */
    public List<ServerPlayer> getPlayersTracking(final ChunkPos chunkPos) {
        final LevelPlot plot = this.getPlot(chunkPos);
        if (plot == null) {
            return List.of();
        }

        final SubLevel subLevel = plot.getSubLevel();

        if (subLevel instanceof final ServerSubLevel serverSubLevel) {
            final Collection<UUID> trackingPlayers = serverSubLevel.getTrackingPlayers();
            final ObjectList<ServerPlayer> players = new ObjectArrayList<>(trackingPlayers.size());

            for (final UUID uuid : serverSubLevel.getTrackingPlayers()) {
                final ServerPlayer player = this.level.getServer().getPlayerList().getPlayer(uuid);

                if (player != null) {
                    players.add(player);
                }
            }

            return players;
        }

        return List.of();
    }

    /**
     * @return all of the plots in the plotgrid.
     */
    public List<? extends SubLevel> getAllSubLevels() {
        return this.allSubLevels;
    }

    /**
     * @return the level of the plotgrid.
     */
    public Level getLevel() {
        return this.level;
    }

    /**
     * @return the log_2 of the side length of a plot
     */
    public int getLogPlotSize() {
        return this.logPlotSize;
    }

    /**
     * @return the log_2 of the side length of the plotgrid
     */
    public int getLogSideLength() {
        return this.logSideLength;
    }

    /**
     * @return the origin of the plotgrid in plot coordinates
     */
    public Vector2i getOrigin() {
        return new Vector2i(this.originX, this.originZ);
    }


    /**
     * Removes a sub-level with a local plot coordinate
     */
    public void removeSubLevel(final int x, final int z, final SubLevelRemovalReason reason) {
        final SubLevel subLevel = this.getSubLevel(x, z);
        if (subLevel == null) {
            throw new IllegalStateException("No sub-level at " + x + ", " + z);
        }

        this.observers.forEach(observer -> observer.onSubLevelRemoved(subLevel, reason));
        subLevel.onRemove();

        final int index = this.getIndex(x, z);
        this.subLevels[index] = null;
        this.allSubLevels.remove(subLevel);
        this.subLevelsByUUID.remove(subLevel.getUniqueId());

        if (reason == SubLevelRemovalReason.REMOVED) {
            this.getOccupancy().clear(index);
        }
    }

    /**
     * @return the count of loaded sub-levels
     */
    public int getLoadedCount() {
        return this.allSubLevels.size();
    }

    public Iterable<SubLevel> queryIntersecting(final BoundingBox3dc bounds) {
        return () -> new ListBackedFilterIterator<>((subLevel) -> subLevel.boundingBox().intersects(bounds), this.allSubLevels);
    }

    /**
     * Removes a sub-level from the plotgrid.
     *
     * @param subLevel the sub-level to remove
     * @param reason   the reason for removal
     */
    public void removeSubLevel(final SubLevel subLevel, final SubLevelRemovalReason reason) {
        final int x = subLevel.getPlot().plotPos.x - this.originX;
        final int z = subLevel.getPlot().plotPos.z - this.originZ;
        this.removeSubLevel(x, z, reason);
    }

    /**
     * Retrieves a particular sub-level by its UUID.
     *
     * @param uuid the UUID of the sub-level
     */
    public @Nullable SubLevel getSubLevel(final UUID uuid) {
        return this.subLevelsByUUID.get(uuid);
    }

    /**
     * The occupancy of the plotgrid, including loaded and unloaded plots
     */
    @ApiStatus.Internal
    public BitSet getOccupancy() {
        return this.occupancy;
    }

    // ─────────────────────────────────────────────────────
    //  Occupied-chunk cache (fast entity-getter path)
    // ─────────────────────────────────────────────────────

    /**
     * Rebuilds the occupied-chunk cache from scratch.
     * Called every tick so that moving/rotating sub-levels are tracked correctly.
     *
     * <p>The cache is a simple {@link LongSet} of chunk-position-encoded longs
     * ({@link ChunkPos#asLong(int, int)}).  It is consumed by
     * {@link #isChunkOccupied(int, int)} which runs in O(1) time.</p>
     */
    private void rebuildOccupiedChunksCache() {
        this.occupiedChunks.clear();
        for (final SubLevel sub : this.allSubLevels) {
            final LevelPlot plot = sub.getPlot();
            final ChunkPos min = plot.getChunkMin();
            final ChunkPos max = plot.getChunkMax();
            for (int cx = min.x; cx <= max.x; cx++) {
                for (int cz = min.z; cz <= max.z; cz++) {
                    this.occupiedChunks.add(ChunkPos.asLong(cx, cz));
                }
            }
        }
    }

    /**
     * Checks whether any loaded sub-level occupies the given chunk position.
     * <p>This is backed by the per-tick cache and completes in O(1) time.</p>
     *
     * @param chunkX the global chunk X coordinate
     * @param chunkZ the global chunk Z coordinate
     * @return {@code true} if at least one sub-level occupies this chunk
     */
    public boolean isChunkOccupied(final int chunkX, final int chunkZ) {
        return this.occupiedChunks.contains(ChunkPos.asLong(chunkX, chunkZ));
    }
}
