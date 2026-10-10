package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.schematics;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.schematics.client.SchematicRenderer;
import dev.ryanhcode.sable.neoforge.mixinterface.compatibility.create.schematics.SchematicLevelExtension;
import net.createmod.catnip.levelWrappers.SchematicLevel;
import net.createmod.catnip.render.ShadedBlockSbbBuilder;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin into {@link com.simibubi.create.content.schematics.client.SchematicRenderer} to render sub-level blocks in the schematic preview.
 */

@Mixin(SchematicRenderer.class)
public class SchematicRendererMixin {

    @Final
    @Shadow
    private BlockPos anchor;

    @SuppressWarnings("removal")
    @Inject(method = "drawLayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;clearCache()V", shift = At.Shift.BEFORE))
    private void sable$drawLayer(final RenderType layer,
                                 final CallbackInfoReturnable<SuperByteBuffer> cir,
                                 @Local final BlockRenderDispatcher dispatcher,
                                 @Local final ModelBlockRenderer renderer,
                                 @Local final RandomSource random,
                                 @Local final SchematicLevel mainRenderWorld,
                                 @Local final PoseStack poseStack,
                                 @Local final BlockPos.MutableBlockPos mutableBlockPos,
                                 @Local final ShadedBlockSbbBuilder sbbBuilder) {
        for (final SchematicLevelExtension.SchematicSubLevel subLevel : ((SchematicLevelExtension) mainRenderWorld).sable$getSubLevels()) {
            final SchematicLevel renderWorld = subLevel.level();
            final BoundingBox bounds = renderWorld.getBounds();
            renderWorld.renderMode = true;

            poseStack.pushPose();
            poseStack.translate(subLevel.position().x, subLevel.position().y, subLevel.position().z);
            poseStack.mulPose(new Quaternionf(subLevel.orientation()));

            for (final BlockPos localPos : BlockPos.betweenClosed(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ())) {
                final BlockPos pos = mutableBlockPos.setWithOffset(localPos, this.anchor);
                final BlockState state = renderWorld.getBlockState(pos);

                if (state.getRenderShape() == RenderShape.MODEL) {
                    final BakedModel model = dispatcher.getBlockModel(state);
                    final BlockEntity blockEntity = renderWorld.getBlockEntity(localPos);
                    ModelData modelData = blockEntity != null ? blockEntity.getModelData() : ModelData.EMPTY;
                    modelData = model.getModelData(renderWorld, pos, state, modelData);
                    final long seed = state.getSeed(pos);
                    random.setSeed(seed);
                    if (model.getRenderTypes(state, random, modelData).contains(layer)) {
                        poseStack.pushPose();
                        poseStack.translate(localPos.getX(), localPos.getY(), localPos.getZ());

                        renderer.tesselateBlock(renderWorld, model, state, pos, poseStack, sbbBuilder, true,
                                random, seed, OverlayTexture.NO_OVERLAY, modelData, layer);

                        poseStack.popPose();
                    }
                }
            }
            poseStack.popPose();
            renderWorld.renderMode = false;
        }
    }
}
