package dev.ryanhcode.sable.platform;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.sublevel.render.vanilla.SingleBlockSubLevelWrapper;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@ApiStatus.Internal
public interface SableSubLevelRenderPlatform {
    SableSubLevelRenderPlatform INSTANCE = SablePlatformUtil.load(SableSubLevelRenderPlatform.class);

    void tesselateBlock(
            final SingleBlockSubLevelWrapper blockAndTintGetter,
            final BakedModel bakedModel,
            final BlockState blockState,
            final BlockPos pos,
            final PoseStack poseStack,
            final VertexConsumer vertexConsumer,
            final RandomSource randomSource,
            final long seed,
            final int packedOverlay,
            final @Nullable RenderType renderType);

    List<RenderType> getRenderLayers(
            final SingleBlockSubLevelWrapper blockAndTintGetter,
            final BakedModel bakedModel,
            final BlockState blockState,
            final BlockPos pos,
            final RandomSource randomSource);

    void tryAddFlywheelVisual(final BlockEntity blockEntity);
}
