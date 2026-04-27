package dev.ryanhcode.sable.sublevel.entity_collision;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.api.math.LevelReusedVectors;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import dev.ryanhcode.sable.companion.math.*;
import dev.ryanhcode.sable.index.SableTags;
import dev.ryanhcode.sable.mixinterface.EntityExtension;
import dev.ryanhcode.sable.mixinterface.voxel_shape_iteration.FastVoxelShapeIterable;
import dev.ryanhcode.sable.physics.impl.SubLevelEntityCollisionContext;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.util.LevelAccelerator;
import dev.ryanhcode.sable.util.SableMathUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.joml.*;

import java.lang.Math;
import java.util.Iterator;
import java.util.Map;

/**
 * Handles collision between an {@link Entity} and {@link SubLevel SubLevels}
 */
public class SubLevelEntityCollision {

    /**
     * Handles collision between an {@link Entity} and {@link SubLevel SubLevels}
     * <p>
     * cursed as hell but we ball
     *
     * @param entity             The entity to collide
     * @param collisionMotionMoj The motion of the entity
     * @return The new motion of the entity after collision
     */
    public static CollisionInfo collide(final Entity entity, final Vec3 collisionMotionMoj, final Vec3 velocityMotionMoj, final LevelReusedVectors sink) {
        if (entity instanceof ServerPlayer) {
            final CollisionInfo collisionInfo = new CollisionInfo();
            collisionInfo.motion = collisionMotionMoj;

            final SubLevel trackingSubLevel = Sable.HELPER.getTrackingSubLevel(entity);
            if (trackingSubLevel != null) {
                entity.setOnGround(true);
                collisionInfo.verticalCollisionBelow = true;
                collisionInfo.verticalCollision = true;
                collisionInfo.trackingSubLevel = trackingSubLevel;
                if (entity.getDeltaMovement().y < 0) {
                    entity.setDeltaMovement(entity.getDeltaMovement().multiply(1.0, 0.0, 1.0));
                }
            }
            return collisionInfo;
        }

        final SubLevel existingTrackingSubLevel = Sable.HELPER.getTrackingSubLevel(entity);

        if (existingTrackingSubLevel != null &&
                EntitySubLevelUtil.shouldKick(entity) &&
                existingTrackingSubLevel.getPlot().contains(entity.position())) {
            EntitySubLevelUtil.kickEntity(existingTrackingSubLevel, entity);
        }

        final BoundingBox3d fullContextBounds = sink.fullContextBounds.set(entity.getBoundingBox().minmax(entity.getBoundingBox().move(collisionMotionMoj)));
        final BoundingBox3d rotatedContextBounds = sink.rotatedContextBounds;
        final AABB entityBounds = entity.getBoundingBox();

        final Vector3d collisionMotion = sink.collisionMotion.set(collisionMotionMoj.x, collisionMotionMoj.y, collisionMotionMoj.z);
        final Vector3d velocityMotion = sink.velocityMotion.set(velocityMotionMoj.x, velocityMotionMoj.y, velocityMotionMoj.z);

        final Level level = entity.level();
        final LevelAccelerator accel = new LevelAccelerator(level);

        Quaterniondc customEntityOrientation = EntitySubLevelUtil.getCustomEntityOrientation(entity, 0.0f);
        sink.entityUpDirection.set(OrientedBoundingBox3d.UP);

        final BoundingBox3d considerationBounds = sink.considerationBounds.set(fullContextBounds);

        if (customEntityOrientation != null) {
            customEntityOrientation.transform(sink.entityUpDirection);
            considerationBounds.expand(entity.getEyeHeight()); // just in case tm
        }
        considerationBounds.expand(1.0); // fences

        final ObjectSet<SubLevel> intersecting = new ObjectOpenHashSet<>();

        for (final SubLevel subLevel : Sable.HELPER.getAllIntersecting(level, considerationBounds)) {
            intersecting.add(subLevel);
        }

        final CollisionInfo collisionInfo = new CollisionInfo();
        collisionInfo.trackingSubLevel = existingTrackingSubLevel;

        if (collisionInfo.trackingSubLevel != null) {
            intersecting.add(collisionInfo.trackingSubLevel);
        }

        if (!intersecting.iterator().hasNext()) {
            collisionInfo.motion = collisionMotionMoj.add(velocityMotionMoj);
            return collisionInfo;
        }

        final BoundingBox3d localBounds = sink.localBounds;
        final BoundingBox3d localBounds2 = sink.localBounds2;

        int substeps = Math.min(10, Math.max(1, (int) (collisionMotion.length() / (0.25 / 16.0))));

        if (entity instanceof final Player player && player.isLocalPlayer()) {
            substeps = 8;
        }

        final Vec3 originalEntityPosition = entity.position();
        final Vector3dc originalEntityFootPosition = Sable.HELPER.getFeetPos(entity, 0.0f, customEntityOrientation);

        final Vector3d entityBoundsCenter = JOMLConversion.getAABBCenter(entityBounds, sink.entityBoundsCenter);
        transformEntityBoundsCenter(sink, customEntityOrientation, entity, entityBoundsCenter);

        sink.entityBoxOrientation.identity();
        final OrientedBoundingBox3d entityBoundsOBB = new OrientedBoundingBox3d(
                entityBoundsCenter.x + collisionMotion.x + velocityMotion.x,
                entityBoundsCenter.y + collisionMotion.y + velocityMotion.y,
                entityBoundsCenter.z + collisionMotion.z + velocityMotion.z,
                entityBounds.getXsize(),
                entityBounds.getYsize(),
                entityBounds.getZsize(),
                sink.entityBoxOrientation,
                sink);

        final OrientedBoundingBox3d cubeOBB = new OrientedBoundingBox3d(sink);

        final Pose3d lastPose = sink.lastPose;
        final Pose3d lastSubLevelPose = sink.lastSubLevelPose;
        final Pose3d subLevelPose = sink.subLevelPose;
        final Matrix4d bakedMatrix = sink.bakedMatrix;

        final Vector3d mtv = sink.mtv;
        final Vector3d normalizedMtv = sink.normalizedMtv;
        final Vector3d existingDeltaMovement = sink.existingDeltaMovement;
        final Vector3d maxMTV = sink.maxMTV;
        final BoundingBox3d maxAABB = sink.maxAABB;
        final Vector3d center = sink.center;

        collisionMotion.zero();

        final Vector3dc steppingMotion = JOMLConversion.toJOML(collisionMotionMoj);
        final Vector3dc steppingVelocityMotion = JOMLConversion.toJOML(velocityMotionMoj);

        boolean swappedTrackingAlready = false; // prevent flickering between tracking 2 sub-levels
        boolean stopTrackingAtEnd = false;

        final Map<SubLevel, FirstCollisionInfo> firstCollisions = new Object2ObjectArrayMap<>();

        for (int i = 1; i <= substeps; i++) {
            final double delta = 1.0 / substeps;
            collisionMotion.fma(delta, steppingMotion);

            if (collisionInfo.trackingSubLevel == null) {
                collisionMotion.fma(delta, steppingVelocityMotion);
            }

            // Entity box handling
            sink.entityBoxOrientation.identity();
            final double yaw = getHitBoxYaw(subLevelPose);
            sink.entityBoxOrientation.rotateY(yaw);

            final Vector3d entityUp = sink.entityUpDirection;

            if (customEntityOrientation != null) {
                entityBoundsCenter.fma(entity.getEyeHeight() - entity.getBoundingBox().getYsize() / 2.0, entityUp);
                customEntityOrientation = EntitySubLevelUtil.getCustomEntityOrientation(entity, (float) i / substeps);

                entityUp.set(OrientedBoundingBox3d.UP);
                transformEntityBoundingBox(customEntityOrientation, sink.entityBoxOrientation, entityUp);
                entityBoundsCenter.fma(-(entity.getEyeHeight() - entity.getBoundingBox().getYsize() / 2.0), entityUp);
            } else {
                entityUp.set(OrientedBoundingBox3d.UP);
            }
            entityBoundsOBB.setOrientation(sink.entityBoxOrientation);

            entityBoundsCenter.add(collisionMotion, entityBoundsOBB.getPosition());

            // iterate through all sub-levels that COULD intersect
            for (final SubLevel subLevel : intersecting) {
                if (Sable.HELPER.getVehicleSubLevel(entity) == subLevel) {
                    continue;
                }

                final Pose3d logicalPose = subLevel.logicalPose();

                lastPose.set(subLevel.lastPose());
                if (lastPose.rotationPoint().lengthSquared() <= 0.0) {
                    lastPose.rotationPoint().set(logicalPose.rotationPoint());
                }

                lastPose.lerp(logicalPose, (double) (i - 1) / substeps, lastSubLevelPose);
                lastPose.lerp(logicalPose, (double) (i) / substeps, subLevelPose);

                rotatedContextBounds.set(fullContextBounds);
                if (customEntityOrientation != null) {
                    entityBoundsOBB.vertices(sink.a);

                    for (final Vector3d vec : sink.a) {
                        rotatedContextBounds.expandTo(vec);
                        rotatedContextBounds.expandTo(vec.sub(collisionMotion.x, collisionMotion.y, collisionMotion.z));
                    }

                    rotatedContextBounds.expand(0.35f);
                }
                rotatedContextBounds.transformInverse(lastPose, bakedMatrix, localBounds);
                rotatedContextBounds.transformInverse(logicalPose, bakedMatrix, localBounds2);

                localBounds.expandTo(localBounds2, localBounds);

                if (localBounds.volume() > 500 * 500 * 500) {
                    Sable.LOGGER.info("Enormous local sub-level collision bounds, quitting.");
                    continue;
                }

                // We subtract 1 from y to allow for fences and similar blocks
                final Iterable<BlockPos> blocks = BlockPos.betweenClosed(sink.minPos.set(localBounds.minX, localBounds.minY - 1, localBounds.minZ), sink.maxPos.set(localBounds.maxX, localBounds.maxY, localBounds.maxZ));

                cubeOBB.getOrientation().set(subLevelPose.orientation());

                if (collisionInfo.trackingSubLevel == subLevel) {
                    // Subtract, then add so the vector can be re-used
                    final float verticalAnchorPosition = 0;

                    final Vector3dc feetOffset = entityUp.mul(verticalAnchorPosition - entity.getBoundingBox().getYsize() / 2.0, sink.posMinusCenter);
                    sink.trackingPosition.set(entityBoundsCenter).add(feetOffset);
                    subLevelPose.transformPosition(lastSubLevelPose.transformPositionInverse(sink.trackingPosition)).sub(feetOffset, entityBoundsCenter);
                    entityBoundsCenter.add(collisionMotion, entityBoundsOBB.getPosition());
                    entityBoundsCenter.fma(verticalAnchorPosition - entity.getBoundingBox().getYsize() / 2.0, entityUp, sink.tempEyePosition).sub(0.0, verticalAnchorPosition, 0.0);
                    ((EntityExtension) entity).sable$setPosSuperRaw(new Vec3(sink.tempEyePosition.x, sink.tempEyePosition.y, sink.tempEyePosition.z));

                    boolean anySurroundingBlocksSolid = false;

                    for (final BlockPos block : blocks) {
                        if (!accel.getBlockState(block).isAir()) {
                            anySurroundingBlocksSolid = true;
                            break;
                        }
                    }

                    if (!anySurroundingBlocksSolid) {
                        stopTrackingAtEnd = true;
                    }
                }

                for (int maxIter = 0; maxIter < 4; maxIter++) {
                    mtv.set(Double.MAX_VALUE);
                    maxMTV.zero();
                    double maxMTVLength = Double.MIN_VALUE;
                    final BlockPos.MutableBlockPos maxBlockPos = sink.maxBlockPos;
                    BlockState maxBlockState = null;

                    // iterate through all blocks
                    for (final BlockPos block : blocks) {
                        final BlockState state = accel.getBlockState(block);
                        final VoxelShape voxelShape = getSubLevelEntityCollisionShape(entity, entityBoundsCenter, subLevelPose, state, accel, block, sink);

                        if (state.isAir()) {
                            continue;
                        }

                        final Iterator<BoundingBox3dc> iterator = ((FastVoxelShapeIterable) voxelShape).sable$allBoxes();
                        while (iterator.hasNext()) {
                            final BoundingBox3dc box = iterator.next();
                            box.center(center);
                            cubeOBB.getPosition().set(block.getX() + center.x,
                                    block.getY() + center.y,
                                    block.getZ() + center.z);
                            subLevelPose.transformPosition(cubeOBB.getPosition());
                            box.size(cubeOBB.getDimensions());

                            OrientedBoundingBox3d.sat(entityBoundsOBB, cubeOBB, mtv);

                            if (mtv.lengthSquared() > 0.0 && mtv.x != Double.MAX_VALUE && mtv.y != Double.MAX_VALUE && mtv.z != Double.MAX_VALUE) {
                                final double lengthMtv = mtv.lengthSquared();
                                if (lengthMtv > maxMTVLength) {
                                    maxMTVLength = lengthMtv;
                                    maxMTV.set(mtv);

                                    box.move(block.getX(), block.getY(), block.getZ(), maxAABB);
                                    maxBlockPos.set(block);
                                    maxBlockState = state;
                                }
                            }
                        }
                    }

                    if (maxMTV.lengthSquared() > 0.0) {
                        // start tracking
                        if (collisionInfo.trackingSubLevel == null) {
                            collisionInfo.trackingSubLevel = subLevel;
//                            collisionInfo.trackingLocalUpDirection = subLevelPose.transformNormalInverse(new Vector3d(0.0, 1.0, 0.0));
                            stopTrackingAtEnd = false;
                        }

                        final Vector3dc localMtv = subLevelPose.transformNormalInverse(maxMTV, sink.localMtv).normalize();

                        final int offsetX = (int) Math.round(localMtv.x());
                        final int offsetY = (int) Math.round(localMtv.y());
                        final int offsetZ = (int) Math.round(localMtv.z());
                        final BlockPos newPos = sink.offsetPos.setWithOffset(maxBlockPos, offsetX, offsetY, offsetZ);
                        final BlockState offsetState = accel.getBlockState(newPos);
                        final VoxelShape offsetShape = getSubLevelEntityCollisionShape(entity, entityBoundsCenter, subLevelPose, offsetState, accel, newPos, sink);
                        final Direction direction = Direction.get(Direction.AxisDirection.POSITIVE, Direction.getNearest(offsetX, offsetY, offsetZ).getAxis());

                        final BoundingBox3d offsetAABB = sink.offsetAABB;
                        final BoundingBox3d compressedMinAABB = sink.compressedMinAABB;
                        final BoundingBox3d compressedOffsetAABB = sink.compressedOffsetAABB;
                        final BoundingBox3d intersection = sink.intersection;

                        boolean discard = false;
                        final Iterator<BoundingBox3dc> iterator = ((FastVoxelShapeIterable) offsetShape).sable$allBoxes();
                        while (iterator.hasNext()) {
                            final BoundingBox3dc box = iterator.next();
                            box.move(newPos.getX(), newPos.getY(), newPos.getZ(), offsetAABB).expand(0.001);
                            if (!maxAABB.intersects(offsetAABB)) {
                                continue;
                            }

                            compressedMinAABB.set(
                                    maxAABB.minX * (1.0 - direction.getStepX()),
                                    maxAABB.minY * (1.0 - direction.getStepY()),
                                    maxAABB.minZ * (1.0 - direction.getStepZ()),
                                    maxAABB.maxX * (1.0 - direction.getStepX()) + direction.getStepX(),
                                    maxAABB.maxY * (1.0 - direction.getStepY()) + direction.getStepY(),
                                    maxAABB.maxZ * (1.0 - direction.getStepZ()) + direction.getStepZ()
                            );

                            compressedOffsetAABB.set(
                                    offsetAABB.minX * (1.0 - direction.getStepX()),
                                    offsetAABB.minY * (1.0 - direction.getStepY()),
                                    offsetAABB.minZ * (1.0 - direction.getStepZ()),
                                    offsetAABB.maxX * (1.0 - direction.getStepX()) + direction.getStepX(),
                                    offsetAABB.maxY * (1.0 - direction.getStepY()) + direction.getStepY(),
                                    offsetAABB.maxZ * (1.0 - direction.getStepZ()) + direction.getStepZ()
                            );

                            compressedMinAABB.intersect(compressedOffsetAABB, intersection);
                            if (Math.abs(intersection.volume() - compressedMinAABB.volume()) < 0.01) {
                                discard = true;
                                break;
                            }
                        }

                        // if the collision is deemed to be interior, let's discard it
                        if (discard) {
                            continue;
                        }

                        // now let's actually handle it
                        maxMTV.normalize(normalizedMtv);
                        final double dot = normalizedMtv.dot(entityUp);

                        final boolean verticalCollision = Math.abs(dot) > 0.6;

                        // record the first collision w/ the sub-level
                        final BlockState collidedBlockState = maxBlockState;

                        firstCollisions.computeIfAbsent(subLevel, (sl) -> {
                            final Vector3d localBoundsCenter = subLevelPose.transformPositionInverse(new Vector3d(entityBoundsCenter));
                            return new FirstCollisionInfo(localBoundsCenter,
                                    new Vector3d(maxMTV).normalize(),
                                    !verticalCollision,
                                    collidedBlockState.is(SableTags.BOUNCY),
                                    collidedBlockState);
                        });

                        if (verticalCollision) {
                            collisionInfo.verticalCollision = true;

                            if (dot > 0.0) {
                                entity.setOnGround(true);
                                collisionInfo.verticalCollisionBelow = true;

                                if (collisionInfo.trackingSubLevel != subLevel && !swappedTrackingAlready) {
                                    swappedTrackingAlready = true;
                                    collisionInfo.trackingSubLevel = subLevel;
//                                    collisionInfo.trackingLocalUpDirection = subLevelPose.transformNormalInverse(new Vector3d(0.0, 1.0, 0.0));
                                }
                            }
                            if (dot > 0.8) {
                                final double preLength = maxMTV.length();
                                entityUp.mul(maxMTV.dot(entityUp), maxMTV).normalize(preLength);
                            }
                        } else {
                            collisionInfo.subLevelHorizontalCollision |= !tryStepUp(entity,
                                    accel,
                                    sink,
                                    subLevelPose,
                                    blocks,
                                    entityBoundsCenter,
                                    entityBounds,
                                    entityBoundsOBB,
                                    cubeOBB,
                                    maxMTV,
                                    normalizedMtv,
                                    collisionMotion);

                            if (collisionInfo.subLevelHorizontalCollision) {
                                // TODO: We really should be going through the vanilla horizontal collision / minor horizontal collision
                                JOMLConversion.toJOML(entity.getDeltaMovement(), existingDeltaMovement);
                                final Vector3d deltaMovementLoss = normalizedMtv.mul(normalizedMtv.dot(existingDeltaMovement));

                                if (deltaMovementLoss.length() > existingDeltaMovement.length() * 0.1) {
                                    entity.setSprinting(false);
                                }

                                // TODO: Vanilla has friction values for these. We should be using those
                                final double friction = 0.995;
                                final Vector3d newDeltaMovement = existingDeltaMovement.sub(deltaMovementLoss);


                                final double upVelocity = entityUp.dot(newDeltaMovement);
                                newDeltaMovement.fma(-upVelocity, entityUp).mul(friction).fma(upVelocity, entityUp);

                                entity.setDeltaMovement(JOMLConversion.toMojang(newDeltaMovement));
                            }
                        }

                        collisionMotion.add(maxMTV);
                        entityBoundsCenter.add(collisionMotion, entityBoundsOBB.getPosition());
                    }
                }
            }
        }

        collisionInfo.inheritedMotion = JOMLConversion.toMojang(
                Sable.HELPER.getFeetPos(entity, 0.0f, customEntityOrientation)
                        .sub(originalEntityFootPosition));

        if (collisionInfo.inheritedMotion.lengthSqr() < 1e-8) {
            collisionInfo.inheritedMotion = null;
        }

        if (stopTrackingAtEnd) {
            collisionInfo.trackingSubLevel = null;
        }

        ((EntityExtension) entity).sable$setPosSuperRaw(originalEntityPosition);

        collisionInfo.motion = JOMLConversion.toMojang(collisionMotion);
        collisionInfo.firstCollisions = firstCollisions;
        return collisionInfo;
    }

    public static void transformEntityBoundsCenter(final LevelReusedVectors sink, final Quaterniondc customOrientation, final Entity entity, final Vector3d center) {
        if (customOrientation == null) {
            return;
        }

        final Vector3d offset = sink.anchorRelativePosition.set(0.0, entity.getEyeHeight() - entity.getBoundingBox().getYsize() / 2.0, 0.0);
        center.add(offset).sub(customOrientation.transform(offset));
    }

    public static void transformEntityBoundingBox(final Quaterniondc customOrientation, final Quaterniond bounds, final Vector3d upDir) {
        if (customOrientation == null) {
            return;
        }

        bounds.premul(customOrientation);
        customOrientation.transform(upDir);
    }

    public static double getHitBoxYaw(final Pose3dc subLevelPose) {
        final Quaterniondc subLevelOrientation = subLevelPose.orientation();
        final Quaterniond snapped = SableMathUtils.clampQuaternionToGrid(subLevelOrientation, SableMathUtils.GridQuats.REAL, new Quaterniond());
        final Quaterniond relativeOrientation = subLevelOrientation.div(snapped, snapped);

        final double dot = OrientedBoundingBox3d.UP.dot(new Vector3d(relativeOrientation.x(), relativeOrientation.y(), relativeOrientation.z()));

        return -2.0 * Math.atan2(-dot, relativeOrientation.w());
    }

    private static @NotNull VoxelShape getSubLevelEntityCollisionShape(final Entity entity,
                                                                       final Vector3dc boundsCenter,
                                                                       final Pose3dc subLevelPose,
                                                                       final BlockState state,
                                                                       final LevelAccelerator level,
                                                                       final BlockPos pos,
                                                                       final LevelReusedVectors sink) {
        if (state.getBlock() instanceof ScaffoldingBlock) {
            final VoxelShape originalShape = state.getCollisionShape(level, pos, new SubLevelEntityCollisionContext(entity));
            final double skew = 0.05;
            if (entity.isShiftKeyDown())
                return originalShape;
            else if (subLevelPose.transformPositionInverse(boundsCenter.fma(-(entity.getBoundingBox().getYsize() / 2.0 - skew), sink.entityUpDirection, new Vector3d())).y > pos.getY() + 1.0 + skew)
                return sink.SCAFFOLDING_TOP;
            return originalShape;
        }

        return state.getCollisionShape(level, pos);
    }

    private static boolean tryStepUp(final Entity entity,
                                     final LevelAccelerator accel,
                                     final LevelReusedVectors sink,
                                     final Pose3dc subLevelPose,
                                     final Iterable<BlockPos> blocks,
                                     final Vector3dc entityBoundsCenter,
                                     final AABB entityBounds,
                                     final OrientedBoundingBox3d entityBoundsOBB,
                                     final OrientedBoundingBox3d cubeOBB,
                                     final Vector3dc maxMTV,
                                     final Vector3dc normalizedMTV,
                                     final Vector3d collisionMotion) {
        if (!entity.onGround()) return false;
        if (collisionMotion.dot(normalizedMTV) > 0.0) return true;

        final double checkIncrement = 1.0 / 16.0;
        final double maxStepHeight = entity.maxUpStep();
        double currentStepUp;

        final Vector3d lastStepTestMTV = sink.lastStepTestMTV.zero();
        int collidingCount = 0;
        int freeCount = 0;

        final double inflation = 0.1;
        entityBoundsOBB.getDimensions().set(entityBounds.getXsize(), entityBounds.getYsize(), entityBounds.getZsize())
                .add(inflation, inflation, inflation);

        for (currentStepUp = 0; currentStepUp <= maxStepHeight; currentStepUp += checkIncrement) {
            final Vector3d boundsCenter = sink.stepHeightEntityBoundsCenter;

            boundsCenter.set(entityBoundsCenter).fma(currentStepUp, sink.entityUpDirection).fma(-2.0 / 16.0, normalizedMTV);

            if (hasCollision(accel, sink, subLevelPose, blocks, entityBoundsOBB, cubeOBB, boundsCenter)) {
                lastStepTestMTV.set(sink.mtv);
                collidingCount++;
            } else {
                freeCount++;
                break;
            }
        }

        entityBoundsOBB.getDimensions().set(entityBounds.getXsize(), entityBounds.getYsize(), entityBounds.getZsize());

        if (freeCount > 0 && collidingCount > 0 && lastStepTestMTV.normalize().dot(sink.entityUpDirection) > 0.8) {
            collisionMotion.fma(currentStepUp, sink.entityUpDirection).fma(-1.0 / 16.0, normalizedMTV);
            return true;
        }

        return false;
    }

    private static boolean hasCollision(final LevelAccelerator accel, final LevelReusedVectors sink, final Pose3dc subLevelPose, final Iterable<BlockPos> blocks, final OrientedBoundingBox3d entityBoundsOBB, final OrientedBoundingBox3d cubeOBB, final Vector3d boundsCenter) {
        entityBoundsOBB.setPosition(boundsCenter);

        // iterate through all blocks
        for (final BlockPos block : blocks) {
            final BlockState state = accel.getBlockState(block);
            final VoxelShape voxelShape = state.getCollisionShape(accel, block);

            if (state.isAir()) {
                continue;
            }

            final Iterator<BoundingBox3dc> iterator = ((FastVoxelShapeIterable) voxelShape).sable$allBoxes();
            final Vector3d center = sink.center;
            final Vector3d mtv = sink.mtv;

            while (iterator.hasNext()) {
                final BoundingBox3dc box = iterator.next();
                box.center(center);
                cubeOBB.getPosition().set(block.getX() + center.x,
                        block.getY() + center.y,
                        block.getZ() + center.z);
                subLevelPose.transformPosition(cubeOBB.getPosition());
                box.size(cubeOBB.getDimensions());

                OrientedBoundingBox3d.sat(entityBoundsOBB, cubeOBB, mtv);

                if (mtv.lengthSquared() > 0.0 && mtv.x != Double.MAX_VALUE && mtv.y != Double.MAX_VALUE && mtv.z != Double.MAX_VALUE) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The first collision with a sub-level during a collision check
     */
    public record FirstCollisionInfo(Vector3dc localLocation, Vector3dc globalDirection, boolean horizontal,
                                     boolean bouncy, BlockState block) {
    }

    public static class CollisionInfo {
        public SubLevel preTrackingSubLevel;
        public Vec3 preDeltaMovement;

        public boolean subLevelHorizontalCollision;
        public boolean horizontalCollision;
        public boolean verticalCollision;
        public boolean verticalCollisionBelow;
        public boolean minorHorizontalCollision;
        public Vec3 inheritedMotion;
        public Vec3 motion;
        public SubLevel trackingSubLevel;
        public Map<SubLevel, FirstCollisionInfo> firstCollisions;
//        public Vector3d trackingLocalUpDirection = null;
    }

}
