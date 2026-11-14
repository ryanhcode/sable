package dev.ryanhcode.sable.sublevel.plot;

import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.platform.SableChunkEventPlatform;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;

/**
 * An allocated & reserved space in a level belonging to a {@link SubLevel}, holding its own chunk grid.
 */
public class ClientLevelPlot extends LevelPlot {
    /**
     * Creates a new plot at the given plot coordinate.
     *
     * @param plotContainer the parent plot container of this level plot
     * @param x             the global X coordinate of the plot, in units of {@code 1 << logSize} chunks
     * @param z             the global Z coordinate of the plot, in units of {@code 1 << logSize} chunks
     * @param logSize       the log_2 of the side length of a plot
     * @param subLevel      the sub-level using this plot
     */
    public ClientLevelPlot(final SubLevelContainer plotContainer, final int x, final int z, final int logSize, final ClientSubLevel subLevel) {
        super(plotContainer, x, z, logSize, subLevel);
    }

    /**
     * Returns the lighting engine this sub-level should use.
     * This is done due to {@link ServerSubLevel ServerSubLevels} having their own lighting engine,
     *
     * @return the lighting engine for this plot, or null if not set
     */
    @Override
    public LevelLightEngine getLightEngine() {
        return this.getSubLevel().getLevel().getLightEngine();
    }

    /**
     * @return the sub-level using this plot.
     */
    @Override
    public ClientSubLevel getSubLevel() {
        return (ClientSubLevel) super.getSubLevel();
    }

    @Override
    protected void onRemoveChunkHolder(final LevelChunk levelChunk) {
        SableChunkEventPlatform.INSTANCE.unloadClientChunk(levelChunk);
        ((ClientLevel) levelChunk.getLevel()).unload(levelChunk);
    }

    @Override
    public void addChunkHolder(final ChunkPos localChunkPos, final PlotChunkHolder holder, final boolean initializeLighting) {
        super.addChunkHolder(localChunkPos, holder, initializeLighting);

        for (final BlockEntity blockEntity : holder.getChunk().getBlockEntities().values()) {
            final BlockEntitySubLevelActor actor = blockEntity instanceof BlockEntitySubLevelActor ? (BlockEntitySubLevelActor) blockEntity : null;

            if (actor != null) {
                this.blockEntityActors.put(blockEntity.getBlockPos(), actor);
            }
        }
    }
}
