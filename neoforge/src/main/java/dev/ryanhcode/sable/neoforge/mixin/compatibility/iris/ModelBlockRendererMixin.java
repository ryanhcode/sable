package dev.ryanhcode.sable.neoforge.mixin.compatibility.iris;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import net.irisshaders.iris.vertices.BlockSensitiveBufferBuilder;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelBlockRenderer.class)
public class ModelBlockRendererMixin {

    @Unique
    private void sable$beginBlock(final BlockState state, final BlockPos pos, final VertexConsumer consumer) {
        if (!(consumer instanceof BlockSensitiveBufferBuilder buffer)) {
            return;
        }

        final Object2IntMap<BlockState> blockStateIds = WorldRenderingSettings.INSTANCE.getBlockStateIds();
        if (blockStateIds == null) {
            return;
        }

        buffer.beginBlock(blockStateIds.getOrDefault(state, -1), (byte) 0, (byte) state.getLightEmission(), pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
    }

    @Unique
    private void sable$endBlock(final VertexConsumer consumer) {
        if (consumer instanceof BlockSensitiveBufferBuilder buffer) {
            buffer.endBlock();
        }
    }

    @Inject(method = "tesselateWithAO(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;JILnet/neoforged/neoforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V", at = @At("HEAD"))
    private void sable$beginBlockAO(final BlockAndTintGetter level, final BakedModel model, final BlockState state, final BlockPos pos, final PoseStack poseStack, final VertexConsumer consumer, final boolean checkSides, final RandomSource random, final long seed, final int packedOverlay, final ModelData modelData, final RenderType renderType, final CallbackInfo ci) {
        this.sable$beginBlock(state, pos, consumer);
    }

    @Inject(method = "tesselateWithAO(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;JILnet/neoforged/neoforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V", at = @At("RETURN"))
    private void sable$endBlockAO(final BlockAndTintGetter level, final BakedModel model, final BlockState state, final BlockPos pos, final PoseStack poseStack, final VertexConsumer consumer, final boolean checkSides, final RandomSource random, final long seed, final int packedOverlay, final ModelData modelData, final RenderType renderType, final CallbackInfo ci) {
        this.sable$endBlock(consumer);
    }

    @Inject(method = "tesselateWithoutAO(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;JILnet/neoforged/neoforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V", at = @At("HEAD"))
    private void sable$beginBlockFlat(final BlockAndTintGetter level, final BakedModel model, final BlockState state, final BlockPos pos, final PoseStack poseStack, final VertexConsumer consumer, final boolean checkSides, final RandomSource random, final long seed, final int packedOverlay, final ModelData modelData, final RenderType renderType, final CallbackInfo ci) {
        this.sable$beginBlock(state, pos, consumer);
    }

    @Inject(method = "tesselateWithoutAO(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;JILnet/neoforged/neoforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V", at = @At("RETURN"))
    private void sable$endBlockFlat(final BlockAndTintGetter level, final BakedModel model, final BlockState state, final BlockPos pos, final PoseStack poseStack, final VertexConsumer consumer, final boolean checkSides, final RandomSource random, final long seed, final int packedOverlay, final ModelData modelData, final RenderType renderType, final CallbackInfo ci) {
        this.sable$endBlock(consumer);
    }
}
