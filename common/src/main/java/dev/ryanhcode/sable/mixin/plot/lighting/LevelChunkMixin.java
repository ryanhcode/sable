package dev.ryanhcode.sable.mixin.plot.lighting;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LevelChunk.class)
public class LevelChunkMixin {

    @Shadow @Final private Level level;

    /**
     * Return the plot light engine if we're in a plot
     */
    @ModifyExpressionValue(method = "setBlockState", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/ChunkSource;getLightEngine()Lnet/minecraft/world/level/lighting/LevelLightEngine;"))
    public LevelLightEngine sable$getLightEngine(final LevelLightEngine original) {
        final SubLevelContainer container = SubLevelContainer.getContainer(this.level);

        if (container != null && this.level instanceof ServerLevel) {
            final LevelChunk chunk = (LevelChunk) (Object) this;
            final LevelPlot plot = container.getPlot(chunk.getPos());

            if (plot != null) {
                return plot.getLightEngine();
            }
        }

        return original;
    }

}
