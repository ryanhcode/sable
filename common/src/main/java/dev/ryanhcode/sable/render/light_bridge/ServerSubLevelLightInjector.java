package dev.ryanhcode.sable.render.light_bridge;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import dev.ryanhcode.sable.sublevel.plot.SubLevelPlayerChunkSender;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.lighting.LightEngine;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ServerSubLevelLightInjector {

    private static final ConcurrentHashMap<UUID, LongSet> injectedPositions = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Long2IntOpenHashMap> cachedWorldSources = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Vector3d> lastPosePositions = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Boolean> needsRescan = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Boolean> needsReinject = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Vector3d> lastRescanPositions = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Boolean> needsResend = new ConcurrentHashMap<>();

    private ServerSubLevelLightInjector() {
    }

    /**
     * Called when server-side block light changes in a section.
     * Scans just that section for emitters and updates the cache.
     */
    public static void onServerLightUpdate(final ServerLevel level, final int sectionX, final int sectionY, final int sectionZ) {
        final var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
        if (container == null) return;

        final double secMinX = SectionPos.sectionToBlockCoord(sectionX);
        final double secMinY = SectionPos.sectionToBlockCoord(sectionY);
        final double secMinZ = SectionPos.sectionToBlockCoord(sectionZ);
        final double secMaxX = secMinX + 16;
        final double secMaxY = secMinY + 16;
        final double secMaxZ = secMinZ + 16;

        for (final SubLevel subLevel : container.getAllSubLevels()) {
            final BoundingBox3dc bounds = subLevel.boundingBox();
            if (bounds == null) continue;

            final double margin = 15.0;
            if (secMaxX + margin >= bounds.minX() && secMinX - margin <= bounds.maxX()
                    && secMaxY + margin >= bounds.minY() && secMinY - margin <= bounds.maxY()
                    && secMaxZ + margin >= bounds.minZ() && secMinZ - margin <= bounds.maxZ()) {

                updateCacheFromSection(level, subLevel, sectionX, sectionY, sectionZ);
                needsReinject.put(subLevel.getUniqueId(), Boolean.TRUE);
            }
        }
    }

    /**
     * Scans a single 16x16x16 world section for light emitters and updates the cache.
     */
    private static void updateCacheFromSection(final ServerLevel level, final SubLevel subLevel,
                                                final int sectionX, final int sectionY, final int sectionZ) {
        if (!level.hasChunk(sectionX, sectionZ)) return;

        final UUID id = subLevel.getUniqueId();
        final Long2IntOpenHashMap sources = cachedWorldSources.computeIfAbsent(id, k -> {
            final var m = new Long2IntOpenHashMap();
            m.defaultReturnValue(0);
            return m;
        });

        final int worldMinX = SectionPos.sectionToBlockCoord(sectionX);
        final int worldMinY = SectionPos.sectionToBlockCoord(sectionY);
        final int worldMinZ = SectionPos.sectionToBlockCoord(sectionZ);

        // Remove old entries from this section
        final var iter = sources.long2IntEntrySet().iterator();
        while (iter.hasNext()) {
            final var entry = iter.next();
            final int x = BlockPos.getX(entry.getLongKey());
            final int y = BlockPos.getY(entry.getLongKey());
            final int z = BlockPos.getZ(entry.getLongKey());
            if (x >= worldMinX && x < worldMinX + 16
                    && y >= worldMinY && y < worldMinY + 16
                    && z >= worldMinZ && z < worldMinZ + 16) {
                iter.remove();
            }
        }

        // Scan section for emitters
        for (int wy = worldMinY; wy < worldMinY + 16; wy++) {
            if (wy < level.getMinBuildHeight() || wy >= level.getMaxBuildHeight()) continue;
            for (int wx = worldMinX; wx < worldMinX + 16; wx++) {
                for (int wz = worldMinZ; wz < worldMinZ + 16; wz++) {
                    final SubLevel atWorld = Sable.HELPER.getContaining(level, new ChunkPos(wx >> 4, wz >> 4));
                    if (atWorld == subLevel) continue;

                    final BlockState state = level.getBlockState(new BlockPos(wx, wy, wz));
                    final int emission = state.getLightEmission();
                    if (emission > 0) {
                        sources.put(BlockPos.asLong(wx, wy, wz), emission);
                    }
                }
            }
        }
    }

    /**
     * Marks a sub-level as needing a full rescan. Call when a new sub-level is created/split.
     */
    public static void markNeedsFullRescan(final UUID subLevelId) {
        needsRescan.put(subLevelId, Boolean.TRUE);
    }

    /**
     * Full bounding box rescan — only used for new/split sub-levels.
     */
    private static void fullRescan(final ServerLevel level, final ServerSubLevel subLevel) {
        final BoundingBox3dc bounds = subLevel.boundingBox();
        if (bounds == null) return;

        final UUID id = subLevel.getUniqueId();
        final Long2IntOpenHashMap sources = new Long2IntOpenHashMap();
        sources.defaultReturnValue(0);

        final int margin = 15;
        final int minX = (int) Math.floor(bounds.minX()) - margin;
        final int minY = Math.max(level.getMinBuildHeight(), (int) Math.floor(bounds.minY()) - margin);
        final int minZ = (int) Math.floor(bounds.minZ()) - margin;
        final int maxX = (int) Math.ceil(bounds.maxX()) + margin;
        final int maxY = Math.min(level.getMaxBuildHeight() - 1, (int) Math.ceil(bounds.maxY()) + margin);
        final int maxZ = (int) Math.ceil(bounds.maxZ()) + margin;

        for (int wy = minY; wy <= maxY; wy++) {
            for (int wx = minX; wx <= maxX; wx++) {
                for (int wz = minZ; wz <= maxZ; wz++) {
                    if (!level.hasChunk(wx >> 4, wz >> 4)) continue;
                    final SubLevel atWorld = Sable.HELPER.getContaining(level, new ChunkPos(wx >> 4, wz >> 4));
                    if (atWorld == subLevel) continue;

                    final BlockState state = level.getBlockState(new BlockPos(wx, wy, wz));
                    final int emission = state.getLightEmission();
                    if (emission > 0) {
                        sources.put(BlockPos.asLong(wx, wy, wz), emission);
                    }
                }
            }
        }

        cachedWorldSources.put(id, sources);
        lastRescanPositions.put(id, new Vector3d(subLevel.logicalPose().position()));
    }

    /**
     * Called from ServerLevelPlot.tick() before light engine updates.
     */
    public static void tickPlot(final ServerLevel level, final ServerSubLevel subLevel, final ServerLevelPlot plot) {
        final UUID id = subLevel.getUniqueId();

        try {
            // Track pose for movement detection
            final Vector3dc currentPos = subLevel.logicalPose().position();
            final Vector3d lastPos = lastPosePositions.get(id);
            if (lastPos == null) {
                lastPosePositions.put(id, new Vector3d(currentPos));
            } else if (lastPos.distanceSquared(currentPos) > 0.25) {
                lastPosePositions.put(id, new Vector3d(currentPos));
                needsReinject.put(id, Boolean.TRUE);

                // If moved far enough from last rescan, do a full rescan
                // to pick up torches in the new area
                final Vector3d lastRescan = lastRescanPositions.get(id);
                if (lastRescan == null || lastRescan.distanceSquared(currentPos) > 8 * 8) {
                    needsRescan.put(id, Boolean.TRUE);
                }
            }

            // Full rescan for new/split sub-levels
            if (needsRescan.remove(id, Boolean.TRUE)) {
                fullRescan(level, subLevel);
                needsReinject.put(id, Boolean.TRUE);
            }

            // Re-inject if needed
            if (needsReinject.remove(id, Boolean.TRUE)) {
                final boolean success = reinject(level, subLevel, plot);
                // If reinject failed to inject anything but we have sources,
                // retry next tick (plot sections might not be ready yet)
                if (!success) {
                    final Long2IntOpenHashMap src = cachedWorldSources.get(id);
                    if (src != null && !src.isEmpty()) {
                        needsRescan.put(id, Boolean.TRUE);
                    }
                }
            }
        } catch (final Throwable e) {
            // Safety
        }
    }

    private static boolean reinject(final ServerLevel level, final ServerSubLevel subLevel, final ServerLevelPlot plot) {
        final UUID subLevelId = subLevel.getUniqueId();
        final Pose3dc pose = subLevel.logicalPose();
        final LevelLightEngine plotLightEngine = plot.getLightEngine();
        if (plotLightEngine.blockEngine == null) return false;

        boolean changed = false;

        // Clean up old
        final LongSet oldPositions = injectedPositions.remove(subLevelId);
        if (oldPositions != null) {
            for (final long packed : oldPositions) {
                try {
                    final BlockState state = level.getBlockState(BlockPos.of(packed));
                    if (state.getLightEmission() <= 0) {
                        final int oldLevel = plotLightEngine.blockEngine.storage.getStoredLevel(packed);
                        if (oldLevel > 0) {
                            plotLightEngine.blockEngine.storage.setStoredLevel(packed, 0);
                            plotLightEngine.blockEngine.enqueueDecrease(
                                    packed, LightEngine.QueueEntry.decreaseAllDirections(oldLevel));
                            changed = true;
                        }
                    }
                } catch (final NullPointerException ignored) {}
            }
            if (changed) {
                do { plotLightEngine.runLightUpdates(); } while (plotLightEngine.hasLightWork());
            }
        }

        // Inject cached sources
        final Long2IntOpenHashMap sources = cachedWorldSources.get(subLevelId);
        if (sources == null || sources.isEmpty()) {
            if (changed) {
                plotLightEngine.blockEngine.storage.swapSectionMap();
                needsResend.put(subLevelId, Boolean.TRUE);
            }
            return changed;
        }

        final LongSet newPositions = new LongOpenHashSet();
        final Vector3d plotLocal = new Vector3d();

        for (final var entry : sources.long2IntEntrySet()) {
            final int wx = BlockPos.getX(entry.getLongKey());
            final int wy = BlockPos.getY(entry.getLongKey());
            final int wz = BlockPos.getZ(entry.getLongKey());
            final int emission = entry.getIntValue();

            plotLocal.set(wx + 0.5, wy + 0.5, wz + 0.5);
            pose.transformPositionInverse(plotLocal);

            final BlockPos plotPos = BlockPos.containing(plotLocal.x, plotLocal.y, plotLocal.z);
            if (!plot.contains(plotLocal)) continue;

            final long plotPacked = plotPos.asLong();
            try {
                plotLightEngine.blockEngine.storage.setStoredLevel(plotPacked, emission);
                plotLightEngine.blockEngine.enqueueIncrease(
                        plotPacked, LightEngine.QueueEntry.increaseLightFromEmission(emission, true));
                newPositions.add(plotPacked);
                changed = true;
            } catch (final NullPointerException ignored) {
                // Section has no DataLayer yet — skip
            }
        }

        if (!newPositions.isEmpty()) {
            injectedPositions.put(subLevelId, newPositions);
        }

        if (changed) {
            plotLightEngine.blockEngine.storage.swapSectionMap();
            needsResend.put(subLevelId, Boolean.TRUE);
        }

        return !newPositions.isEmpty();
    }

    public static void afterPlotTick(final ServerLevel level, final ServerSubLevel subLevel, final ServerLevelPlot plot) {
        final UUID id = subLevel.getUniqueId();
        if (!needsResend.remove(id, Boolean.TRUE)) return;

        for (final PlotChunkHolder holder : plot.getLoadedChunks()) {
            final var chunk = holder.getChunk();
            if (chunk == null) continue;

            final ChunkPos globalPos = holder.getPos();
            final var players = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level).getPlayersTracking(globalPos);
            for (final ServerPlayer player : players) {
                SubLevelPlayerChunkSender.sendChunk(player.connection::send, plot.getLightEngine(), chunk);
            }
        }
    }

    public static void clear() {
        injectedPositions.clear();
        cachedWorldSources.clear();
        lastPosePositions.clear();
        needsRescan.clear();
        needsReinject.clear();
        needsResend.clear();
        lastRescanPositions.clear();
    }
}
