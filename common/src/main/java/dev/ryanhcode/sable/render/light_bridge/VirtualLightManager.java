package dev.ryanhcode.sable.render.light_bridge;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.mixinterface.plot.SubLevelContainerHolder;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages virtual light sources projected from sub-levels into the parent world.
 * Maintains per-sub-level spread maps so that a sub-level can query world light
 * excluding its own contribution (preventing feedback loops).
 */
public final class VirtualLightManager {

    private static final VirtualLightManager INSTANCE = new VirtualLightManager();

    /** Combined virtual lights from ALL sub-levels: packed BlockPos → light level */
    private final Long2IntOpenHashMap activeLights = new Long2IntOpenHashMap();

    /** Per-sub-level spread maps: UUID → (packed BlockPos → light level) */
    private final ConcurrentHashMap<UUID, Long2IntOpenHashMap> perSubLevelLights = new ConcurrentHashMap<>();

    private static final Logger LOGGER = LoggerFactory.getLogger("SableLightBridge");
    private static long debugCounter = 0;

    /** Thread-local flag to suppress virtual light injection during world light sampling */
    private static final ThreadLocal<Boolean> sampling = ThreadLocal.withInitial(() -> false);

    /** Cached world block light near sub-levels, populated on main thread, read on worker threads */
    private final Long2IntOpenHashMap worldLightCache = new Long2IntOpenHashMap();

    /** Sub-levels that need DataLayer write + rebuild on the next tick */
    private final java.util.Set<UUID> pendingRebuilds = java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    private VirtualLightManager() {
        this.activeLights.defaultReturnValue(0);
    }

    public static VirtualLightManager get() {
        return INSTANCE;
    }

    public void tick(final ClientLevel level) {
        if (!(level instanceof final SubLevelContainerHolder holder)) return;

        final ClientSubLevelContainer container = (ClientSubLevelContainer) holder.sable$getPlotContainer();
        if (container == null) return;

        final Long2IntOpenHashMap newCombined = new Long2IntOpenHashMap();
        newCombined.defaultReturnValue(0);

        final ConcurrentHashMap<UUID, Long2IntOpenHashMap> newPerSubLevel = new ConcurrentHashMap<>();

        for (final ClientSubLevel subLevel : container.getAllSubLevels()) {
            if (!subLevel.isFinalized()) continue;

            final Long2IntOpenHashMap sources = SubLevelLightBridge.collectEmittingBlocks(subLevel);
            if (sources.isEmpty()) continue;

            // Build spread map for this sub-level
            final Long2IntOpenHashMap spread = new Long2IntOpenHashMap();
            spread.defaultReturnValue(0);

            for (final var entry : sources.long2IntEntrySet()) {
                spreadLight(spread, entry.getLongKey(), entry.getIntValue());
            }

            newPerSubLevel.put(subLevel.getUniqueId(), spread);

            // Merge into combined map
            for (final var entry : spread.long2IntEntrySet()) {
                final int existing = newCombined.get(entry.getLongKey());
                if (entry.getIntValue() > existing) {
                    newCombined.put(entry.getLongKey(), entry.getIntValue());
                }
            }
        }

        // Diff for dirty marking
        final LongSet allAffected = new LongOpenHashSet();

        for (final var entry : activeLights.long2IntEntrySet()) {
            final int newVal = newCombined.get(entry.getLongKey());
            if (newVal != entry.getIntValue()) {
                allAffected.add(entry.getLongKey());
            }
        }
        for (final var entry : newCombined.long2IntEntrySet()) {
            if (!activeLights.containsKey(entry.getLongKey())) {
                allAffected.add(entry.getLongKey());
            }
        }

        activeLights.clear();
        activeLights.putAll(newCombined);

        perSubLevelLights.clear();
        perSubLevelLights.putAll(newPerSubLevel);

        markDirty(level, allAffected);
    }

    private static void spreadLight(final Long2IntOpenHashMap map, final long sourcePos, final int emission) {
        final int sx = BlockPos.getX(sourcePos);
        final int sy = BlockPos.getY(sourcePos);
        final int sz = BlockPos.getZ(sourcePos);

        for (int dx = -emission; dx <= emission; dx++) {
            final int adx = Math.abs(dx);
            final int remainYZ = emission - adx;
            for (int dy = -remainYZ; dy <= remainYZ; dy++) {
                final int ady = Math.abs(dy);
                final int remainZ = remainYZ - ady;
                for (int dz = -remainZ; dz <= remainZ; dz++) {
                    final int dist = adx + ady + Math.abs(dz);
                    final int lightLevel = emission - dist;
                    if (lightLevel <= 0) continue;

                    final long packed = BlockPos.asLong(sx + dx, sy + dy, sz + dz);
                    final int existing = map.get(packed);
                    if (lightLevel > existing) {
                        map.put(packed, lightLevel);
                    }
                }
            }
        }
    }

    /**
     * Returns virtual light at a position from ALL sub-levels.
     * Used for sub-level → world injection.
     */
    public int getVirtualLight(final long packedPos) {
        return activeLights.get(packedPos);
    }

    public int getVirtualLight(final BlockPos pos) {
        return activeLights.get(pos.asLong());
    }

    /**
     * Returns virtual light at a position EXCLUDING a specific sub-level's contribution.
     * Used when sampling world light for a sub-level to prevent feedback loops.
     */
    public int getVirtualLightExcluding(final BlockPos pos, final UUID excludeSubLevel) {
        return getVirtualLightExcluding(pos.asLong(), excludeSubLevel);
    }

    public int getVirtualLightExcluding(final long packedPos, final UUID excludeSubLevel) {
        int max = 0;
        for (final var entry : perSubLevelLights.entrySet()) {
            if (entry.getKey().equals(excludeSubLevel)) continue;
            final int val = entry.getValue().get(packedPos);
            if (val > max) max = val;
        }
        return max;
    }

    public boolean hasAnyLights() {
        return !activeLights.isEmpty();
    }

    public void setSampling(final boolean value) {
        sampling.set(value);
    }

    public boolean isSampling() {
        return sampling.get();
    }

    public void onSubLevelRemoved(final UUID subLevelId, final ClientLevel level) {
        perSubLevelLights.remove(subLevelId);
    }

    public void clear() {
        activeLights.clear();
        perSubLevelLights.clear();
    }

    /**
     * Called when a world block light update occurs at a section position.
     * Checks if any sub-level's visual bounding box overlaps that section
     * and marks their render sections dirty if so.
     */
    public void onWorldLightUpdate(final ClientLevel level, final int sectionX, final int sectionY, final int sectionZ) {
        if (!(level instanceof final SubLevelContainerHolder holder)) return;

        final ClientSubLevelContainer container = (ClientSubLevelContainer) holder.sable$getPlotContainer();
        if (container == null) return;

        final double minX = SectionPos.sectionToBlockCoord(sectionX);
        final double minY = SectionPos.sectionToBlockCoord(sectionY);
        final double minZ = SectionPos.sectionToBlockCoord(sectionZ);
        final double maxX = minX + 16;
        final double maxY = minY + 16;
        final double maxZ = minZ + 16;

        for (final ClientSubLevel subLevel : container.getAllSubLevels()) {
            if (!subLevel.isFinalized()) continue;

            final var bounds = subLevel.boundingBox();
            final double margin = 15.0;
            if (maxX + margin >= bounds.minX() && minX - margin <= bounds.maxX()
                    && maxY + margin >= bounds.minY() && minY - margin <= bounds.maxY()
                    && maxZ + margin >= bounds.minZ() && minZ - margin <= bounds.maxZ()) {

                // World → sub-level injection disabled for now — needs more work
                // WorldToSubLevelLightInjector.onSectionLightChanged(level, subLevel, sectionX, sectionY, sectionZ);
            }
        }
    }

    /**
     * Caches world block light for every block in a sub-level by transforming
     * plot positions to world space and reading the light engine on the main thread.
     */
    private void cacheWorldLightForSubLevel(final ClientLevel level, final ClientSubLevel subLevel) {
        final var plot = subLevel.getPlot();
        final var plotBounds = plot.getBoundingBox();
        if (plotBounds == null) return;

        final var pose = subLevel.logicalPose();
        final var worldPos = new org.joml.Vector3d();
        final var probe = new BlockPos.MutableBlockPos();

        setSampling(true);
        try {
            // Write world light to plot blocks AND their neighbors (±1 in each axis)
            // because face lighting reads from adjacent blocks
            final int margin = 2;
            for (int y = plotBounds.minY() - margin; y <= plotBounds.maxY() + margin; y++) {
                for (int x = plotBounds.minX() - margin; x <= plotBounds.maxX() + margin; x++) {
                    for (int z = plotBounds.minZ() - margin; z <= plotBounds.maxZ() + margin; z++) {
                        worldPos.set(x + 0.5, y + 0.5, z + 0.5);
                        pose.transformPosition(worldPos);
                        probe.set(worldPos.x, worldPos.y, worldPos.z);

                        final SubLevel atWorld = Sable.HELPER.getContaining(level, new ChunkPos(probe));
                        if (atWorld != null) continue;

                        int worldLight = level.getLightEngine()
                                .getLayerListener(LightLayer.BLOCK)
                                .getLightValue(probe);

                        // Also check virtual light from other sub-levels
                        if (hasAnyLights()) {
                            final int vl = getVirtualLightExcluding(probe, subLevel.getUniqueId());
                            if (vl > worldLight) worldLight = vl;
                        }

                        // Store in cache — write ALL values including 0 to clear stale entries
                        final long plotPacked = BlockPos.asLong(x, y, z);
                        worldLightCache.put(plotPacked, java.lang.Math.max(0, worldLight));
                    }
                }
            }
        } finally {
            setSampling(false);
        }
    }

    /**
     * Returns cached world light at a plot-local position.
     * Called from worker threads during section compilation.
     */
    public int getCachedWorldLight(final BlockPos plotPos) {
        return worldLightCache.get(plotPos.asLong());
    }

    public int getCachedWorldLight(final long packedPlotPos) {
        return worldLightCache.get(packedPlotPos);
    }

    private static void markDirty(final ClientLevel level, final LongSet positions) {
        if (positions.isEmpty()) return;

        final LongSet dirtySections = new LongOpenHashSet();
        final LongIterator it = positions.iterator();
        while (it.hasNext()) {
            final long packed = it.nextLong();
            dirtySections.add(SectionPos.asLong(
                    SectionPos.blockToSectionCoord(BlockPos.getX(packed)),
                    SectionPos.blockToSectionCoord(BlockPos.getY(packed)),
                    SectionPos.blockToSectionCoord(BlockPos.getZ(packed))
            ));
        }

        final LongIterator secIt = dirtySections.iterator();
        while (secIt.hasNext()) {
            final long sec = secIt.nextLong();
            level.setSectionDirtyWithNeighbors(
                    SectionPos.x(sec),
                    SectionPos.y(sec),
                    SectionPos.z(sec)
            );
        }
    }
}
