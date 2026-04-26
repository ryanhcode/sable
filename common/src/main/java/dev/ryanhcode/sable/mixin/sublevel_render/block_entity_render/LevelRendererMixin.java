package dev.ryanhcode.sable.mixin.sublevel_render.block_entity_render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixinhelpers.sublevel_render.vanilla.VanillaSubLevelBlockEntityRenderer;
import dev.ryanhcode.sable.mixinterface.BlockEntityRenderDispatcherExtension;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.SubLevelRenderData;
import dev.ryanhcode.sable.sublevel.render.dispatcher.SubLevelRenderDispatcher;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.SortedSet;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    @Shadow
    @Nullable
    private ClientLevel level;

    @Shadow
    @Final
    private BlockEntityRenderDispatcher blockEntityRenderDispatcher;

    @Shadow
    @Final
    private Long2ObjectMap<SortedSet<BlockDestructionProgress>> destructionProgress;

    @Unique
    private VanillaSubLevelBlockEntityRenderer sable$subLevelBlockEntityRenderer;

    @Inject(method = "<init>", at = @At("TAIL"))
    public void init(final Minecraft minecraft, final EntityRenderDispatcher entityRenderDispatcher, final BlockEntityRenderDispatcher blockEntityRenderDispatcher, final RenderBuffers renderBuffers, final CallbackInfo ci) {
        this.sable$subLevelBlockEntityRenderer = new VanillaSubLevelBlockEntityRenderer(blockEntityRenderDispatcher, renderBuffers, this.destructionProgress);
    }

    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher;render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V", ordinal = 1))
    public <E extends BlockEntity> void sable$renderBlockEntities(final BlockEntityRenderDispatcher instance, final E blockEntity, final float pt, final PoseStack poseStack, final MultiBufferSource multiBufferSource, final Operation<Void> original, @Local(argsOnly = true) final Camera camera) {
        final ClientSubLevel subLevel = Sable.HELPER.getContainingClient(blockEntity);
        if (subLevel == null) {
            original.call(instance, blockEntity, pt, poseStack, multiBufferSource);
            return;
        }

        final BlockEntityRenderDispatcherExtension extension = (BlockEntityRenderDispatcherExtension) this.blockEntityRenderDispatcher;
        final Vec3 cameraPosition = camera.getPosition();
        final BlockPos blockPos = blockEntity.getBlockPos();

        poseStack.pushPose();
        poseStack.translate(-(blockPos.getX() - cameraPosition.x()), -(blockPos.getY() - cameraPosition.y()), -(blockPos.getZ() - cameraPosition.z())); //undo translation

        final Vector3f sableCameraPosition = new Vector3f();
        final SubLevelRenderData subLevelRenderData = subLevel.getRenderData();

        final Vector3dc invChunkOffset = subLevel.renderPose().rotationPoint();
        final Matrix4f transformation = subLevelRenderData.getTransformation(cameraPosition.x, cameraPosition.y, cameraPosition.z);

        transformation.invert(new Matrix4f()).transformPosition(sableCameraPosition.zero());
        extension.sable$setCameraPosition(new Vec3(sableCameraPosition.x + invChunkOffset.x(), sableCameraPosition.y + invChunkOffset.y(), sableCameraPosition.z + invChunkOffset.z()));

        poseStack.mulPose(transformation);
        this.sable$subLevelBlockEntityRenderer.renderSingleBE(blockEntity, poseStack, pt, invChunkOffset.x(), invChunkOffset.y(), invChunkOffset.z());

        poseStack.popPose();
        extension.sable$setCameraPosition(null);
    }

    @Inject(method = "renderLevel", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/LevelRenderer;globalBlockEntities:Ljava/util/Set;", shift = At.Shift.BEFORE, ordinal = 0))
    public void sable$preRenderBEs(final DeltaTracker deltaTracker, final boolean bl, final Camera camera, final GameRenderer gameRenderer, final LightTexture lightTexture, final Matrix4f matrix4f, final Matrix4f matrix4f2, final CallbackInfo ci) {
        final List<ClientSubLevel> subLevels = SubLevelContainer.getContainer(this.level).getAllSubLevels();
        final Vec3 cameraPosition = camera.getPosition();
        SubLevelRenderDispatcher.get().renderBlockEntities(subLevels, this.sable$subLevelBlockEntityRenderer, cameraPosition.x, cameraPosition.y, cameraPosition.z, deltaTracker.getGameTimeDeltaPartialTick(false));
    }
}