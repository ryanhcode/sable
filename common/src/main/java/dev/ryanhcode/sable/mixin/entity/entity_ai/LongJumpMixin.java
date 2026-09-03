package dev.ryanhcode.sable.mixin.entity.entity_ai;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.monster.breeze.LongJump;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LongJump.class)
public class LongJumpMixin {
    @WrapOperation(method = "canJumpFromCurrentPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/monster/breeze/Breeze;blockPosition()Lnet/minecraft/core/BlockPos;"))
    private static BlockPos sable$subLevelBlockPosition(final Breeze breeze, final Operation<BlockPos> original) {
        final SubLevel subLevel = Sable.HELPER.getTrackingSubLevel(breeze);
        if (subLevel == null) return original.call(breeze);

        return BlockPos.containing(subLevel.logicalPose().transformPositionInverse(breeze.position()));
    }

    @WrapOperation(method = "canRun", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/monster/breeze/BreezeUtil;hasLineOfSight(Lnet/minecraft/world/entity/monster/breeze/Breeze;Lnet/minecraft/world/phys/Vec3;)Z"))
    private static boolean sable$lineOfSightForSubLevels(final Breeze breeze, final Vec3 pos, final Operation<Boolean> original) {
        if (original.call(breeze, pos)) return true;

        final LivingEntity target = breeze.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
        if (target == null) return false;
        final SubLevel subLevel = Sable.HELPER.getTrackingSubLevel(target);
        if (subLevel == null) return false;

        return subLevel.getLevel().clip(
                new ClipContext(target.position(), pos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                        breeze)).getType() == HitResult.Type.MISS;
    }

    @WrapOperation(method = "canRun", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/Brain;setMemory(Lnet/minecraft/world/entity/ai/memory/MemoryModuleType;Ljava/lang/Object;)V"))
    private static void sable$transformJumpTarget(final Brain<Breeze> instance, final MemoryModuleType<?> memoryType, final Object memoryValue, final Operation<Void> original, final ServerLevel level, final Breeze breeze) {
        if (memoryType == MemoryModuleType.BREEZE_JUMP_TARGET && memoryValue instanceof final BlockPos pos) {
            final LivingEntity target = breeze.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
            if (target != null) {
                final SubLevel subLevel = Sable.HELPER.getTrackingSubLevel(target);
                if (subLevel != null) {
                    final Vec3 physicalVec = subLevel.logicalPose().transformPosition(Vec3.atBottomCenterOf(pos));
                    original.call(instance, memoryType, BlockPos.containing(physicalVec));
                    return;
                }
            }
        }

        original.call(instance, memoryType, memoryValue);
    }
}
