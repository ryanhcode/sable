package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.behaviour_compatibility.block_breaking_behaviour;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.actors.roller.RollerMovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.kinetics.base.BlockBreakingMovementBehaviour;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.neoforge.mixinhelper.compatibility.create.block_breakers.SubLevelBlockBreakingUtility;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockBreakingMovementBehaviour.class)
public abstract class BlockBreakingMovementBehaviourMixin implements MovementBehaviour {

    @Shadow
    public abstract boolean canBreak(Level world, BlockPos breakingPos, BlockState state);

    @WrapMethod(method = "visitNewPosition")
    public void sable$checkPosition(final MovementContext context, final BlockPos pos, final Operation<Void> original) {
        if (!context.stall) {
            original.call(context, pos);

            if (!context.stall) {
                final Vec3 localCenter = context.localPos.getCenter();
                final Vec3 sublevelLocalCenter = context.contraption.entity.toGlobalVector(localCenter, 1);
                final Vec3 subLevelLocalDir = context.rotation.apply(this.getActiveAreaOffset(context));


                final BlockPos breakingPosWSublevel = SubLevelBlockBreakingUtility.findBreakingPos(
                        (blockPos, state) -> this.canBreak(context.world, blockPos, state),
                        Sable.HELPER.getContaining(context.world, context.contraption.anchor),
                        context.world,
                        subLevelLocalDir,
                        sublevelLocalCenter,
                        pos
                );

                original.call(context, breakingPosWSublevel);
            }
        }
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    public void sable$testBreakingPosDist(final MovementContext context, final CallbackInfo ci) {
        if ((Object) this instanceof RollerMovementBehaviour) {
            return;
        }

        final CompoundTag data = context.data;
        if (data.contains("BreakingPos") || data.contains("LastPos")) {
            final BlockPos blockPos = NbtUtils.readBlockPos(data, "BreakingPos").orElseGet(() -> NbtUtils.readBlockPos(data, "LastPos").orElse(null));

            if (blockPos != null) {
                final Vec3 localCenter = context.localPos.getCenter();

                Vec3 sublevelLocalCenter = context.contraption.entity.toGlobalVector(localCenter, 1);
                Vec3 targetCenter = blockPos.getCenter();

                final ActiveSableCompanion helper = Sable.HELPER;
                final SubLevel parentSublevel = helper.getContaining(context.world, context.contraption.anchor);
                final SubLevel targetSubLevel = helper.getContaining(context.world, blockPos);

                if (parentSublevel != null) {
                    sublevelLocalCenter = parentSublevel.logicalPose().transformPosition(sublevelLocalCenter);
                }

                if (targetSubLevel != null) {
                    targetCenter = targetSubLevel.logicalPose().transformPosition(targetCenter);
                }

                final double allowDistanceSqr = this.getActiveAreaOffset(context).lengthSqr() + 1;
                if (sublevelLocalCenter.distanceToSqr(targetCenter) > allowDistanceSqr ) {
                    data.remove("Progress");
                    data.remove("TicksUntilNextProgress");
                    data.remove("BreakingPos");
                    data.remove("LastPos");
                    data.remove("WaitingTicks");

                    context.stall = false;
                    context.world.destroyBlockProgress(data.getInt("BreakerId"), blockPos, -1);

                    ci.cancel();
                }
            }
        }
    }
}
