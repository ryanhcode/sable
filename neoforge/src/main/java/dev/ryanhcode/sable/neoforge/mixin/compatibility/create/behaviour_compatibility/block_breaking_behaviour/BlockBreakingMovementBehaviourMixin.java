package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.behaviour_compatibility.block_breaking_behaviour;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.kinetics.base.BlockBreakingMovementBehaviour;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.neoforge.mixinhelper.compatibility.create.block_breakers.SubLevelBlockBreakingUtility;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.createmod.catnip.math.VecHelper;
import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into {@link com.simibubi.create.content.kinetics.base.BlockBreakingMovementBehaviour} so contraption block breaking can locate targets in sub-levels.
 */

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

        //make sure we're actually starting to break something
        if (context.stall && (context.data.contains("BreakingPos"))) {
            final Vector3d checkPos = JOMLConversion.toJOML(context.position);

            //project our position into the real world
            final SubLevel parentSublevel = Sable.HELPER.getContaining(context.world, context.position);
            if (parentSublevel != null) {
                parentSublevel.logicalPose().transformPosition(checkPos);
            }

            //project our position into the target's plot
            final SubLevel targetSublevel = Sable.HELPER.getContaining(context.world, NbtUtils.readBlockPos(context.data, "BreakingPos").orElseThrow());
            if (targetSublevel != null) {
                targetSublevel.logicalPose().transformPositionInverse(checkPos);
            }

            //save the current projected position to check movement distance against
            context.data.put("ProjectedPos", VecHelper.writeNBT(JOMLConversion.toMojang(checkPos)));
        }
    }

    @Inject(method = "cancelStall", at = @At("TAIL"))
    public void sable$removeProjected(final MovementContext context, final CallbackInfo ci) {
        context.data.remove("ProjectedPos");
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    public void sable$testProjectedPosDist(final MovementContext context, final CallbackInfo ci) {
        final CompoundTag data = context.data;

        //kind of a work-around to not require injecting into every place where BreakingPos is removed.
        if (!context.data.contains("BreakingPos")) {
            data.remove("ProjectedPos");
            return;
        }

        final Vec3 sublevelLocalCenter = context.contraption.entity.toGlobalVector(context.localPos.getCenter(), 1);
        if (data.contains("ProjectedPos") && Sable.HELPER.distanceSquaredWithSubLevels(context.world, VecHelper.readNBT(data.getList("ProjectedPos", Tag.TAG_DOUBLE)), sublevelLocalCenter) > 2*2) {
            final BlockPos blockPos = NbtUtils.readBlockPos(data, "BreakingPos").orElse(null);

            data.remove("Progress");
            data.remove("TicksUntilNextProgress");
            data.remove("BreakingPos");
            data.remove("LastPos");
            data.remove("WaitingTicks");
            data.remove("ProjectedPos");

            context.stall = false;
            if (blockPos != null) {
                context.world.destroyBlockProgress(data.getInt("BreakerId"), blockPos, -1);
            }

            ci.cancel();
        }
    }
}
