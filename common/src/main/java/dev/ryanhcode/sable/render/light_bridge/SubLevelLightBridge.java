package dev.ryanhcode.sable.render.light_bridge;

import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.joml.Vector3d;

/**
 * Bridges light data between sub-levels and the parent world.
 * <p>
 * Direction 1 (world → sub-level): Samples block light from the parent level
 * at the sub-level's visual position and provides a per-section boost value
 * that the shader can use to brighten sub-level blocks.
 * <p>
 * Direction 2 (sub-level → world): Tracks light-emitting blocks inside a
 * sub-level, transforms their positions to world space, and injects virtual
 * light into the parent level so nearby real-world blocks are illuminated.
 */
public final class SubLevelLightBridge {

    private SubLevelLightBridge() {
    }

    // ── Direction 1: world → sub-level ──────────────────────────────────

    /**
     * Samples the maximum block light in the parent level around the world-space
     * position that corresponds to a given section origin inside a sub-level.
     *
     * @param subLevel      the client sub-level being rendered
     * @param sectionOrigin the section origin in plot-local coordinates (block pos)
     * @return the maximum sampled block light [0-15]
     */
    public static int sampleWorldBlockLightForSection(final ClientSubLevel subLevel, final BlockPos sectionOrigin) {
        final Pose3dc pose = subLevel.renderPose();
        final ClientLevel level = subLevel.getLevel();

        int maxLight = 0;
        final Vector3d worldPos = new Vector3d();
        final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();

        for (int dx = 0; dx <= 16; dx += 8) {
            for (int dy = 0; dy <= 16; dy += 8) {
                for (int dz = 0; dz <= 16; dz += 8) {
                    worldPos.set(
                            sectionOrigin.getX() + dx,
                            sectionOrigin.getY() + dy,
                            sectionOrigin.getZ() + dz
                    );
                    pose.transformPosition(worldPos);
                    probe.set(worldPos.x, worldPos.y, worldPos.z);

                    final int light = level.getBrightness(LightLayer.BLOCK, probe);
                    if (light > maxLight) {
                        maxLight = light;
                        if (maxLight >= 15) return 15;
                    }
                }
            }
        }

        return maxLight;
    }

    /**
     * Computes a block-light boost for the whole sub-level by sampling at the
     * bounding-box center + face centers. Cheaper than per-section but less accurate.
     */
    public static int sampleWorldBlockLightForSubLevel(final ClientSubLevel subLevel) {
        final BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
        if (bounds == null) return 0;

        final Pose3dc pose = subLevel.renderPose();
        final ClientLevel level = subLevel.getLevel();
        final Vector3d worldPos = new Vector3d();
        final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();

        int maxLight = 0;

        final double cx = (bounds.minX() + bounds.maxX()) / 2.0;
        final double cy = (bounds.minY() + bounds.maxY()) / 2.0;
        final double cz = (bounds.minZ() + bounds.maxZ()) / 2.0;

        final double[][] samples = {
                {cx, cy, cz},
                {bounds.minX(), cy, cz},
                {bounds.maxX() + 1, cy, cz},
                {cx, bounds.minY(), cz},
                {cx, bounds.maxY() + 1, cz},
                {cx, cy, bounds.minZ()},
                {cx, cy, bounds.maxZ() + 1},
        };

        for (final double[] s : samples) {
            worldPos.set(s[0], s[1], s[2]);
            pose.transformPosition(worldPos);
            probe.set(worldPos.x, worldPos.y, worldPos.z);

            final int light = level.getBrightness(LightLayer.BLOCK, probe);
            if (light > maxLight) {
                maxLight = light;
                if (maxLight >= 15) return 15;
            }
        }

        return maxLight;
    }

    // ── Direction 2: sub-level → world ──────────────────────────────────

    /**
     * Scans a sub-level's plot for light-emitting blocks and returns their
     * world-space positions + emission levels.
     * <p>
     * Call this once per tick (or when blocks change) and feed the result to
     * {@link VirtualLightManager}.
     *
     * @param subLevel the sub-level to scan
     * @return map of packed world BlockPos → light emission level
     */
    public static Long2IntOpenHashMap collectEmittingBlocks(final ClientSubLevel subLevel) {
        final Long2IntOpenHashMap result = new Long2IntOpenHashMap();
        result.defaultReturnValue(0);

        final LevelPlot plot = subLevel.getPlot();
        final BoundingBox3ic bounds = plot.getBoundingBox();
        if (bounds == null) return result;

        final Pose3dc pose = subLevel.logicalPose();
        final Vector3d worldPos = new Vector3d();

        for (final PlotChunkHolder holder : plot.getLoadedChunks()) {
            final LevelChunk chunk = holder.getChunk();
            if (chunk == null) continue;

            final int baseX = chunk.getPos().getMinBlockX();
            final int baseZ = chunk.getPos().getMinBlockZ();

            for (int sIdx = 0; sIdx < chunk.getSectionsCount(); sIdx++) {
                final LevelChunkSection section = chunk.getSection(sIdx);
                if (section.hasOnlyAir()) continue;

                final int baseY = chunk.getLevel().getSectionYFromSectionIndex(sIdx) << 4;

                for (int x = 0; x < 16; x++) {
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            final BlockState state = section.getBlockState(x, y, z);
                            final int emission = state.getLightEmission();
                            if (emission <= 0) continue;

                            worldPos.set(baseX + x + 0.5, baseY + y + 0.5, baseZ + z + 0.5);
                            pose.transformPosition(worldPos);

                            final long packed = BlockPos.asLong(
                                    (int) Math.floor(worldPos.x),
                                    (int) Math.floor(worldPos.y),
                                    (int) Math.floor(worldPos.z)
                            );

                            final int existing = result.get(packed);
                            if (emission > existing) {
                                result.put(packed, emission);
                            }
                        }
                    }
                }
            }
        }

        return result;
    }
}
