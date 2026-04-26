package dev.ryanhcode.sable.mixin.portal;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(NetherPortalBlock.class)
public class NetherPortalBlockMixin {

    @WrapOperation(method = "getPortalDestination", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/border/WorldBorder;clampToBounds(DDD)Lnet/minecraft/core/BlockPos;"))
    private BlockPos sable$getPortalDestination(final WorldBorder instance,
                                                final double x,
                                                final double y,
                                                final double z,
                                                Operation<BlockPos> original,
                                                @Local(argsOnly = true) final Entity entity,
                                                @Local(ordinal = 0) final double multiplier) {
        final Vec3 position = new Vec3(entity.getX(), entity.getY(), entity.getZ());

        final Vec3 globalPos = Sable.HELPER.projectOutOfSubLevel(entity.level(), position);

        return original.call(instance,
                globalPos.x * multiplier,
                globalPos.y * multiplier,
                globalPos.z * multiplier
        );
    }

}
