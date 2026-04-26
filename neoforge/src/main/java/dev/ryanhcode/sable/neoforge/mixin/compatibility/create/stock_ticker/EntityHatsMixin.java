package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.stock_ticker;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.equipment.hats.EntityHats;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EntityHats.class)
public class EntityHatsMixin {

    @WrapOperation(method = "getLogisticsHatFor", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;blockPosition()Lnet/minecraft/core/BlockPos;"))
    private static BlockPos sable$getStockTickerPosition(final LivingEntity instance, Operation<BlockPos> original) {
        final Entity vehicle = instance.getRootVehicle();

        if (Sable.HELPER.getContaining(vehicle) != null) {
            if (vehicle instanceof  LivingEntity lv)
                return original.call(lv);
            return vehicle.blockPosition();
        }

        return original.call(instance);
    }
}
