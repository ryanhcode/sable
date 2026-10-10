package dev.ryanhcode.sable.mixin.dynamic_directional_shading;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.mixinterface.dynamic_directional_shading.ModelBlockRendererCacheExtension;
import dev.ryanhcode.sable.render.dynamic_shade.SableDynamicDirectionalShading;
import dev.ryanhcode.sable.render.dynamic_shade.SubLevelVertexConsumer;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Mixin into {@link net.minecraft.client.renderer.block.ModelBlockRenderer} to apply dynamic directional shading to blocks rendered inside sub-levels.
 */
@Mixin(ModelBlockRenderer.class)
public class ModelBlockRendererMixin {

    @Shadow
    @Final
    public static ThreadLocal<ModelBlockRenderer.Cache> CACHE;

    @ModifyVariable(method = "putQuadData", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private VertexConsumer sable$modifyConsumer(final VertexConsumer value) {
        return SableDynamicDirectionalShading.isEnabled() && ((ModelBlockRendererCacheExtension) CACHE.get()).sable$getOnSubLevel() ? new SubLevelVertexConsumer(value) : value;
    }

    @WrapOperation(method = "renderModelFaceFlat", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/BlockAndTintGetter;getShade(Lnet/minecraft/core/Direction;Z)F"))
    public float getShade(final BlockAndTintGetter instance, final Direction direction, final boolean cull, final Operation<Float> original) {
        final boolean onSubLevel = SableDynamicDirectionalShading.isEnabled() && ((ModelBlockRendererCacheExtension) ModelBlockRenderer.CACHE.get()).sable$getOnSubLevel();
        return onSubLevel ? 1.0f : original.call(instance, direction, cull);
    }
}
