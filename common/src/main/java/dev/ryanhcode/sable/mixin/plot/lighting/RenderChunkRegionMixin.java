package dev.ryanhcode.sable.mixin.plot.lighting;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixinterface.plot.SubLevelContainerHolder;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Makes render chunk regions implement {@link SubLevelContainerHolder}, exposing their plot container.
 */
@Mixin(RenderChunkRegion.class)
public class RenderChunkRegionMixin implements SubLevelContainerHolder {


    @Shadow @Final protected Level level;

    @Override
    public SubLevelContainer sable$getPlotContainer() {
        return SubLevelContainer.getContainer(this.level);
    }
}
