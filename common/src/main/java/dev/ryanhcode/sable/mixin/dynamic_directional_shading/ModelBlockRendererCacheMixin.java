package dev.ryanhcode.sable.mixin.dynamic_directional_shading;

import dev.ryanhcode.sable.mixinterface.dynamic_directional_shading.ModelBlockRendererCacheExtension;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Mixin into {@link net.minecraft.client.renderer.block.ModelBlockRenderer.Cache} to track whether the current block is on a sub-level, implementing {@link dev.ryanhcode.sable.mixinterface.dynamic_directional_shading.ModelBlockRendererCacheExtension}.
 */
@Mixin(ModelBlockRenderer.Cache.class)
public class ModelBlockRendererCacheMixin implements ModelBlockRendererCacheExtension {

    @Unique
    private boolean sable$onSubLevel;

    @Override
    public void sable$setOnSubLevel(final boolean onSubLevel) {
        this.sable$onSubLevel = onSubLevel;
    }

    @Override
    public boolean sable$getOnSubLevel() {
        return this.sable$onSubLevel;
    }

}
