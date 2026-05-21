package dev.ryanhcode.sable.sublevel.plot.heat;

import dev.ryanhcode.sable.SableConfig;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.HeatDataChunkSection;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Manages the heatmap and flood-fill for sub-level splitting
 */
public class SubLevelHeatMapManager {

    private static final Collection<SplitListener> LISTENERS = new ObjectArraySet<>();

    /**
     * All directions to check for solid blocks in 3D (including diagonals)
     */
    private static final BlockPos[] DIRECTION_OFFSETS = new BlockPos[] {
            new BlockPos(1, 0, 0),
            new BlockPos(-1, 0, 0),
            new BlockPos(0, 1, 0),
            new BlockPos(0, -1, 0),
            new BlockPos(0, 0, 1),
            new BlockPos(0, 0, -1),
            new BlockPos(1, 1, 0),
            new BlockPos(-1, -1, 0),
            new BlockPos(1, -1, 0),
            new BlockPos(-1, 1, 0),
            new BlockPos(1, 0, 1),
            new BlockPos(-1, 0, -1),
            new BlockPos(1, 0, -1),
            new BlockPos(-1, 0, 1),
            new BlockPos(0, 1, 1),
            new BlockPos(0, -1, -1),
            new BlockPos(0, -1, 1),
            new BlockPos(0, 1, -1)
    };

    @NotNull
    private final ServerSubLevel subLevel;

    /**
     * {@link BlockPos} to partial sub-level indices
     */
    private final Long2IntOpenHashMap subLevelSplits = new Long2IntOpenHashMap();//
    private final ObjectList<BlockPos> floodfill = new ObjectArrayList<>();
    private final ObjectList<BlockPos> removed = new ObjectArrayList<>();
    private final ObjectList<BlockPos> newStarts = new ObjectArrayList<>();
    private final IntArrayList splitIndexMap = new IntArrayList(); // from partial indices to the actual indices
    private HeatMapPropagationState state = HeatMapPropagationState.FILLING;
    private boolean initialized = false; //set to true on the first placed block
    private boolean splitComplete = false; //set to true on first completed flood-fill
    private int solidCount = 0;

    public SubLevelHeatMapManager(@NotNull final ServerSubLevel subLevel) {
        this.subLevel = subLevel;
    }

    /**
     * Ticks the heatmap manager, performing {@link SableConfig#SUB_LEVEL_SPLITTING_HEATMAP_STEPS_PER_TICK} steps
     */
    public void tick() {
        final int steps = SableConfig.SUB_LEVEL_SPLITTING_HEATMAP_STEPS_PER_TICK.getAsInt();
        for (int i = 0; i < steps; i++) {
            if (this.step()) break;
        }
    }

    /**
     * @return true if nothing left to do
     */
    private boolean step() {
        if (this.state == HeatMapPropagationState.FILLING) {
            if (!this.floodfill.isEmpty()) {
                final BlockPos p = new BlockPos(this.floodfill.getFirst());
                this.floodfill.removeFirst();
                if (this.heatMapContains(p)) {
                    final int currentHeat = this.heatMapGet(p);
                    for (final BlockPos dir : DIRECTION_OFFSETS) {
                        final BlockPos p2 = p.offset(dir);
                        final boolean solid = this.isSolidAt(p2);
                        final boolean contains = this.heatMapContains(p2);

                        if (solid && !contains) {
                            this.heatMapSet(p2, (short) (currentHeat + 1));
                            this.subLevelSplits.remove(p2.asLong());
                            this.floodfill.add(p2);
                        }
                    }
                }
            }
            if (this.floodfill.isEmpty()) {
                this.splitComplete = true;
                this.state = HeatMapPropagationState.CLEARING;

                // Split off separated regions!
                if (!this.subLevelSplits.isEmpty())
                    this.split();
            }
        }
        if (this.state == HeatMapPropagationState.CLEARING) {
            if (!this.floodfill.isEmpty()) {
                final BlockPos p = new BlockPos(this.floodfill.getFirst());
                this.floodfill.removeFirst();
                if (this.heatMapContains(p)) {
                    final int currentHeat = this.heatMapGet(p);
                    final int currentIndex = this.splitIndexMap.getInt(this.subLevelSplits.get(p.asLong()));
                    for (final BlockPos dir : DIRECTION_OFFSETS) {
                        final BlockPos p2 = p.offset(dir);
                        if (this.isSolidAt(p2)) {
                            if (this.subLevelSplits.containsKey(p2.asLong())) {
                                final int otherIndex = this.splitIndexMap.getInt(this.subLevelSplits.get(p2.asLong()));
                                if (currentIndex != otherIndex) {
                                    // two different floodfills have collided, sacrifice the index mapping of the other one
                                    this.splitIndexMap.set(this.subLevelSplits.get(p2.asLong()), currentIndex);
                                }
                            }
                            if (this.heatMapContains(p2)) {
                                if (this.heatMapGet(p2) > currentHeat) {
                                    this.floodfill.add(p2);
                                    this.subLevelSplits.put(p2.asLong(), currentIndex);
                                } else {
                                    this.newStarts.add(p2);
                                }
                            }
                        }
                    }
                    this.heatMapRemove(p);
                }
            } else if (!this.removed.isEmpty()) {
                for (final BlockPos index : this.removed) {
                    final BlockPos p = new BlockPos(index);
                    if (this.heatMapContains(p)) {
                        final int currentHeat = this.heatMapGet(p);
                        for (final BlockPos dir : DIRECTION_OFFSETS) {
                            final BlockPos p2 = p.offset(dir);
                            if (this.isSolidAt(p2) && this.heatMapContains(p2) && this.heatMapGet(p2) > currentHeat) {

                                boolean canRemove = true;
                                for (final BlockPos dir2 : DIRECTION_OFFSETS) {
                                    if (new BlockPos(-dir.getX(), -dir.getY(), -dir.getZ()).equals(dir2))
                                        continue;
                                    final BlockPos p3 = p2.offset(dir2);
                                    if (this.isSolidAt(p3) && this.heatMapContains(p3) && this.heatMapGet(p3) < this.heatMapGet(p2))
                                        canRemove = false;
                                }
                                if (canRemove) {
                                    //start new floodfill from this source, with fresh split index
                                    this.floodfill.add(p2);
                                    final int newIndex = this.splitIndexMap.size();
                                    this.subLevelSplits.put(p2.asLong(), newIndex);
                                    this.splitIndexMap.add(newIndex);
                                }
                            }
                        }
                    }
                    this.heatMapRemove(p);
                }
                this.removed.clear();
            } else if (!this.newStarts.isEmpty()) {
                this.floodfill.addAll(this.newStarts);
                this.newStarts.clear();
                this.state = HeatMapPropagationState.FILLING;
            } else if (!this.subLevelSplits.isEmpty()) {
                this.splitComplete = true;
                // Split off separated regions!
                this.split();
            } else {
                this.splitComplete = true;
                // Nothing left to do, stop looping
                return true;
            }
        }
        return false;
    }

    private void split() {
        final Int2ObjectMap<List<BlockPos>> newSubLevelBlocks = new Int2ObjectOpenHashMap<>();
        for (final long l : this.subLevelSplits.keySet()) {
            final int splitIndex = this.splitIndexMap.get(this.subLevelSplits.get(l));
            newSubLevelBlocks.computeIfAbsent(splitIndex, x -> new ObjectArrayList<>()).add(BlockPos.of(l));
        }

        final boolean splittingWholeSubLevel = newSubLevelBlocks.size() == 1 && this.solidCount == newSubLevelBlocks.values().stream().findFirst().orElseThrow().size();
        // in the case there is only a single split, and it is the entire sub-level, let's just clear the splits and
        // not do anything (ideally). we have to rebuild the heatmap though, as the root is gone...
        if (splittingWholeSubLevel) {
            final List<BlockPos> allBlocks = newSubLevelBlocks.values().stream().findFirst().orElseThrow();
            this.rebuildHeatmapFrom(allBlocks);

            newSubLevelBlocks.clear();
        }

        // if the sum of the newSubLevelBlocks sizes is equal to the heatmap block count, we are about to split
        // the entire sub-level, deleting the original. this will mean the client will receive multiple new sub-levels
        // with no previous "owner" sub-level to trace back the motion of the new sub-levels from.
        // let's avoid this by getting rid of one of the splits to keep it in our sub-level
        int totalSplitBlocks = 0;

        for (final List<BlockPos> blocks : newSubLevelBlocks.values()) {
            totalSplitBlocks += blocks.size();
        }

        if (!splittingWholeSubLevel && totalSplitBlocks != 0 && totalSplitBlocks == this.solidCount) {
            final Map.Entry<Integer, List<BlockPos>> minSize = newSubLevelBlocks.entrySet().stream().sorted(Comparator.comparingInt(a -> -a.getValue().size())).findFirst().orElseThrow();
            this.rebuildHeatmapFrom(minSize.getValue());
            newSubLevelBlocks.remove((int) minSize.getKey());
        }

        this.subLevelSplits.clear();
        this.splitIndexMap.clear();
        this.splitIndexMap.add(0);
        final Level level = this.subLevel.getLevel();

        for (final List<BlockPos> blocks : newSubLevelBlocks.values()) {
            final BoundingBox3i bounds = Objects.requireNonNull(BoundingBox3i.from(blocks)).expand(1, 1, 1);

            for (final SplitListener listener : LISTENERS) {
                listener.addBlocks(level, bounds, blocks);
            }

            final ServerSubLevel subLevel = SubLevelAssemblyHelper.assembleBlocks((ServerLevel) level, blocks.get(0), blocks, bounds);

            // Protect against split sub-levels that have zero mass.
            if (subLevel.getMassTracker().getCenterOfMass() == null || subLevel.getMassTracker().getMass() <= 0.0) {
                subLevel.getPlot().destroyAllBlocks();

                final SubLevelContainer container = Objects.requireNonNull(SubLevelContainer.getContainer(level));
                container.removeSubLevel(subLevel, SubLevelRemovalReason.REMOVED);
            }
        }
    }

    private void rebuildHeatmapFrom(final List<BlockPos> blocks) {
        this.state = HeatMapPropagationState.FILLING;

        this.initialized = false;
        this.splitComplete = false;
        this.solidCount = 0;

        this.newStarts.clear();
        this.floodfill.clear();
        this.removed.clear();

        blocks.forEach(this::heatMapRemove);
        blocks.forEach(this::onSolidAdded);
    }

    private boolean isSolidAt(final BlockPos blockPos) {
        final Level level = this.subLevel.getLevel();
        return !level.getBlockState(blockPos).isAir();
    }

    /**
     * Called whenever a block at a position becomes solid
     *
     * @param blockPos the position of the block
     */
    public void onSolidAdded(final BlockPos blockPos) {
        this.solidCount++;

        if (!this.initialized) {
            this.initialized = true;
            this.heatMapSet(blockPos, (short) 1);
            this.floodfill.add(blockPos);
            this.splitIndexMap.add(0); // index zero reserved for in-air placements
            return;
        }

        int minimumAdjacentHeat = Integer.MAX_VALUE;
        if (this.removed.remove(blockPos))
            return;

        for (final BlockPos direction : DIRECTION_OFFSETS) {
            final BlockPos neighbor = blockPos.offset(direction);

            if (this.heatMapContains(neighbor)) {
                final short heat = this.heatMapGet(neighbor);

                if (heat < minimumAdjacentHeat) {
                    minimumAdjacentHeat = heat;
                }
            }
        }

        if (minimumAdjacentHeat == Integer.MAX_VALUE) {
            // block  placed in thin air
            // assume it is a split section

            if (!this.splitComplete)
                this.subLevelSplits.put(blockPos.asLong(), 0);

        } else {
            this.heatMapSet(blockPos, (short) (minimumAdjacentHeat + 1));
            if (this.state == HeatMapPropagationState.FILLING)
                this.floodfill.add(blockPos);
            else
                this.newStarts.add(blockPos);
        }

    }

    /**
     * Called whenever a block at a position becomes non-solid
     *
     * @param blockPos the position of the block
     */
    public void onSolidRemoved(final BlockPos blockPos) {
        this.solidCount--;
        this.removed.add(blockPos);
    }

    private void heatMapRemove(final BlockPos blockPos) {
        this.heatMapSet(blockPos, (short) 0);
    }

    private boolean heatMapContains(final BlockPos neighbor) {
        return this.heatMapGet(neighbor) != 0;
    }

    private short heatMapGet(final BlockPos blockPos) {
        final LevelPlot plot = this.subLevel.getPlot();
        final SectionPos section = SectionPos.of(blockPos);
        final PlotChunkHolder chunkHolder = plot.getChunkHolder(plot.toLocal(section.chunk()));

        if (chunkHolder == null) {
            return 0;
        }

        final HeatDataChunkSection heatSection = chunkHolder.getHeatSection(section.y());

        if (heatSection == null) {
            return 0;
        }

        return heatSection.get(blockPos.getX() & 15, blockPos.getY() & 15, blockPos.getZ() & 15);
    }

    private void heatMapSet(final BlockPos blockPos, final short value) {
        final LevelPlot plot = this.subLevel.getPlot();
        final SectionPos section = SectionPos.of(blockPos);
        final PlotChunkHolder chunkHolder = plot.getChunkHolder(plot.toLocal(section.chunk()));

        if (chunkHolder == null) {
            return;
        }

        HeatDataChunkSection heatSection = chunkHolder.getHeatSection(section.y());

        if (heatSection == null) {
            heatSection = new HeatDataChunkSection();
            chunkHolder.setHeatSection(section.y(), heatSection);
        }

        heatSection.set(blockPos.getX() & 15, blockPos.getY() & 15, blockPos.getZ() & 15, value);
    }

    /**
     * Adds a split listener
     * @param listener the listener to add
     */
    public static void addSplitListener(final SplitListener listener) {
        LISTENERS.add(listener);
    }

    @FunctionalInterface
    public interface SplitListener {

        void addBlocks(final Level level, final BoundingBox3ic assemblyBounds, final Collection<BlockPos> blocks);

    }
}
