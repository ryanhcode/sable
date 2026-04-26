package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.deployer;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

import javax.annotation.Nullable;

@Mixin(DeployerBlockEntity.class)
public abstract class DeployerBlockEntityMixin extends SmartBlockEntity {

    public DeployerBlockEntityMixin(final BlockEntityType<?> type, final BlockPos pos, final BlockState state) {
        super(type, pos, state);
    }

    @WrapOperation(method = "start", at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(DD)D"))
    private double sable$deployerMin(final double a, final double b, Operation<Double> original, @Local(ordinal = 1) final Vec3 rayOrigin, @Local(ordinal = 0) final BlockHitResult result) {
        return original.call(Math.sqrt(Sable.HELPER.distanceSquaredWithSubLevels(this.level, result.getLocation(), rayOrigin)), b);
    }


    @ModifyArg(method = "activate", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/kinetics/deployer/DeployerHandler;activate(Lcom/simibubi/create/content/kinetics/deployer/DeployerFakePlayer;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/Vec3;Lcom/simibubi/create/content/kinetics/deployer/DeployerBlockEntity$Mode;)V"), index = 2, remap = false)
    private BlockPos sable$checkPositions(final BlockPos pos) {
        final Vec3 centerPos = Vec3.atCenterOf(pos);

        ActiveSableCompanion helper = Sable.HELPER;
        final BlockPos gatheredPos = helper.runIncludingSubLevels(this.getLevel(), centerPos, true, helper.getContaining(this), this::sable$getState);

        if (gatheredPos != null)
            return gatheredPos;

        return pos;
    }

    @Unique
    @Nullable
    private BlockPos sable$getState(final SubLevel subLevel, final BlockPos pos) {
        final Level level = this.getLevel();
        assert level != null;

        final BlockState state = level.getBlockState(pos);

        if (!state.isAir()) {
            return pos;
        } else {
            for (final Direction direction : Iterate.directions) {
                if (!level.getBlockState(pos.relative(direction)).isAir())
                    return pos;
            }
        }

        return null;
    }
}
