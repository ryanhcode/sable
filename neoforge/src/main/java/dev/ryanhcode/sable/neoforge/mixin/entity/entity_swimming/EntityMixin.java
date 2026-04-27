package dev.ryanhcode.sable.neoforge.mixin.entity.entity_swimming;

import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.api.math.LevelReusedVectors;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.LevelExtension;
import dev.ryanhcode.sable.neoforge.mixinhelper.entity.SableInterimCalculation;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.common.extensions.IEntityExtension;
import net.neoforged.neoforge.fluids.FluidType;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Entity.class, priority = 500)
public abstract class EntityMixin implements IEntityExtension {

    @Shadow
    public abstract boolean touchingUnloadedChunk();

    @Shadow
    public abstract AABB getBoundingBox();

    @Shadow
    @Deprecated
    public abstract boolean isPushedByFluid();

    @Shadow
    private Level level;

    @Shadow
    public abstract Vec3 getDeltaMovement();

    @Shadow
    public abstract void setDeltaMovement(Vec3 arg);

    @Shadow
    protected abstract void setFluidTypeHeight(FluidType type, double height);

    @Shadow
    public abstract BlockPos blockPosition();

    @Shadow
    public abstract Level level();

    @Shadow
    private Vec3 position;

    @Shadow
    public abstract Vec3 getEyePosition();

    @Shadow
    private FluidType forgeFluidTypeOnEyes;

    /**
     * @author RyanH
     * @reason Take into account water on sub-levels.
     */
    @Overwrite
    public void updateFluidHeightAndDoFluidPushing() {
        if (!this.touchingUnloadedChunk()) {
            final AABB aabb = this.getBoundingBox().deflate(0.001);
            final int i = Mth.floor(aabb.minX);
            final int j = Mth.ceil(aabb.maxX);
            final int k = Mth.floor(aabb.minY);
            final int l = Mth.ceil(aabb.maxY);
            final int i1 = Mth.floor(aabb.minZ);
            final int j1 = Mth.ceil(aabb.maxZ);
            final BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();

            Object2ObjectMap<FluidType, SableInterimCalculation> interimCalcs = null;

            for (int l1 = i; l1 < j; l1++) {
                for (int i2 = k; i2 < l; i2++) {
                    for (int j2 = i1; j2 < j1; j2++) {
                        blockpos$mutableblockpos.set(l1, i2, j2);
                        final FluidState fluidstate = this.level.getFluidState(blockpos$mutableblockpos);
                        final FluidType fluidType = fluidstate.getFluidType();
                        if (!fluidType.isAir()) {
                            final double d1 = (float) i2 + fluidstate.getHeight(this.level, blockpos$mutableblockpos);
                            if (d1 >= aabb.minY) {
                                if (interimCalcs == null) {
                                    interimCalcs = new Object2ObjectArrayMap<>();
                                }

                                final SableInterimCalculation interim = interimCalcs.computeIfAbsent(fluidType, t -> new SableInterimCalculation());
                                interim.fluidHeight = Math.max(d1 - aabb.minY, interim.fluidHeight);
                                if (this.isPushedByFluid(fluidType)) {
                                    Vec3 vec31 = fluidstate.getFlow(this.level, blockpos$mutableblockpos);
                                    if (interim.fluidHeight < 0.4) {
                                        vec31 = vec31.scale(interim.fluidHeight);
                                    }

                                    interim.flowVector = interim.flowVector.add(vec31);
                                    interim.blockCount++;
                                }
                            }
                        }
                    }
                }
            }

            //#region sable stuff
            final ActiveSableCompanion helper = Sable.HELPER;
            final BoundingBox3d globalBounds = new BoundingBox3d(aabb);
            final BoundingBox3d localBounds = new BoundingBox3d();
            final Iterable<SubLevel> intersecting = helper.getAllIntersecting(this.level, globalBounds);

            final BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

            final Vector3d playerCenter = new Vector3d();
            final Vector3d playerSize = new Vector3d();
            final Quaterniond playerOrientation = new Quaterniond();

            for (final SubLevel subLevel : intersecting) {
                final Pose3dc pose = subLevel.lastPose();
                globalBounds.transformInverse(pose, localBounds);

                final LevelReusedVectors jomlSink = ((LevelExtension) this.level).sable$getJOMLSink();
                final Quaterniond localPlayerBox = pose.orientation().conjugate(playerOrientation);

                final double yaw = SubLevelEntityCollision.getHitBoxYaw(pose);
                localPlayerBox.rotateY(yaw);

                final OrientedBoundingBox3d playerBox = new OrientedBoundingBox3d(pose.transformPositionInverse(globalBounds.center(playerCenter)), globalBounds.size(playerSize), localPlayerBox, jomlSink);
                final OrientedBoundingBox3d fluidBox = new OrientedBoundingBox3d(new Vector3d(), new Vector3d(1.0), JOMLConversion.QUAT_IDENTITY, jomlSink);

                final int minX = Mth.floor(localBounds.minX);
                final int maxX = Mth.ceil(localBounds.maxX);
                final int minY = Mth.floor(localBounds.minY);
                final int maxY = Mth.ceil(localBounds.maxY);
                final int minZ = Mth.floor(localBounds.minZ);
                final int maxZ = Mth.ceil(localBounds.maxZ);

                double minYVertex = Float.MAX_VALUE;
                boolean hasComputedMinYVertex = false;

                for (int x = minX; x < maxX; x++) {
                    for (int y = minY; y < maxY; y++) {
                        for (int z = minZ; z < maxZ; z++) {
                            mutableBlockPos.set(x, y, z);
                            final FluidState fluidState = this.level.getFluidState(mutableBlockPos);
                            final FluidType fluidType = fluidState.getFluidType();

                            if (!fluidType.isAir()) {
                                final double fluidLevelY = (float) y + fluidState.getHeight(this.level, mutableBlockPos);

                                if (!hasComputedMinYVertex) {
                                    final Vector3d[] vertices = playerBox.vertices(jomlSink.a);

                                    for (final Vector3d vertex : vertices) {
                                        minYVertex = Math.min(minYVertex, vertex.y);
                                    }

                                    hasComputedMinYVertex = true;
                                }

                                if (fluidLevelY >= minYVertex) {
                                    fluidBox.getPosition().set(x + 0.5, y + 0.5, z + 0.5);

                                    if (!(OrientedBoundingBox3d.sat(playerBox, fluidBox).lengthSquared() > 0.0))
                                        continue;

                                    if (interimCalcs == null) {
                                        interimCalcs = new Object2ObjectArrayMap<>();
                                    }

                                    final SableInterimCalculation interim = interimCalcs.computeIfAbsent(fluidType, t -> new SableInterimCalculation());
                                    interim.fluidHeight = Math.max(fluidLevelY - minYVertex, interim.fluidHeight);

                                    if (Sable.HELPER.getTrackingSubLevel((Entity) (Object) this) == null && helper.getContaining((Entity) (Object) this) != subLevel) {
                                        ((EntityMovementExtension) this).sable$setTrackingSubLevel(subLevel);
                                    }

                                    if (this.isPushedByFluid(fluidType)) {
                                        Vec3 flowVec = fluidState.getFlow(this.level, mutableBlockPos);

                                        if (interim.fluidHeight < 0.4) {
                                            flowVec = flowVec.scale(interim.fluidHeight);
                                        }

                                        flowVec = pose.transformNormal(flowVec);

                                        interim.flowVector = interim.flowVector.add(flowVec);
                                        interim.blockCount++;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            //#region sable end

            if (interimCalcs != null) {
                interimCalcs.forEach((fluidTypex, interimx) -> {
                    if (interimx.flowVector.length() > 0.0) {
                        if (interimx.blockCount > 0) {
                            interimx.flowVector = interimx.flowVector.scale(1.0 / (double) interimx.blockCount);
                        }

                        if (!((Object) this instanceof Player)) {
                            interimx.flowVector = interimx.flowVector.normalize();
                        }

                        final Vec3 vec32 = this.getDeltaMovement();
                        interimx.flowVector = interimx.flowVector.scale(this.getFluidMotionScale(fluidTypex));
                        final double d2 = 0.003;
                        if (Math.abs(vec32.x) < d2 && Math.abs(vec32.z) < d2 && interimx.flowVector.length() < 0.0045000000000000005) {
                            interimx.flowVector = interimx.flowVector.normalize().scale(0.0045000000000000005);
                        }

                        this.setDeltaMovement(this.getDeltaMovement().add(interimx.flowVector));
                    }

                    this.setFluidTypeHeight(fluidTypex, interimx.fluidHeight);
                });
            }
        }
    }

    @Override
    public boolean canStartSwimming() {
        final Level level = this.level();
        final BlockPos globalBlockPos = this.blockPosition();
        FluidType fluidType = level.getFluidState(globalBlockPos).getFluidType();

        if (fluidType == Fluids.EMPTY.getFluidType()) {
            final Iterable<SubLevel> intersecting = Sable.HELPER.getAllIntersecting(this.level, new BoundingBox3d(globalBlockPos).expand(0.5));

            for (final SubLevel subLevel : intersecting) {
                final Pose3dc pose = subLevel.lastPose();

                final BlockPos localBlockPos = BlockPos.containing(pose.transformPositionInverse(this.position));
                fluidType = level.getFluidState(localBlockPos).getFluidType();

                if (fluidType != Fluids.EMPTY.getFluidType()) {
                    break;
                }
            }
        }

        return !this.getEyeInFluidType().isAir() && this.canSwimInFluidType(this.getEyeInFluidType()) && this.canSwimInFluidType(fluidType);
    }

    @Inject(method = "updateFluidOnEyes", at = @At(value = "TAIL"))
    public void sable$subLevelFluidOnEyes(final CallbackInfo ci) {
        if (this.forgeFluidTypeOnEyes != NeoForgeMod.EMPTY_TYPE.value() && this.forgeFluidTypeOnEyes != Fluids.EMPTY.getFluidType()) {
            return;
        }

        final Vec3 globalEyePos = this.getEyePosition();
        final Iterable<SubLevel> intersecting = Sable.HELPER.getAllIntersecting(this.level, new BoundingBox3d(BlockPos.containing(globalEyePos)).expand(0.5));

        for (final SubLevel subLevel : intersecting) {
            final Pose3dc pose = subLevel.lastPose();
            final Vec3 localEyePos = pose.transformPositionInverse(globalEyePos);
            final BlockPos blockPos = BlockPos.containing(localEyePos);

            final FluidState fluidState = this.level.getFluidState(blockPos);
            final double e = (float) blockPos.getY() + fluidState.getHeight(this.level, blockPos);

            if (e > localEyePos.y) {
                this.forgeFluidTypeOnEyes = fluidState.getFluidType();

                if (this.forgeFluidTypeOnEyes != NeoForgeMod.EMPTY_TYPE.value() && this.forgeFluidTypeOnEyes != Fluids.EMPTY.getFluidType()) {
                    return;
                }
            }
        }
    }
}
