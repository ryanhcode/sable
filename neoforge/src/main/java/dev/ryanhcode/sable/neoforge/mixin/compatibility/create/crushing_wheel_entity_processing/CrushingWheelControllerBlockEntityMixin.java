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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into {@link com.simibubi.create.content.kinetics.crusher.CrushingWheelControllerBlockEntity} so the crushing wheel controller can process entities in sub-levels.
 */

@Mixin(CrushingWheelControllerBlockEntity.class)
public abstract class CrushingWheelControllerBlockEntityMixin extends SmartBlockEntity {

    @Shadow
    public Entity processingEntity;
    @Unique
    private SubLevel sable$parentSublevel = null;

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
}
