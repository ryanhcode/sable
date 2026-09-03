package dev.ryanhcode.sable.mixin.plot.lighting;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixinterface.plot.SubLevelContainerHolder;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Makes brightness queries inside plots use the plot's own light engine.
 */
@Mixin(BlockAndTintGetter.class)
public interface BlockAndTintGetterMixin {

    @Shadow LevelLightEngine getLightEngine();

    /**
     * @author RyanH
     * @reason Make brightness queries in plots use the plot light engine
     */
    @Overwrite
    default int getBrightness(final LightLayer lightLayer, final BlockPos blockPos) {
        LevelLightEngine engine = this.getLightEngine();

        if (this instanceof final SubLevelContainerHolder holder) {
            final SubLevelContainer plotContainer = holder.sable$getPlotContainer();

            if (plotContainer.getLevel() instanceof ServerLevel) {
                final LevelPlot plot = plotContainer.getPlot(new ChunkPos(blockPos));

                if (plot != null) {
                    engine = plot.getLightEngine();
                }
            }
        }

        return engine.getLayerListener(lightLayer).getLightValue(blockPos);
    }

    /**
     * @author RyanH
     * @reason Make brightness queries in plots use the plot light engine
     */
    @Overwrite
    default int getRawBrightness(final BlockPos blockPos, final int i) {
        LevelLightEngine engine = this.getLightEngine();

        if (this instanceof final SubLevelContainerHolder holder) {
            final SubLevelContainer plotContainer = holder.sable$getPlotContainer();

            if (plotContainer.getLevel() instanceof ServerLevel) {
                final LevelPlot plot = plotContainer.getPlot(new ChunkPos(blockPos));

                if (plot != null) {
                    engine = plot.getLightEngine();
                }
            }
        }

        return engine.getRawBrightness(blockPos, i);
    }

}
