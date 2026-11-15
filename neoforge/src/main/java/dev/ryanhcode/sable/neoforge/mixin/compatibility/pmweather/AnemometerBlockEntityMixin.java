package dev.ryanhcode.sable.neoforge.mixin.compatibility.pmweather;

import dev.protomanly.pmweather.block.entity.AnemometerBlockEntity;
import dev.protomanly.pmweather.weather.WindEngine;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Mixin into {@link dev.protomanly.pmweather.block.entity.AnemometerBlockEntity} (Protomanly's Weather) to query wind from sub-level positions.
 */

@Mixin(AnemometerBlockEntity.class)
public class AnemometerBlockEntityMixin {

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Ldev/protomanly/pmweather/weather/WindEngine;getWind(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/Level;)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 sable$redirectGetWind(final BlockPos position, final Level level) {
        final Vec3 pos = Sable.HELPER.projectOutOfSubLevel(level, new Vec3(position.getX(), position.getY() + 1, position.getZ()));
        return WindEngine.getWind(pos, level);
    }
}