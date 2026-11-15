package dev.ryanhcode.sable.mixin.entity.entity_kicking;

import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

/**
 * Mixin into {@link net.minecraft.world.level.block.Block} to transform the item entity's initial velocity when dropping items from blocks inside sub-levels.
 */
@Mixin(Block.class)
public abstract class BlockMixin {

    @Shadow
    private static void popResource(final Level arg, final Supplier<ItemEntity> supplier, final ItemStack arg2) {
    }

    @Inject(method = "popResource(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;popResource(Lnet/minecraft/world/level/Level;Ljava/util/function/Supplier;Lnet/minecraft/world/item/ItemStack;)V", shift = At.Shift.BEFORE), cancellable = true)
    private static void sable$popResourceFromFace(final Level level, final BlockPos blockPos, final ItemStack itemStack, final CallbackInfo ci, @Local(ordinal = 1) final double x, @Local(ordinal = 2) final double y, @Local(ordinal = 3) final double z) {
        final SubLevel subLevel = Sable.HELPER.getContaining(level, blockPos);

        if (subLevel != null) {
            popResource(level, () -> {
                final ItemEntity itemEntity = new ItemEntity(level, x, y, z, itemStack);

                Vec3 deltaMovement = itemEntity.getDeltaMovement();

                deltaMovement = subLevel.logicalPose().transformNormalInverse(deltaMovement);

                itemEntity.setDeltaMovement(deltaMovement);

                return itemEntity;
            }, itemStack);

            ci.cancel();
        }
    }

}
