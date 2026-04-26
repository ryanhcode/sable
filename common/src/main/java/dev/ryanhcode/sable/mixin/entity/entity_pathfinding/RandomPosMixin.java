package dev.ryanhcode.sable.mixin.entity.entity_pathfinding;

import dev.ryanhcode.sable.Sable;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.RandomPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RandomPos.class)
public class RandomPosMixin {

    @WrapMethod(method = "generateRandomPosTowardDirection")
    private static BlockPos randomPosIncludeSubLevel(PathfinderMob mob, int range, RandomSource random, BlockPos pos, Operation<BlockPos> original,
                                                     @Share("effectiveMobPos") LocalRef<Vec3> effectiveMobPos) {
        final SubLevel trackingSubLevel = Sable.HELPER.getTrackingSubLevel(mob);

        if (trackingSubLevel != null) {
            effectiveMobPos.set(trackingSubLevel.logicalPose().transformPositionInverse(mob.position()));
        }
        return original.call(mob, range, random, pos);
    }

    @WrapOperation(method = "generateRandomPosTowardDirection", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/PathfinderMob;getX()D"))
    private static double replaceGetX(PathfinderMob instance, Operation<Double> original, @Share("effectiveMobPos") LocalRef<Vec3> effectiveMobPos) {
        if (effectiveMobPos.get() != null)
            return effectiveMobPos.get().x;
        return original.call(instance);
    }

    @WrapOperation(method = "generateRandomPosTowardDirection", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/PathfinderMob;getZ()D"))
    private static double replaceGetZ(PathfinderMob instance, Operation<Double> original, @Share("effectiveMobPos") LocalRef<Vec3> effectiveMobPos) {
        if (effectiveMobPos.get() != null)
            return effectiveMobPos.get().z;
        return original.call(instance);
    }

    @WrapOperation(method = "generateRandomPosTowardDirection", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/PathfinderMob;getY()D"))
    private static double replaceGetY(PathfinderMob instance, Operation<Double> original, @Share("effectiveMobPos") LocalRef<Vec3> effectiveMobPos) {
        if (effectiveMobPos.get() != null)
            return effectiveMobPos.get().y;
        return original.call(instance);
    }

}
