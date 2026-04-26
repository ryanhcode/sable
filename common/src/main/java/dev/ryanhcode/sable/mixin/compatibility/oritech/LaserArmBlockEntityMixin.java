package dev.ryanhcode.sable.mixin.compatibility.oritech;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import rearth.oritech.api.energy.containers.DynamicEnergyStorage;
import rearth.oritech.api.networking.NetworkedBlockEntity;
import rearth.oritech.block.entity.interaction.LaserArmBlockEntity;

import java.util.ArrayDeque;

@Mixin(LaserArmBlockEntity.class)
public abstract class LaserArmBlockEntityMixin extends NetworkedBlockEntity {

    @Shadow
    protected abstract boolean validTarget(LivingEntity entity);

    @Shadow
    private BlockPos targetDirection;

    @Shadow
    public abstract BlockPos getLaserHeadPosition();

    @Shadow
    protected abstract ArrayDeque<BlockPos> findNextAreaBlockTarget(BlockPos center, int scanDist);

    @Shadow
    @Final
    protected DynamicEnergyStorage energyStorage;

    public LaserArmBlockEntityMixin(final BlockEntityType<?> type, final BlockPos pos, final BlockState state) {
        super(type, pos, state);
    }

    @Redirect(method = "trySetNewTarget", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;distManhattan(Lnet/minecraft/core/Vec3i;)I"))
    private int test2(final BlockPos instance, final Vec3i vec3i) {
        Vec3 centerTarget = Vec3.atCenterOf(vec3i);
        Vec3 centerParent = instance.getCenter();

        centerTarget = Sable.HELPER.projectOutOfSubLevel(this.getLevel(), centerTarget);
        centerParent = Sable.HELPER.projectOutOfSubLevel(this.getLevel(), centerParent);

        return BlockPos.containing(centerTarget).distManhattan(BlockPos.containing(centerParent));
    }

    @Redirect(method = "findNextBlockBreakTarget", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;distanceTo(Lnet/minecraft/world/phys/Vec3;)D"))
    private double test4(final Vec3 instance, final Vec3 vec) {
        return Math.sqrt(Sable.HELPER.distanceSquaredWithSubLevels(this.getLevel(), instance, vec));
    }

//    @Redirect(method = "findNextBlockBreakTarget", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;subtract(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/core/BlockPos;"))
//    private BlockPos test3(BlockPos instance, Vec3i vector) {
//        instance = BlockPos.containing(Sable.HELPER.projectOutOfSubLevel(this.getLevel(), Vec3.atCenterOf(instance)));
//        vector = BlockPos.containing(Sable.HELPER.projectOutOfSubLevel(this.getLevel(), Vec3.atCenterOf(vector)));
//
//        return instance.subtract(vector);
//    }

    /**
     * soft overwrite to use center positions instead of block based positions
     */
    //TODO ensure this won't cause issues with other addons that *might* mixin into this call.
    @WrapOperation(method = "findNextBlockBreakTarget", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;atLowerCornerOf(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 sable$softOverwriteDirection(final Vec3i toCopy, final Operation<Vec3> original) {
        Vec3 centerTargetPos = this.targetDirection.getCenter();
        final SubLevel targetSublevel = Sable.HELPER.getContaining(this.getLevel(), centerTargetPos);
        if (targetSublevel != null) {
            centerTargetPos = targetSublevel.logicalPose().transformPosition(centerTargetPos);
        }

        final Vec3 centerHeadPos = this.getLaserHeadPosition().getCenter();
        final SubLevel parentSublevel = Sable.HELPER.getContaining(this.getLevel(), centerHeadPos);
        if (parentSublevel != null) {
            centerTargetPos = parentSublevel.logicalPose().transformPositionInverse(centerTargetPos);
        }

        //ensure that direction is local
        return centerTargetPos.subtract(centerHeadPos).normalize();
    }

    //TODO memory optimize this
    @WrapMethod(method = "basicRaycast")
    private BlockPos test23929(Vec3 from, Vec3 direction, final int range, final float searchOffset, final Operation<BlockPos> original) {
        final BlockPos localCast = original.call(from, direction, range, searchOffset);
        double minDistance = Double.MAX_VALUE;
        BlockPos gatheredPos = null;
        if (localCast != null) {
            gatheredPos = localCast;
            minDistance = localCast.distToCenterSqr(from);
        }

        final SubLevel parentSublevel = Sable.HELPER.getContaining(this.getLevel(), from);
        if (parentSublevel != null) {
            direction = parentSublevel.logicalPose().transformNormal(direction);
            from = parentSublevel.logicalPose().transformPosition(from);
        }

        final BlockPos globalCast = original.call(from, direction, range, searchOffset);
        if (globalCast != null) {
            final double globalDist = globalCast.distToCenterSqr(from);
            if (gatheredPos == null || globalDist < minDistance) {
                minDistance = globalDist;
                gatheredPos = globalCast;
            }
        }

        final Vec3 target = from.add(direction.scale(range));
        //TODO make these static members
        final BoundingBox3d searchBounds = new BoundingBox3d(from.x, from.y, from.z, target.x, target.y, target.z);
        final Vector3d tempOrigin = new Vector3d();
        final Vector3d tempDir = new Vector3d();
        //

        for (final SubLevel subLevel : Sable.HELPER.getAllIntersecting(this.getLevel(), searchBounds)) {
            if (subLevel == parentSublevel) {
                continue;
            }

            subLevel.logicalPose().transformPositionInverse(tempOrigin.set(from.x, from.y, from.z));
            subLevel.logicalPose().transformNormalInverse(tempDir.set(direction.x, direction.y, direction.z));

            final BlockPos sublevelCall = original.call(new Vec3(tempOrigin.x, tempOrigin.y, tempOrigin.z), new Vec3(tempDir.x, tempDir.y, tempDir.z), range, searchOffset / 2f);
            if (sublevelCall != null) {
                final double sublevelDist = sublevelCall.distToCenterSqr(tempOrigin.x, tempOrigin.y, tempOrigin.z);
                if (gatheredPos == null || sublevelDist < minDistance) {
                    minDistance = sublevelDist;
                    gatheredPos = sublevelCall;
                }
            }
        }

        return gatheredPos;
    }


}
