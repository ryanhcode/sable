package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.stock_ticker;

import com.simibubi.create.content.equipment.hats.EntityHats;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Mixin into {@link com.simibubi.create.content.equipment.hats.EntityHats} to use the vehicle position when checking for stock ticker hats on sub-level contraptions.
 */

@Mixin(EntityHats.class)
public class EntityHatsMixin {

    @Redirect(method = "getLogisticsHatFor", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;blockPosition()Lnet/minecraft/core/BlockPos;"))
    private static BlockPos sable$getStockTickerPosition(final LivingEntity instance) {
        final Entity vehicle = instance.getRootVehicle();

        if (Sable.HELPER.getContaining(vehicle) != null) {
            return vehicle.blockPosition();
        }

        return instance.blockPosition();

    }
}
