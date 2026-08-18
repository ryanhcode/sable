package dev.ryanhcode.sable;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundFloatingBlockMaterialPacket;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundPhysicsPropertyPacket;
import dev.ryanhcode.sable.physics.chunk.VoxelNeighborhoodState;
import dev.ryanhcode.sable.physics.config.FloatingBlockMaterialDataHandler;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertiesDefinitionLoader;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.physics.floating_block.FloatingBlockController;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import dev.ryanhcode.sable.sublevel.plot.heat.SubLevelHeatMapManager;
import dev.ryanhcode.sable.sublevel.water_occlusion.WaterOcclusionContainer;
import foundry.veil.api.network.VeilPacketManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Common events either dispatched to from mixins for hotswapping convenience, or dispatched to from platform-specific events
 */
public class SableCommonEvents {
    /**
     * Handles a change in blockstate in a chunk at chunk-relative position x, y, z.
     * Only called server-side.
     */
    public static void handleBlockChange(final ServerLevel level, final LevelChunk chunk, final int x, final int y, final int z, final BlockState oldState, final BlockState newState) {
        final ChunkPos chunkPos = chunk.getPos();

        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);

        final PlotChunkHolder plotChunk = container.getChunkHolder(chunkPos);

        final int localX = x & SectionPos.SECTION_MASK;
        final int localZ = z & SectionPos.SECTION_MASK;

        if (plotChunk != null) {
            final LevelPlot plot = container.getPlot(chunkPos);
            final BlockPos blockPos = new BlockPos(x, y, z);

            plotChunk.handleBlockChange(localX, y, localZ, oldState, newState);
            plot.updateBoundingBox();
            plot.expandIfNecessary(blockPos);

            final SubLevel subLevel = plot.getSubLevel();

            final WaterOcclusionContainer<?> waterOcclusionContainer = WaterOcclusionContainer.getContainer(level);

            if (waterOcclusionContainer != null) {
                if (VoxelNeighborhoodState.isSolid(level, blockPos, oldState) != VoxelNeighborhoodState.isSolid(level, blockPos, newState)) {
                    waterOcclusionContainer.markDirty(blockPos);
                }
            }

            // Handle heatmap addition / removal
            if (subLevel instanceof final ServerSubLevel serverSubLevel) {
                final SubLevelHeatMapManager heatMapManager = serverSubLevel.getHeatMapManager();
                final FloatingBlockController floatingBlockController = serverSubLevel.getFloatingBlockController();

                if (oldState != newState){
                    floatingBlockController.queueRemoveFloatingBlock(oldState, blockPos);
                    floatingBlockController.queueAddFloatingBlock(newState, blockPos);
                }

                if (oldState.isAir() && !newState.isAir()) {
                    heatMapManager.onSolidAdded(blockPos);
                }

                if (!oldState.isAir() && newState.isAir()) {
                    heatMapManager.onSolidRemoved(blockPos);
                }
            }

            if (subLevel.isRemoved()) {
                return;
            }
        }

        final int idx = chunk.getSectionIndex(y);
        final LevelChunkSection section = chunk.getSection(idx);
        final SectionPos sectionPos = SectionPos.of(chunkPos, chunk.getSectionYFromSectionIndex(idx));

        container.physicsSystem().handleBlockChange(sectionPos, section, localX, y & 15, localZ, oldState, newState);
    }

    public static void syncDataPacket(final VeilPacketManager.PacketSink sink) {
        sink.sendPacket(PhysicsBlockPropertiesDefinitionLoader.INSTANCE.getDefinitions().stream().map(ClientboundPhysicsPropertyPacket::new).toArray(CustomPacketPayload[]::new));
        sink.sendPacket(DimensionPhysicsData.compilePacket());
        sink.sendPacket(FloatingBlockMaterialDataHandler.allMaterials.entrySet().stream().map(e -> new ClientboundFloatingBlockMaterialPacket(e.getKey(), e.getValue())).toArray(CustomPacketPayload[]::new));
    }
}
