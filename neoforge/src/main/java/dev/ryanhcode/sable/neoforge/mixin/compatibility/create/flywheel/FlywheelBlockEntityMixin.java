package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.flywheel;

import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.flywheel.FlywheelBlockEntity;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelReactionWheel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into {@link com.simibubi.create.content.kinetics.flywheel.FlywheelBlockEntity} to provide smoothed angular velocity for sub-level physics.
 */

@Mixin(FlywheelBlockEntity.class)
public abstract class FlywheelBlockEntityMixin extends KineticBlockEntity implements BlockEntitySubLevelReactionWheel {

    @Unique float sable$smoothedSpeed = 0;
    public FlywheelBlockEntityMixin(BlockEntityType<?> arg, BlockPos arg2, BlockState arg3) {
        super(arg, arg2, arg3);
    }

    @Inject(method = "tick",at = @At("HEAD"))
    public void sable$tick(CallbackInfo ci)
    {
        sable$smoothedSpeed += (speed - sable$smoothedSpeed) / 32f;
    }

    @Inject(method = "write",at = @At("TAIL"))
    public void sable$write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci)
    {
        compound.putFloat("SmoothedSpeed",sable$smoothedSpeed);
    }
    @Inject(method = "read",at = @At("TAIL"))
    public void sable$read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci)
    {
        sable$smoothedSpeed = compound.getFloat("SmoothedSpeed");
    }

    @Override
    public void sable$getAngularVelocity(Vector3d v) {
        Direction.Axis axis = ((IRotate) getBlockState()
                .getBlock()).getRotationAxis(getBlockState());
        Direction dir = Direction.get(Direction.AxisDirection.NEGATIVE,axis);
        float angularSpeed = sable$smoothedSpeed * (float)Math.TAU / 60f;
        v.set(dir.getStepX(),dir.getStepY(),dir.getStepZ()).mul(angularSpeed);
    }
}
