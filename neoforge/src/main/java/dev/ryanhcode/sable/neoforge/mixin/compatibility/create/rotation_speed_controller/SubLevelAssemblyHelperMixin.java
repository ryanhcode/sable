package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.rotation_speed_controller;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.kinetics.speedController.SpeedControllerBlock;
import com.simibubi.create.content.kinetics.speedController.SpeedControllerBlockEntity;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = SubLevelAssemblyHelper.class, remap = false)
public class SubLevelAssemblyHelperMixin {

    @WrapOperation(method = "moveBlocks", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/BlockEntity;saveWithFullMetadata(Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/nbt/CompoundTag;"))
    private static CompoundTag sable$rotateControllerSpeed(final BlockEntity instance, final HolderLookup.Provider registries,
                                                           final Operation<CompoundTag> original,
                                                           @Local(argsOnly = true) final SubLevelAssemblyHelper.AssemblyTransform transform) {
        final CompoundTag tag = original.call(instance, registries);
        if (!(instance instanceof final SpeedControllerBlockEntity controller)
                || transform.getRotation() == Rotation.NONE || !tag.contains("ScrollValue", Tag.TAG_INT)) {
            return tag;
        }

        final BlockPos source = controller.source;
        if (source == null) {
            return tag;
        }

        final Direction.Axis shaftAxis = controller.getBlockState().getValue(SpeedControllerBlock.HORIZONTAL_AXIS);
        final Direction shaftDirection = Direction.get(Direction.AxisDirection.POSITIVE, shaftAxis);
        final Direction.Axis outputAxis;
        if (source.equals(controller.getBlockPos().above())) {
            // The large cog supplies power, so the setting controls the controller's shaft.
            outputAxis = shaftAxis;
        } else if (source.equals(controller.getBlockPos().relative(shaftDirection))
                || source.equals(controller.getBlockPos().relative(shaftDirection.getOpposite()))) {
            // The shaft supplies power, so the setting controls the perpendicular large cog.
            outputAxis = shaftAxis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
        } else {
            return tag;
        }

        // BlockState stores an unsigned axis, but Create's speed is signed along its positive direction.
        final Direction positiveOutput = Direction.get(Direction.AxisDirection.POSITIVE, outputAxis);
        if (transform.getRotation().rotate(positiveOutput).getAxisDirection() == Direction.AxisDirection.NEGATIVE) {
            tag.putInt("ScrollValue", -tag.getInt("ScrollValue"));
        }
        return tag;
    }
}
