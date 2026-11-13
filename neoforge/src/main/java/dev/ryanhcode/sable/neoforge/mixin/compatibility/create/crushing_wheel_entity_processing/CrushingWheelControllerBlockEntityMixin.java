package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.crushing_wheel_entity_processing;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.kinetics.crusher.CrushingWheelControllerBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CrushingWheelControllerBlockEntity.class)
public abstract class CrushingWheelControllerBlockEntityMixin extends SmartBlockEntity {

    @Unique
    private static final double SABLE$MAX_CRUSHING_POSITION_DISTANCE_SQR = 64.0;

    @Shadow
    public Entity processingEntity;
    @Unique
    private SubLevel sable$parentSublevel = null;
    @Unique
    private boolean sable$warnedInvalidPosition;
    @Unique
    private boolean sable$warnedInvalidVelocity;

    public CrushingWheelControllerBlockEntityMixin(final BlockEntityType<?> typeIn, final BlockPos pos, final BlockState state) {
        super(typeIn, pos, state);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    public void sable$initSublevel(final CallbackInfo ci) {
        this.sable$parentSublevel = Sable.HELPER.getContaining(this);
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;"))
    public AABB sable$pushEntityLocalAABB(final Entity instance, final Operation<AABB> original) {
        final AABB boundingBox = original.call(instance);
        if (this.sable$parentSublevel != null) {
            final BoundingBox3d bb3d = new BoundingBox3d(boundingBox);
            bb3d.transformInverse(this.sable$parentSublevel.logicalPose());
            return bb3d.toMojang();
        }

        return boundingBox;
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getX()D"))
    public double sable$pushEntityLocalX(final Entity instance, final Operation<Double> original) {
        Double x = original.call(instance);
        if (this.sable$parentSublevel != null) {
            x = this.sable$parentSublevel.logicalPose().transformPositionInverse(instance.position()).x;
        }

        return x;
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getY()D"))
    public double sable$pushEntityLocalY(final Entity instance, final Operation<Double> original) {
        Double y = original.call(instance);
        if (this.sable$parentSublevel != null) {
            y = this.sable$parentSublevel.logicalPose().transformPositionInverse(instance.position()).y;
        }

        return y;
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getZ()D"))
    public double sable$pushEntityLocalZ(final Entity instance, final Operation<Double> original) {
        Double z = original.call(instance);
        if (this.sable$parentSublevel != null) {
            z = this.sable$parentSublevel.logicalPose().transformPositionInverse(instance.position()).z;
        }

        return z;
    }

    @WrapOperation(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"
            ),
            require = 1,
            allow = 1
    )
    public void sable$transformCrushingVelocity(final Entity instance, final Vec3 localVelocity, final Operation<Void> original) {
        if (this.sable$parentSublevel == null) {
            original.call(instance, localVelocity);
            return;
        }

        final Vec3 worldVelocity = this.sable$parentSublevel.logicalPose().transformNormal(localVelocity);
        if (!sable$isFinite(worldVelocity)) {
            if (!this.sable$warnedInvalidVelocity) {
                this.sable$warnedInvalidVelocity = true;
                Sable.LOGGER.error(
                        "Invalid crushing-wheel velocity transform at {} for entity {}: local={}, transformed={}",
                        this.getBlockPos(), instance.getUUID(), localVelocity, worldVelocity
                );
            }
            original.call(instance, Vec3.ZERO);
            return;
        }

        original.call(instance, worldVelocity);
    }

    @WrapOperation(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;setPos(DDD)V"
            ),
            require = 2,
            allow = 2
    )
    public void sable$transformCrushingPosition(final Entity instance, final double localX, final double localY, final double localZ, final Operation<Void> original) {
        if (this.sable$parentSublevel == null) {
            original.call(instance, localX, localY, localZ);
            return;
        }

        final Vec3 localPosition = new Vec3(localX, localY, localZ);
        final Vec3 worldPosition = this.sable$parentSublevel.logicalPose().transformPosition(localPosition);
        final boolean invalid = !sable$isFinite(worldPosition)
                || worldPosition.distanceToSqr(instance.position()) > SABLE$MAX_CRUSHING_POSITION_DISTANCE_SQR;
        if (invalid) {
            if (!this.sable$warnedInvalidPosition) {
                this.sable$warnedInvalidPosition = true;
                Sable.LOGGER.error(
                        "Blocked unsafe crushing-wheel position at {} for entity {}: local={}, transformed={}, current={}",
                        this.getBlockPos(), instance.getUUID(), localPosition, worldPosition, instance.position()
                );
            }
            return;
        }

        original.call(instance, worldPosition.x, worldPosition.y, worldPosition.z);
    }

    @Unique
    private static boolean sable$isFinite(final Vec3 vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }
}
