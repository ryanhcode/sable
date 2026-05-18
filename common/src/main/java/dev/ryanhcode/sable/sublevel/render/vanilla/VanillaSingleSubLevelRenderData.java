package dev.ryanhcode.sable.sublevel.render.vanilla;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.platform.SableSubLevelRenderPlatform;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.SubLevelRenderData;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.PrioritizeChunkUpdates;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.*;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A renderer and view area for a {@link dev.ryanhcode.sable.sublevel.SubLevel}.
 */
public class VanillaSingleSubLevelRenderData implements SubLevelRenderData {

    private static final RandomSource RANDOM = RandomSource.create();
    private static final SingleBlockSubLevelWrapper LEVEL_WRAPPER = new SingleBlockSubLevelWrapper();
    private static final Matrix4f TRANSFORM = new Matrix4f();
    private static final Vector3d CENTER_OF_ROT = new Vector3d();

    /**
     * The sub-level this renderer is for
     */
    private final ClientSubLevel subLevel;

    /**
     * The cached block state for single block rendering
     */
    private BlockState singleBlockState = null;

    /**
     * The cached block position for single block rendering
     */
    private BlockPos singleBlockPos = null;

    /**
     * The cached block seed for single block rendering
     */
    private long singleBlockSeed = 42L;

    /**
     * The cached block entity position for single block rendering
     */
    private BlockEntity singleBlockEntity = null;
    private boolean singleBlockEntityGlobal = false;

    /**
     * Creates a new renderer for the given sub-level
     *
     * @param subLevel the sub-level to render
     */
    public VanillaSingleSubLevelRenderData(final ClientSubLevel subLevel) {
        this.subLevel = subLevel;
        this.rebuild();
    }

    private <E extends BlockEntity> void handleBlockEntity(@Nullable final E blockEntity) {
        if (Objects.equals(this.singleBlockEntity, blockEntity)) {
            return;
        }

        if (blockEntity == null) {
            this.removeBlockEntity();
            return;
        }

        final BlockEntityRenderer<E> blockEntityRenderer = Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(blockEntity);
        if (blockEntityRenderer == null) {
            this.removeBlockEntity();
            return;
        }

        this.singleBlockEntity = blockEntity;
        this.singleBlockEntityGlobal = blockEntityRenderer.shouldRenderOffScreen(blockEntity);
    }

    private void removeBlockEntity() {
        if (this.singleBlockEntity != null && this.singleBlockEntityGlobal) {
            Minecraft.getInstance().levelRenderer.updateGlobalBlockEntities(Set.of(this.singleBlockEntity), Set.of());
        }
        this.singleBlockEntity = null;
        this.singleBlockEntityGlobal = false;
    }

    public void renderSingleBlock(final RenderType layer, final VertexConsumer consumer, final Matrix4f modelView, final double camX, final double camY, final double camZ) {
        final Minecraft client = Minecraft.getInstance();
        if (this.singleBlockState.isAir()) {
            this.rebuild();
        }

        if (this.singleBlockState.getRenderShape() != RenderShape.MODEL) {
            return;
        }

        final BakedModel bakedModel = client.getBlockRenderer().getBlockModel(this.singleBlockState);
        final Pose3dc renderPose = this.subLevel.renderPose();
        final Vector3dc renderPos = renderPose.position();
        LEVEL_WRAPPER.setup(this.subLevel.getLevel(), renderPos.x(), renderPos.y(), renderPos.z(), this.singleBlockPos, this.singleBlockState);

        RANDOM.setSeed(this.singleBlockSeed);
        final List<RenderType> renderLayers = SableSubLevelRenderPlatform.INSTANCE.getRenderLayers(LEVEL_WRAPPER, bakedModel, this.singleBlockState, this.singleBlockPos, RANDOM);
        if (!renderLayers.contains(layer)) {
            LEVEL_WRAPPER.clear();
            return;
        }

        final PoseStack stack = new PoseStack();

        // These NEED to be here because renderPos is mutated below
        final double renderX = renderPos.x();
        final double renderY = renderPos.y();
        final double renderZ = renderPos.z();
        {
            final Quaterniondc renderRot = renderPose.orientation();
            final Vector3d renderCOR = renderRot.transform(CENTER_OF_ROT.set(renderPose.rotationPoint()).sub(this.singleBlockPos.getX(), this.singleBlockPos.getY(), this.singleBlockPos.getZ()));

            renderCOR.negate().add(renderX, renderY, renderZ);

            final Matrix4f transform = TRANSFORM.identity();

            // convert the camera pos to local to the origin / rotated
            transform.translate((float) (renderCOR.x() - camX), (float) (renderCOR.y() - camY), (float) (renderCOR.z() - camZ));
            transform.rotate(new Quaternionf(renderRot));

            stack.last().pose().mul(modelView).mul(transform);
            transform.normal(stack.last().normal());
        }

        SableSubLevelRenderPlatform.INSTANCE.tesselateBlock(LEVEL_WRAPPER, bakedModel, this.singleBlockState, this.singleBlockPos, stack, consumer, RANDOM, this.singleBlockSeed, OverlayTexture.NO_OVERLAY, layer);
        LEVEL_WRAPPER.clear();
    }

    public @Nullable BlockEntity getRenderBlockEntity() {
        if (this.singleBlockState.isAir()) {
            this.rebuild();
        }
        return this.singleBlockEntity;
    }

    @Override
    public void rebuild() {
        final BoundingBox3ic bounds = this.subLevel.getPlot().getBoundingBox();
        final BlockPos pos = new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ());

        final BlockState blockState = this.subLevel.getLevel().getBlockState(pos);

        this.singleBlockState = blockState;
        this.singleBlockPos = pos;
        this.singleBlockSeed = blockState.getSeed(pos);

        this.handleBlockEntity(blockState.hasBlockEntity() ? this.subLevel.getLevel().getBlockEntity(pos) : null);

        if (this.singleBlockEntity != null) {
            SableSubLevelRenderPlatform.INSTANCE.tryAddFlywheelVisual(this.singleBlockEntity);
        }
    }

    @Override
    public void compileSections(final PrioritizeChunkUpdates chunkUpdates, final RenderRegionCache renderRegionCache, final Camera camera) {
    }

    @Override
    public int getVisibleSectionCount() {
        return 1;
    }

    @Override
    public ClientSubLevel getSubLevel() {
        return this.subLevel;
    }

    @Override
    public void setDirty(final int x, final int y, final int z, final boolean playerChanged) {
        this.rebuild();
    }

    @Override
    public boolean isSectionCompiled(final int x, final int y, final int z) {
        return true;
    }

    @Override
    public void close() {
        this.removeBlockEntity();
    }
}
