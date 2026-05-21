package dev.ryanhcode.sable.mixin.particle;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import dev.ryanhcode.sable.api.particle.ParticleSubLevelKickable;
import dev.ryanhcode.sable.companion.math.*;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.ClipContextExtension;
import dev.ryanhcode.sable.mixinterface.particle.ParticleExtension;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(Particle.class)
public abstract class ParticleMixin implements ParticleExtension {

    @Unique
    private static final double LIGHT_QUERY_AREA = 8.0F;
    @Unique
    private final static BoundingBox3d TEMP_BOX = new BoundingBox3d();
    @Unique
    private final Vector3d sable$inheritedVelocity = new Vector3d();
    @Shadow
    public double x;
    @Shadow
    public double y;
    @Shadow
    public double z;
    @Shadow
    protected double xd;
    @Shadow
    protected double zd;
    @Shadow
    protected double yd;
    @Shadow
    protected double xo;
    @Shadow
    protected double zo;
    @Shadow
    protected double yo;
    @Shadow
    @Final
    protected ClientLevel level;
    @Shadow
    protected boolean onGround;
    @Unique
    private boolean sable$checkedInitialKick = false;
    @Unique
    @Nullable
    private ClientSubLevel sable$trackingSubLevel = null;
    @Unique
    @Nullable
    private Vector3d sable$localTrackingAnchor = null;
    @Unique
    private List<ClientSubLevel> sable$nearbySubLevels;
    @Shadow
    private boolean stoppedByCollision;

    @Shadow
    public abstract void setPos(double d, double e, double g);

    @Shadow
    public abstract void move(double d, double e, double f);

    @Shadow
    protected abstract void setLocationFromBoundingbox();

    @Shadow
    public abstract AABB getBoundingBox();

    @Shadow
    public abstract void setBoundingBox(AABB aABB);

    @Shadow
    public abstract void tick();

    //#region stupid vanilla velocity
    @ModifyConstant(method = "Lnet/minecraft/client/particle/Particle;<init>(Lnet/minecraft/client/multiplayer/ClientLevel;DDDDDD)V", constant = @Constant(ordinal = 13))
    private double sable$removeUpwardsVelocity(final double originalBlockDamageDistanceConstant) {
        return 0.0;
    }
    //#endregion

    @Inject(method = "Lnet/minecraft/client/particle/Particle;<init>(Lnet/minecraft/client/multiplayer/ClientLevel;DDDDDD)V", at = @At("TAIL"))
    private void sable$addUpwardsVelocity(final ClientLevel clientLevel, final double d, final double e, final double f, final double g, final double h, final double i, final CallbackInfo ci) {
        final Vec3 particlePosition = new Vec3(this.x, this.y, this.z);
        final ClientSubLevel subLevel = Sable.HELPER.getContainingClient(particlePosition);

        if (subLevel != null) {
            final Vec3 stupidVanillaVelocity = subLevel.logicalPose().transformNormalInverse(new Vec3(0.0, 0.1, 0.0));

            this.xd += stupidVanillaVelocity.x;
            this.yd += stupidVanillaVelocity.y;
            this.zd += stupidVanillaVelocity.z;

            this.sable$setTrackingSubLevel(subLevel, particlePosition);
        }
    }

    @Override
    public void sable$initialKickOut() {
        final Vec3 particlePosition = new Vec3(this.x, this.y, this.z);

        if (!this.sable$checkedInitialKick) {
            final ClientSubLevel subLevel = Sable.HELPER.getContainingClient(particlePosition);

            if (subLevel != null) {
                // Kick the particle out!
                final Pose3d pose = subLevel.logicalPose();
                final Vec3 particlePositionOld = new Vec3(this.xo, this.yo, this.zo);

                final Vec3 globalPosition = pose.transformPosition(particlePosition);
                final Vec3 globalPositionOld = pose.transformPosition(particlePositionOld);
                final Vec3 globalVelocity = pose.transformNormal(new Vec3(this.xd, this.yd, this.zd));

                this.x = globalPosition.x;
                this.y = globalPosition.y;
                this.z = globalPosition.z;

                this.xo = globalPositionOld.x;
                this.yo = globalPositionOld.y;
                this.zo = globalPositionOld.z;

                this.xd = globalVelocity.x;
                this.yd = globalVelocity.y;
                this.zd = globalVelocity.z;

                this.setPos(this.x, this.y, this.z);

                this.sable$setTrackingSubLevel(subLevel, particlePosition);
            }

            this.sable$checkedInitialKick = true;
        }
    }

    private void sable$kickFromTracking() {
        final Vector3d currentLocalPos = this.sable$trackingSubLevel.logicalPose().transformPositionInverse(new Vector3d(this.x, this.y, this.z));
        Sable.HELPER.getVelocity(this.level, currentLocalPos, this.sable$inheritedVelocity);

        // from m/s to m/t
        this.sable$inheritedVelocity.mul(1.0 / 20.0);
        this.sable$localTrackingAnchor = null;
        this.sable$trackingSubLevel = null;
    }

    @Override
    public void sable$moveWithInheritedVelocity() {

    }

    @Override
    public void sable$setTrackingSubLevel(final ClientSubLevel subLevel, final Vec3 particlePosition) {
        this.sable$trackingSubLevel = subLevel;
        this.sable$localTrackingAnchor = new Vector3d();
        this.sable$localTrackingAnchor.set(particlePosition.x, particlePosition.y, particlePosition.z);
        this.sable$inheritedVelocity.zero();
    }

    @Override
    public SubLevel sable$getTrackingSubLevel() {
        return this.sable$trackingSubLevel;
    }

    @WrapMethod(method = "move")
    private void sable$moveWithSubLevels(final double motionX, final double motionY, final double motionZ, final Operation<Void> original) {
        final AABB bounds = this.getBoundingBox();
        final BoundingBox3d globalBounds = new BoundingBox3d(bounds).expand(0.5);
        final ObjectSet<SubLevel> intersecting = new ObjectOpenHashSet<>();

        final boolean ignoreIntersecting = this instanceof final ParticleSubLevelKickable kickable && !kickable.sable$shouldCareAboutIntersectingSubLevels();

        if (!ignoreIntersecting) {
            final Iterable<SubLevel> subLevels = Sable.HELPER.getAllIntersecting(this.level, globalBounds);
            for (final SubLevel subLevel : subLevels) {
                intersecting.add(subLevel);
            }
        }

        if (this.sable$trackingSubLevel != null) {
            intersecting.add(this.sable$trackingSubLevel);
        }

        if (this.sable$trackingSubLevel != null && this.sable$trackingSubLevel.isRemoved()) {
            this.sable$trackingSubLevel = null;
            this.sable$localTrackingAnchor = null;
        }

        final Vector3d movementFromPushing = new Vector3d();
        final Vector3d localPosition = new Vector3d();
        final Vector3d globalBoundsCenter = new Vector3d();
        final Vector3d localRayStart = new Vector3d();
        final Vector3d localRayEnd = new Vector3d();

        final Vector3d movement = new Vector3d(motionX, motionY, motionZ);
        movement.add(this.sable$inheritedVelocity);

        boolean isGrounded = false;
        for (final SubLevel subLevel : intersecting) {
            final Pose3dc pose = subLevel.logicalPose();
            final Pose3dc last = subLevel.lastPose();

            movementFromPushing.zero();

            if (this.sable$trackingSubLevel == subLevel) {
                // Move with our current tracking sub-level
                JOMLConversion.getAABBCenter(bounds, globalBoundsCenter);
                last.transformPositionInverse(globalBoundsCenter, localPosition);
                final Vector3d newGlobalPosition = pose.transformPosition(localPosition);
                movementFromPushing.add(newGlobalPosition).sub(globalBoundsCenter);
            } else {
                // Handle sub-levels moving into the particle and pushing it
                JOMLConversion.getAABBCenter(bounds, globalBoundsCenter);
                last.transformPositionInverse(globalBoundsCenter, localRayStart);
                pose.transformPositionInverse(globalBoundsCenter, localRayEnd);

                final ClipContext clipContext = new ClipContext(JOMLConversion.toMojang(localRayStart),
                        JOMLConversion.toMojang(localRayEnd),
                        ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE,
                        CollisionContext.empty());
                ((ClipContextExtension) clipContext).sable$setDoNotProject(true);
                final BlockHitResult result = this.level.clip(clipContext);

                if (result.getType() == HitResult.Type.BLOCK) {
                    pose.transformPosition(JOMLConversion.toJOML(result.getLocation(), movementFromPushing)).sub(globalBoundsCenter);

                    if (this.sable$trackingSubLevel == null) {
                        this.sable$setTrackingSubLevel((ClientSubLevel) subLevel, result.getLocation());
                    }
                }
            }

            final boolean shouldCollide = !(this.sable$trackingSubLevel == subLevel &&
                    this instanceof final ParticleSubLevelKickable kickable &&
                    !kickable.sable$shouldCollideWithTrackingSubLevel());

            if (shouldCollide) {
                final Vector3dc pushedPosition = JOMLConversion.getAABBCenter(bounds, globalBoundsCenter).add(movementFromPushing);
                pose.transformPositionInverse(pushedPosition, localRayStart);
                pose.transformPositionInverse(pushedPosition.add(movement, localRayEnd));

                final ClipContext clipContext = new ClipContext(JOMLConversion.toMojang(localRayStart),
                        JOMLConversion.toMojang(localRayEnd),
                        ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE,
                        CollisionContext.empty());
                ((ClipContextExtension) clipContext).sable$setDoNotProject(true);
                final BlockHitResult result = this.level.clip(clipContext);

                if (result != null && result.getType() == HitResult.Type.BLOCK) {
                    final Vec3 diff = pose.transformPosition(result.getLocation())
                            .subtract(pushedPosition.x(), pushedPosition.y(), pushedPosition.z());
                    movement.set(diff.x, diff.y, diff.z);
                }
            }

            movement.add(movementFromPushing);

            //#region run cube collision to push us away from the block
            if (shouldCollide) {
                final Vec3 collisionBoxCenter = pose.transformPositionInverse(bounds.getCenter().add(movement.x, movement.y, movement.z));
                final double radius = Math.max(bounds.getXsize(), Math.max(bounds.getYsize(), bounds.getZsize())) / 2.0;
                final BoundingBox3d collisionBounds = new BoundingBox3d();
                collisionBounds.set(collisionBoxCenter.x - radius, collisionBoxCenter.y - radius, collisionBoxCenter.z - radius,
                        collisionBoxCenter.x + radius, collisionBoxCenter.y + radius, collisionBoxCenter.z + radius
                );
                final Vector3d mtv = this.resolveAABBCollision(collisionBounds);
                if (mtv.lengthSquared() > 0.0) {
                    subLevel.logicalPose().transformNormal(mtv);

                    final Vector3d nmtv = mtv.normalize(new Vector3d());

                    Vector3dc upDirection = OrientedBoundingBox3d.UP;
                    if (this instanceof final ParticleSubLevelKickable kickable) {
                        upDirection = kickable.sable$getUpDirection();
                    }

                    final double verticalDot = nmtv.dot(upDirection);
                    if (verticalDot > 0.6) {
                        isGrounded = true;
                    }

                    final double dot = nmtv.dot(this.xd, this.yd, this.zd);
                    this.xd -= dot * nmtv.x;
                    this.yd -= dot * nmtv.y;
                    this.zd -= dot * nmtv.z;

                    // emulate vanilla stopping particles when they reach stoppedByCollision
                    // as we can't actually stop their collision
                    if (verticalDot > 0.6 || verticalDot < 0.6) {
                        this.xd = upDirection.x() * this.xd;
                        this.yd = upDirection.y() * this.yd;
                        this.zd = upDirection.z() * this.zd;
                    }

                    movement.add(mtv);

                    if (this.sable$trackingSubLevel == null) {
                        this.sable$setTrackingSubLevel((ClientSubLevel) subLevel, collisionBoxCenter);
                    }
                }
            }
            //#endregion
        }

        original.call(movement.x, movement.y, movement.z);
        this.onGround |= isGrounded;

        if (this.sable$trackingSubLevel != null && !(this instanceof final ParticleSubLevelKickable kickable && !kickable.sable$shouldKickFromTracking())) {
            if (this.sable$trackingSubLevel.logicalPose().transformPosition(this.sable$localTrackingAnchor, new Vector3d()).distanceSquared(this.x, this.y, this.z) > 0.5 * 0.5) {
                this.sable$kickFromTracking();
            }
        }
    }

    /**
     * Resolves collisions between an AABB and nearby blocks, pushing the AABB out by the maximum MTV.
     */
    private Vector3d resolveAABBCollision(final BoundingBox3d box) {
        final Vector3d totalMTV = new Vector3d();
        final Vector3d mtv = new Vector3d();
        final double[] maxMTVLengthSq = {0.0};

        final int minX = (int) Math.floor(box.minX());
        final int minY = (int) Math.floor(box.minY());
        final int minZ = (int) Math.floor(box.minZ());
        final int maxX = (int) Math.floor(box.maxX());
        final int maxY = (int) Math.floor(box.maxY());
        final int maxZ = (int) Math.floor(box.maxZ());

        final BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    final BlockPos blockPos = mpos.set(x, y, z);
                    final BlockState state = this.level.getBlockState(blockPos);

                    if (!state.isAir()) {
                        final VoxelShape shape = state.getCollisionShape(this.level, blockPos);
                        if (!shape.isEmpty()) {
                            final int finalX = x;
                            final int finalY = y;
                            final int finalZ = z;

                            if (state.isCollisionShapeFullBlock(this.level, blockPos)) {
                                TEMP_BOX.setUnchecked(
                                        0.0 + finalX, 0.0 + finalY, 0.0 + finalZ,
                                        1.0 + finalX, 1.0 + finalY, 1.0 + finalZ
                                );

                                mtv.zero();
                                this.resolveAABBAABBCollision(box, TEMP_BOX, mtv);

                                final double lenSq = mtv.lengthSquared();
                                if (lenSq > maxMTVLengthSq[0]) {
                                    maxMTVLengthSq[0] = lenSq;
                                    totalMTV.set(mtv);
                                }
                            } else {
                                shape.forAllBoxes((minXb, minYb, minZb, maxXb, maxYb, maxZb) -> {
                                    TEMP_BOX.setUnchecked(
                                            minXb + finalX, minYb + finalY, minZb + finalZ,
                                            maxXb + finalX, maxYb + finalY, maxZb + finalZ
                                    );

                                    mtv.zero();
                                    this.resolveAABBAABBCollision(box, TEMP_BOX, mtv);

                                    final double lenSq = mtv.lengthSquared();
                                    if (lenSq > maxMTVLengthSq[0]) {
                                        maxMTVLengthSq[0] = lenSq;
                                        totalMTV.set(mtv);
                                    }
                                });
                            }
                        }
                    }
                }
            }
        }

        return totalMTV;
    }

    /**
     * Resolves collision between two AABBs, outputs MTV into mtv.
     * Only resolves if they intersect.
     */
    private void resolveAABBAABBCollision(final BoundingBox3d a, final BoundingBox3dc b, final Vector3d mtv) {
        final double dx1 = b.maxX() - a.minX();
        final double dx2 = a.maxX() - b.minX();
        if (dx1 <= 0 || dx2 <= 0) {
            return;
        }

        final double dy1 = b.maxY() - a.minY();
        final double dy2 = a.maxY() - b.minY();
        if (dy1 <= 0 || dy2 <= 0) {
            return;
        }

        final double dz1 = b.maxZ() - a.minZ();
        final double dz2 = a.maxZ() - b.minZ();
        if (dz1 <= 0 || dz2 <= 0) {
            return;
        }

        double minOverlap = dx1;
        mtv.set(dx1, 0, 0);

        if (dx2 < minOverlap) {
            minOverlap = dx2;
            mtv.set(-dx2, 0, 0);
        }

        if (dy1 < minOverlap) {
            minOverlap = dy1;
            mtv.set(0, dy1, 0);
        }
        if (dy2 < minOverlap) {
            minOverlap = dy2;
            mtv.set(0, -dy2, 0);
        }

        if (dz1 < minOverlap) {
            minOverlap = dz1;
            mtv.set(0, 0, dz1);
        }
        if (dz2 < minOverlap) {
            minOverlap = dz2;
            mtv.set(0, 0, -dz2);
        }
    }

    @Inject(method = "getLightColor", at = @At("HEAD"), cancellable = true)
    private void sable$checkSubLevelLightColor(final float f, final CallbackInfoReturnable<Integer> cir) {
        final BlockPos pos = BlockPos.containing(this.x, this.y, this.z);
        final boolean hasChunk = this.level.hasChunkAt(pos);

        if (!hasChunk) {
            return;
        }

        final BlockState state = this.level.getBlockState(pos);
        if (state.emissiveRendering(this.level, pos)) {
            cir.setReturnValue(LightTexture.FULL_BRIGHT);
            return;
        }

        int blockLight;
        int skyLight;

        // If tracking a sub-level, then don't bother checking against others
        if (this.sable$trackingSubLevel != null) {
            blockLight = this.level.getBrightness(LightLayer.BLOCK, pos);
            skyLight = this.level.getBrightness(LightLayer.SKY, pos);

            final Vector3d particlePos = new Vector3d();
            final BlockPos.MutableBlockPos localBlockPos = new BlockPos.MutableBlockPos();
            final BlockPos.MutableBlockPos heightmapPos = new BlockPos.MutableBlockPos();

            final Pose3d pose = this.sable$trackingSubLevel.logicalPose();
            pose.transformPositionInverse(particlePos.set(this.x, this.y, this.z));
            localBlockPos.set(particlePos.x, particlePos.y, particlePos.z);
            blockLight = Math.max(blockLight, this.sable$trackingSubLevel.getLevel().getBrightness(LightLayer.BLOCK, localBlockPos));

            heightmapPos.setWithOffset(localBlockPos, 0, 1, 0);
            final LevelPlot plot = this.sable$trackingSubLevel.getPlot();
            boolean isAboveGround = false;

            while (heightmapPos.getY() >= plot.getBoundingBox().minY()) {
                if (!this.level.getBlockState(heightmapPos).isAir()) {
                    isAboveGround = true;
                    break;
                }

                heightmapPos.move(0, -1, 0);
            }

            if (isAboveGround) {
                skyLight = Math.min(skyLight, this.sable$trackingSubLevel.scaleSkyLight(this.level.getBrightness(LightLayer.SKY, localBlockPos)));
            }
        } else {
            if (this.sable$nearbySubLevels == null) {
                this.sable$nearbySubLevels = new ObjectArrayList<>(6);
                final Iterable<SubLevel> all = Sable.HELPER.getAllIntersecting(this.level, new BoundingBox3d(pos).expand(LIGHT_QUERY_AREA));
                for (final SubLevel subLevel : all) {
                    this.sable$nearbySubLevels.add((ClientSubLevel) subLevel);
                }
            }
            if (this.sable$nearbySubLevels.isEmpty()) {
                return;
            }

            blockLight = this.level.getBrightness(LightLayer.BLOCK, pos);
            skyLight = this.level.getBrightness(LightLayer.SKY, pos);

            final Vector3d particlePos = new Vector3d();
            final BlockPos.MutableBlockPos localBlockPos = new BlockPos.MutableBlockPos();
            final BlockPos.MutableBlockPos heightmapPos = new BlockPos.MutableBlockPos();
            final BoundingBox3d box = new BoundingBox3d(pos).expand(0.5);

            for (final ClientSubLevel subLevel : this.sable$nearbySubLevels) {
                if (!subLevel.boundingBox().intersects(box)) {
                    continue;
                }

                final Pose3d pose = subLevel.logicalPose();
                pose.transformPositionInverse(particlePos.set(this.x, this.y, this.z));
                localBlockPos.set(particlePos.x, particlePos.y, particlePos.z);
                blockLight = Math.max(blockLight, subLevel.getLevel().getBrightness(LightLayer.BLOCK, localBlockPos));

                heightmapPos.setWithOffset(localBlockPos, 0, 1, 0);
                final LevelPlot plot = subLevel.getPlot();
                boolean isAboveGround = false;

                while (heightmapPos.getY() >= plot.getBoundingBox().minY()) {
                    if (!this.level.getBlockState(heightmapPos).isAir()) {
                        isAboveGround = true;
                        break;
                    }

                    heightmapPos.move(0, -1, 0);
                }

                if (isAboveGround) {
                    skyLight = Math.min(skyLight, subLevel.scaleSkyLight(this.level.getBrightness(LightLayer.SKY, localBlockPos)));
                }
            }
        }

        final int k = state.getLightEmission();
        if (blockLight < k) {
            blockLight = k;
        }

        cir.setReturnValue(LightTexture.pack(blockLight, skyLight));
    }
}

