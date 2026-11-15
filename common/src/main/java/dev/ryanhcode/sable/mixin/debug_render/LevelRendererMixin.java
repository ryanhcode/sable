package dev.ryanhcode.sable.mixin.debug_render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.network.client.ClientSableInterpolationState;
import dev.ryanhcode.sable.network.client.SubLevelSnapshotInterpolator;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into {@link net.minecraft.client.renderer.LevelRenderer} to render sub-level bounding boxes and poses when hitboxes are shown.
 */
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    @Shadow
    private ClientLevel level;

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void renderLevel(final DeltaTracker deltaTracker, final boolean bl, final Camera camera, final GameRenderer gameRenderer, final LightTexture lightTexture, final Matrix4f matrix4f, final Matrix4f matrix4f2, final CallbackInfo ci) {
        final Minecraft minecraft = Minecraft.getInstance();

        if (!minecraft.getEntityRenderDispatcher().shouldRenderHitBoxes() || Minecraft.getInstance().showOnlyReducedInfo()) {
            return;
        }

        final SubLevelContainer container = SubLevelContainer.getContainer(this.level);

        final MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        final VertexConsumer consumer = bufferSource.getBuffer(RenderType.LINES);

        final double cx = camera.getPosition().x;
        final double cy = camera.getPosition().y;
        final double cz = camera.getPosition().z;

        final PoseStack ps = new PoseStack();
        ps.mulPose(matrix4f);

        for (final SubLevel subLevel : container.getAllSubLevels()) {
            final BoundingBox3dc bounds = subLevel.boundingBox();

            LevelRenderer.renderLineBox(
                    ps,
                    consumer,
                    bounds.minX() - cx,
                    bounds.minY() - cy,
                    bounds.minZ() - cz,
                    bounds.maxX() - cx,
                    bounds.maxY() - cy,
                    bounds.maxZ() - cz,
                    0.5f, 0.5f, 0.5f, 0.7f
            );

            ps.pushPose();
            final Pose3dc renderPose = ((ClientSubLevel) subLevel).renderPose();
            final BoundingBox3ic plotBounds = subLevel.getPlot().getBoundingBox();

            final Vector3dc globalCenter = renderPose.position();
            final Vector3dc localCenter = renderPose.rotationPoint();

            ps.translate(globalCenter.x() - cx, globalCenter.y() - cy, globalCenter.z() - cz);
            ps.mulPose(new Quaternionf(renderPose.orientation()));

            LevelRenderer.renderLineBox(
                    ps,
                    consumer,
                    -2.0f / 16.0f,
                    -2.0f / 16.0f,
                    -2.0f / 16.0f,
                    2.0f / 16.0f,
                    2.0f / 16.0f,
                    2.0f / 16.0f,
                    0.7f, 0.7f, 0.5f, 1.0f
            );

            LevelRenderer.renderLineBox(
                    ps,
                    consumer,
                    plotBounds.minX() - localCenter.x(),
                    plotBounds.minY() - localCenter.y(),
                    plotBounds.minZ() - localCenter.z(),
                    plotBounds.maxX() + 1.0 - localCenter.x(),
                    plotBounds.maxY() + 1.0 - localCenter.y(),
                    plotBounds.maxZ() + 1.0 - localCenter.z(),
                    0.9f, 0.5f, 0.5f, 1.0f
            );
            ps.popPose();

            if (ClientSableInterpolationState.RENDER_INTERPOLATION_BOUNDS) {
                final Vector3d boundSize = bounds.size(new Vector3d());
                final SubLevelSnapshotInterpolator interpolator = ((ClientSubLevel) subLevel).getInterpolator();
                for (final SubLevelSnapshotInterpolator.Snapshot buffer : interpolator.buffer) {
                    final Pose3dc pose = buffer.pose();

                    LevelRenderer.renderLineBox(
                            ps,
                            consumer,
                            pose.position().x() - boundSize.x() / 2.0 - cx,
                            pose.position().y() - boundSize.y() / 2.0 - cy,
                            pose.position().z() - boundSize.z() / 2.0 - cz,
                            pose.position().x() + boundSize.x() / 2.0 - cx,
                            pose.position().y() + boundSize.y() / 2.0 - cy,
                            pose.position().z() + boundSize.z() / 2.0 - cz,
                            0.0f, 1.0f, 1.0f, 0.5f
                    );
                }
            }
        }

        bufferSource.endLastBatch();
    }
}