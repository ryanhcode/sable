package dev.ryanhcode.sable.neoforge.mixin.compatibility.pmweather;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.protomanly.pmweather.block.AnemometerBlock;
import dev.protomanly.pmweather.weather.WindEngine;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AnemometerBlock.class)
public class AnemometerBlockMixin {

    @WrapOperation(method = "useWithoutItem", at = @At(value = "INVOKE", target = "Ldev/protomanly/pmweather/weather/WindEngine;getWind(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/Level;)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 sable$redirectGetWind(final BlockPos position, final Level level, Operation<Vec3> original) {
        final Vec3 pos = Sable.HELPER.projectOutOfSubLevel(level, new Vec3(position.getX(), position.getY() + 1, position.getZ()));
        return original.call(BlockPos.containing(pos), level);
    }
}