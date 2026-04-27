package dev.ryanhcode.sable.neoforge.mixin.block_entity_visible;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = LevelRenderer.class, priority = 2000)
public class LevelRendererMixin {

    /**
     * @author RyanH
     * @reason Take sub-levels into account for visibility check
     */
    @SuppressWarnings("unchecked")
    @Redirect(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/client/ClientHooks;isBlockEntityRendererVisible(Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/client/renderer/culling/Frustum;)Z"), require = 0)
    private static <T extends BlockEntity> boolean isBlockEntityRendererVisible(final BlockEntityRenderDispatcher dispatcher, final BlockEntity blockEntity, final Frustum frustum) {
        final BlockEntityRenderer<T> renderer = (BlockEntityRenderer<T>) dispatcher.getRenderer(blockEntity);

        if (renderer == null) return false;

        AABB renderBounds = renderer.getRenderBoundingBox((T) blockEntity);

        final SubLevel subLevel = Sable.HELPER.getContainingClient(renderBounds.getCenter());

        if (subLevel != null) {
            final BoundingBox3d bb = new BoundingBox3d(renderBounds);
            renderBounds = bb.transform(subLevel.logicalPose(), bb).toMojang();
        }

        return frustum.isVisible(renderBounds);
    }
}
