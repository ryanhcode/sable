package dev.ryanhcode.sable.neoforge.mixin.entity.entity_swimming;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalDoubleRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
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
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

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


    @Shadow public abstract void updateFluidHeightAndDoFluidPushing();

    @Unique
    private SubLevel sable$sl = null;
    @Unique
    private Pose3dc sable$lastPose = null;
    @Unique
    private Object2ObjectMap interimCalcs = null;
    @Unique
    private BoundingBox3d sable$globalBound = new BoundingBox3d();
    @Unique
    private LevelReusedVectors sable$jomlSink = null;

    // avoid reallocation
    @Unique
    private BoundingBox3d sable$localBound = new BoundingBox3d();
    @Unique
    private Quaterniond sable$playerOrientation = new Quaterniond();
    @Unique
    private BlockPos.MutableBlockPos sable$mutableBlockPos = null;
    @Unique
    private Vector3d sable$playerCenter = new Vector3d();
    @Unique
    private Vector3d sable$playerSize = new Vector3d();

    @Unique
    private boolean sable$finishComputing = false; //in case another mod is recursively calling the function, like Valkerien skies for example, don't recompute

    @WrapOperation(method = "updateFluidHeightAndDoFluidPushing()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;"))
    private AABB returnSubLevelBoundingBoxAndInit(Entity instance, Operation<AABB> original,
                                                  @Share("localPlayerBox") LocalRef<Quaterniond> localPlayerBox,
                                                  @Share("playerBox") LocalRef<OrientedBoundingBox3d> playerBox,
                                                  @Share("fluidBox") LocalRef<OrientedBoundingBox3d> fluidBox,
                                                  @Share("minYVertex") LocalDoubleRef minYVertex,
                                                  @Share("hasComputedMinYVertex") LocalBooleanRef hasComputedMinYVertex) {
        if (this.sable$finishComputing || (this.sable$sl == null))
            return original.call(instance);
        this.sable$globalBound.transformInverse(this.sable$lastPose, this.sable$localBound);
        localPlayerBox.set(this.sable$lastPose.orientation().conjugate(this.sable$playerOrientation));
        localPlayerBox.get().rotateY(SubLevelEntityCollision.getHitBoxYaw(this.sable$lastPose));
        playerBox.set(new OrientedBoundingBox3d(this.sable$lastPose.transformPositionInverse(this.sable$globalBound.center(this.sable$playerCenter)), this.sable$globalBound.size(this.sable$playerSize), localPlayerBox.get(), this.sable$jomlSink));
        fluidBox.set(new OrientedBoundingBox3d(new Vector3d(), new Vector3d(1.0), JOMLConversion.QUAT_IDENTITY, this.sable$jomlSink));
        minYVertex.set(Float.MAX_VALUE);
        hasComputedMinYVertex.set(false);
        return this.sable$localBound.toMojang();
    }

    @WrapOperation(method = "updateFluidHeightAndDoFluidPushing()V", at = @At(value = "NEW", target = "()Lnet/minecraft/core/BlockPos$MutableBlockPos;"))
    private BlockPos.MutableBlockPos returnPreAllocedMutableBlockPos(Operation<BlockPos.MutableBlockPos> original) {
        if (this.sable$finishComputing)
            return original.call();
        if (this.sable$mutableBlockPos == null )
            return this.sable$mutableBlockPos = original.call();
        return this.sable$mutableBlockPos;
    }

    @Inject(method = "updateFluidHeightAndDoFluidPushing()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/material/FluidState;getHeight(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F"))
    private void calculateMinYVertex(final CallbackInfo ci, @Share("minYVertex") LocalDoubleRef minYVertex,
                                     @Share("hasComputedMinYVertex") LocalBooleanRef hasComputedMinYVertex,
                                     @Share("playerBox") LocalRef<OrientedBoundingBox3d> playerBox,
                                     @Share("fluidBox") LocalRef<OrientedBoundingBox3d> fluidBox) {
        if (this.sable$finishComputing || hasComputedMinYVertex.get() || this.sable$jomlSink == null)
            return;
        final Vector3d[] vertices = playerBox.get().vertices(this.sable$jomlSink.a);

        for (final Vector3d vertex : vertices) {
            minYVertex.set(Math.min(minYVertex.get(), vertex.y));
        }

        hasComputedMinYVertex.set(true);
    }

    @WrapOperation(method = "updateFluidHeightAndDoFluidPushing()V", at = @At(value = "FIELD", target = "Lnet/minecraft/world/phys/AABB;minY:D", opcode = Opcodes.GETFIELD, ordinal = 1))
    private double returnMinYVertext(AABB instance, Operation<Double> original,
                                     @Local LocalRef<Object2ObjectMap> localIntrasic,
                                     @Share("minYVertex") LocalDoubleRef minYVertex,
                                     @Share("hasComputedMinYVertex") LocalBooleanRef hasComputedMinYVertex) {
        if (this.sable$finishComputing || !hasComputedMinYVertex.get())
            return original.call(instance);
        return minYVertex.get();
    }

    @Definition(id = "minY", field = "Lnet/minecraft/world/phys/AABB;minY:D")
    @Definition(id = "aabb", local = @Local(type = AABB.class, ordinal = 0))
    @Expression("? >= aabb.minY")
    @ModifyExpressionValue(method = "updateFluidHeightAndDoFluidPushing()V", at = @At("MIXINEXTRAS:EXPRESSION"))
    private boolean willThisWorks(boolean original, @Share("playerBox") LocalRef<OrientedBoundingBox3d> playerBox, @Share("fluidBox") LocalRef<OrientedBoundingBox3d> fluidBox) {
        if (this.sable$finishComputing || playerBox.get() == null)
            return original;
        if (!original)
            return false;

        fluidBox.get().getPosition().set(sable$mutableBlockPos.getX() + 0.5, sable$mutableBlockPos.getY() + 0.5, sable$mutableBlockPos.getZ() + 0.5);
        return (OrientedBoundingBox3d.sat(playerBox.get(), fluidBox.get()).lengthSquared() > 0.0);
    }

    @WrapOperation(method = "updateFluidHeightAndDoFluidPushing()V", at = @At(value = "FIELD", target = "Lnet/minecraft/world/phys/AABB;minY:D", opcode = Opcodes.GETFIELD, ordinal = 2))
    private double returnMinYVertext2(AABB instance, Operation<Double> original,
                                     @Local LocalRef<Object2ObjectMap> localIntrasic,
                                     @Share("minYVertex") LocalDoubleRef minYVertex,
                                     @Share("hasComputedMinYVertex") LocalBooleanRef hasComputedMinYVertex) {
        if (this.sable$finishComputing || !hasComputedMinYVertex.get())
            return original.call(instance);
        final ActiveSableCompanion helper = Sable.HELPER;
        if (Sable.HELPER.getTrackingSubLevel(Entity.class.cast(this)) == null && helper.getContaining(Entity.class.cast(this)) != this.sable$sl) {
            ((EntityMovementExtension) this).sable$setTrackingSubLevel(this.sable$sl);
        }
        return minYVertex.get();
    }

    @WrapOperation(method = "updateFluidHeightAndDoFluidPushing()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;add(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 modifyFlowPos(Vec3 instance, Vec3 vec, Operation<Vec3> original) {
        if (this.sable$finishComputing || this.sable$lastPose == null)
            return original.call(instance, vec);
        return original.call(instance, this.sable$lastPose.transformNormal(vec));
    }

    /**
     * inject at {@code if (interimCalcs != null) interimCalcs.foreach(...)}
     * the {@code if (interimCalcs == null)} check inside the loop is actually a {@code Opcodes.IFNONNULL}
     **/
    @Inject(method = "updateFluidHeightAndDoFluidPushing()V", at = @At(value = "JUMP", opcode = Opcodes.IFNULL, shift = At.Shift.BY, by = -1), cancellable = true, locals = LocalCapture.CAPTURE_FAILHARD)
    private void includeSublevel(CallbackInfo ci, AABB aabb, @Local LocalRef<Object2ObjectMap> localIntrasic) {
        if (this.sable$finishComputing)
            return;

        if (this.sable$sl != null) {
            if (localIntrasic.get() != null) {
                if (this.interimCalcs == null)
                    this.interimCalcs = new Object2ObjectArrayMap(localIntrasic.get());
                else
                    this.interimCalcs.putAll(localIntrasic.get());
            }
            ci.cancel();
            return;
        }

        this.interimCalcs = localIntrasic.get();
        this.sable$jomlSink = ((LevelExtension) this.level).sable$getJOMLSink();

        final ActiveSableCompanion helper = Sable.HELPER;
        this.sable$globalBound.set(aabb);
        final Iterable<SubLevel> intersecting = helper.getAllIntersecting(this.level, this.sable$globalBound);
        for (final SubLevel subLevel : intersecting) {
            this.sable$sl = subLevel;
            this.sable$lastPose = subLevel.lastPose();
            this.updateFluidHeightAndDoFluidPushing();
        }
        localIntrasic.set(this.interimCalcs);
        this.sable$finishComputing = true;
    }

    @Inject(method = "updateFluidHeightAndDoFluidPushing()V", at = @At("TAIL"))
    private void resetStateMachine(CallbackInfo ci) {
        this.sable$finishComputing = false;
        this.sable$sl = null;
        this.sable$lastPose = null;
        this.interimCalcs = null;
        this.sable$jomlSink = null;
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
