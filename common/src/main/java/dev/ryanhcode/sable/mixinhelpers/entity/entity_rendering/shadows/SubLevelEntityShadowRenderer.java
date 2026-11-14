package dev.ryanhcode.sable.mixinhelpers.entity.entity_rendering.shadows;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.*;

import java.lang.Math;

public class SubLevelEntityShadowRenderer {

    /**
     * Shadow inflation factor above blocks
     */
    public static final double INFLATION = 1.01;

    private static final Direction[] DIRECTIONS = Direction.values();
    private static final Vector3d CENTER = new Vector3d();
    private static final Vector3d ENTITY_RELATIVE_CENTER = new Vector3d();
    private static final Vector3d NORMAL = new Vector3d();
    private static final Vector3d LOCAL_POS = new Vector3d();
    private static final Vector3d ENTITY_LOCAL_POS = new Vector3d();
    private static final Vector3f RENDER_POSITION = new Vector3f();
    private static final BoundingBox3d BOUNDS = new BoundingBox3d();
    private static final BlockPos.MutableBlockPos TEMP = new BlockPos.MutableBlockPos();

    private static final Vector3d[] CORNERS = new Vector3d[]{
            new Vector3d(),
            new Vector3d(),
            new Vector3d(),
            new Vector3d()
    };
    private static final Vector3d[] REVERSE_CORNERS = new Vector3d[]{
            CORNERS[3],
            CORNERS[2],
            CORNERS[1],
            CORNERS[0]
    };

    /**
     * Renders the shadows of entities on sub-levels.
     */
    public static void renderEntityShadowOnSubLevels(final Entity entity,
                                                     final float f,
                                                     final float partialTick,
                                                     final float shadowRadius,
                                                     final VertexConsumer vertexConsumer,
                                                     final PoseStack.Pose pose) {
        final Quaterniondc customOrientation = EntitySubLevelUtil.getCustomEntityOrientation(entity, partialTick);
        final Vec3 entityOrigin = entity.getPosition(partialTick);
        Vec3 entityFeet = entityOrigin;
        Vector3dc upDir = OrientedBoundingBox3d.UNIT_Y;

        final Vec3 eyePos = entity.getEyePosition(partialTick);
        if (customOrientation != null) {
            entityFeet = eyePos.subtract(JOMLConversion.toMojang(customOrientation.transform(new Vector3d(0.0, entity.getEyeHeight(), 0.0))));
            upDir = customOrientation.transform(new Vector3d(upDir));
        }

        final Level level = entity.level();
        final float shadowHeight = Math.min(f / 0.5F, shadowRadius) * 3.0f; // TODO: Why 3.0?

        final BoundingBox3d bounds = new BoundingBox3d(
                entityFeet.x - shadowRadius,
                entityFeet.y - shadowHeight,
                entityFeet.z - shadowRadius,
                entityFeet.x + shadowRadius,
                entityFeet.y + 0.2,
                entityFeet.z + shadowRadius
        );

        final BoundingBox3d localBounds = new BoundingBox3d();

        if (customOrientation != null) {
            bounds.transform(new Matrix4d()
                    .translate(entityFeet.x, entityFeet.y, entityFeet.z)
                    .rotate(customOrientation)
                    .translate(-entityFeet.x, -entityFeet.y, -entityFeet.z), bounds);
        }

        final Iterable<SubLevel> intersecting = Sable.HELPER.getAllIntersecting(level, bounds);
        for (final SubLevel subLevel : intersecting) {
            final Pose3dc renderPose = ((ClientSubLevel) subLevel).renderPose();
            bounds.transformInverse(renderPose, localBounds);

            for (final BlockPos subLevelBlockPos : BlockPos.betweenClosed(
                    Mth.floor(localBounds.minX),
                    Mth.floor(localBounds.minY),
                    Mth.floor(localBounds.minZ),
                    Mth.floor(localBounds.maxX),
                    Mth.floor(localBounds.maxY),
                    Mth.floor(localBounds.maxZ)
            )) {
                final BlockState blockState = level.getBlockState(subLevelBlockPos);

                if (blockState.getRenderShape() == RenderShape.INVISIBLE || level.getMaxLocalRawBrightness(entity.blockPosition()) <= 3) {
                    continue;
                }

                if (!blockState.isCollisionShapeFullBlock(level, subLevelBlockPos)) {
                    continue;
                }

                final VoxelShape voxelShape = blockState.getShape(level, subLevelBlockPos);
                if (voxelShape.isEmpty()) {
                    continue;
                }

                final float light = LightTexture.getBrightness(level.dimensionType(), level.getMaxLocalRawBrightness(entity.blockPosition()));

                // render the shadows for the faces of this voxelshape
                final BoundingBox3d shapeBounds = BOUNDS.set(voxelShape.bounds()).move(subLevelBlockPos.getX(), subLevelBlockPos.getY(), subLevelBlockPos.getZ(), BOUNDS);
                final Vector3d center = shapeBounds.center(CENTER);
                final double centerX = center.x;
                final double centerY = center.y;
                final double centerZ = center.z;
                renderPose.transformPosition(center);

                for (final Direction direction : DIRECTIONS) {
                    // if there's another full block collision shape block in the same direction, stop
                    final BlockPos offset = TEMP.setWithOffset(subLevelBlockPos, direction);
                    final BlockState offsetState = level.getBlockState(offset);
                    if (offsetState.getRenderShape() != RenderShape.INVISIBLE && offsetState.isCollisionShapeFullBlock(level, offset)) {
                        continue;
                    }

                    if (renderPose.transformNormal(JOMLConversion.atLowerCornerOf(direction.getNormal(), NORMAL)).dot(upDir) < 0.6) {
                        continue;
                    }

                    if (center.sub(entityFeet.x, entityFeet.y, entityFeet.z, ENTITY_RELATIVE_CENTER).dot(NORMAL) >= 0.0) {
                        continue;
                    }

                    final double xHalfExtent = (shapeBounds.maxX - shapeBounds.minX) / 2.0;
                    final double zHalfExtent = (shapeBounds.maxZ - shapeBounds.minZ) / 2.0;
                    final double yHalfExtent = (shapeBounds.maxY - shapeBounds.minY) / 2.0;

                    if (direction.getAxis() == Direction.Axis.Y) {
                        final double yStep = direction.getStepY() * INFLATION;
                        CORNERS[0].set(centerX - xHalfExtent, centerY + yStep * yHalfExtent, centerZ + zHalfExtent);
                        CORNERS[1].set(centerX + xHalfExtent, centerY + yStep * yHalfExtent, centerZ + zHalfExtent);
                        CORNERS[2].set(centerX + xHalfExtent, centerY + yStep * yHalfExtent, centerZ - zHalfExtent);
                        CORNERS[3].set(centerX - xHalfExtent, centerY + yStep * yHalfExtent, centerZ - zHalfExtent);
                    } else if (direction.getAxis() == Direction.Axis.X) {
                        final double xStep = direction.getStepX() * INFLATION;
                        CORNERS[0].set(centerX + xStep * xHalfExtent, centerY + yHalfExtent, centerZ + zHalfExtent);
                        CORNERS[1].set(centerX + xStep * xHalfExtent, centerY - yHalfExtent, centerZ + zHalfExtent);
                        CORNERS[2].set(centerX + xStep * xHalfExtent, centerY - yHalfExtent, centerZ - zHalfExtent);
                        CORNERS[3].set(centerX + xStep * xHalfExtent, centerY + yHalfExtent, centerZ - zHalfExtent);
                    } else if (direction.getAxis() == Direction.Axis.Z) {
                        final double zStep = direction.getStepZ() * INFLATION;
                        CORNERS[0].set(centerX + xHalfExtent, centerY + yHalfExtent, centerZ + zStep * zHalfExtent);
                        CORNERS[1].set(centerX - xHalfExtent, centerY + yHalfExtent, centerZ + zStep * zHalfExtent);
                        CORNERS[2].set(centerX - xHalfExtent, centerY - yHalfExtent, centerZ + zStep * zHalfExtent);
                        CORNERS[3].set(centerX + xHalfExtent, centerY - yHalfExtent, centerZ + zStep * zHalfExtent);
                    }

                    // Reverse the order of the corners for negative directions, for flipping the quad
                    final Vector3dc[] corners = switch (direction.getAxisDirection()) {
                        case POSITIVE -> CORNERS;
                        case NEGATIVE -> REVERSE_CORNERS;
                    };

                    for (final Vector3dc corner : corners) {
                        renderPose.transformPosition(corner, LOCAL_POS).sub(entityFeet.x, entityFeet.y, entityFeet.z);

                        final Vector3d entityLocalPos = ENTITY_LOCAL_POS.set(LOCAL_POS);
                        if (customOrientation != null)
                            customOrientation.transformInverse(entityLocalPos);

                        final double yDiff = entityLocalPos.y;
                        final int alpha = Mth.floor((float) Math.max(0.0, (f - (float) -yDiff * 0.5F) * 0.5F * light) * 255.0F);

                        LOCAL_POS.add(entityFeet.x - entityOrigin.x, entityFeet.y - entityOrigin.y, entityFeet.z - entityOrigin.z);

                        shadowVertex(pose,
                                vertexConsumer,
                                alpha << 24 | 0xFFFFFF,
                                (float) LOCAL_POS.x,
                                (float) LOCAL_POS.y,
                                (float) LOCAL_POS.z,
                                (float) ((entityLocalPos.x + shadowRadius) / (shadowRadius * 2.0F)),
                                (float) ((entityLocalPos.z + shadowRadius) / (shadowRadius * 2.0F)));
                    }
                }
            }
        }
    }

    private static void shadowVertex(final PoseStack.Pose pose, final VertexConsumer vertexConsumer, final int i, final float f, final float g, final float h, final float j, final float k) {
        final Vector3f vector3f = pose.pose().transformPosition(f, g, h, RENDER_POSITION);
        vertexConsumer.addVertex(vector3f.x(), vector3f.y(), vector3f.z(), i, j, k, OverlayTexture.NO_OVERLAY, LightTexture.FULL_BRIGHT, 0.0F, 1.0F, 0.0F);
    }
}
