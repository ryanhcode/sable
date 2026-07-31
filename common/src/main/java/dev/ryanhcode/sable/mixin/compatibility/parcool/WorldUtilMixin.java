package dev.ryanhcode.sable.mixin.compatibility.parcool;

import dev.ryanhcode.sable.mixinhelpers.SubLevelNoCollisionHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "com.alrex.parcool.utilities.WorldUtil", remap = false)
public class WorldUtilMixin {

    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;noCollision(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Z"), require = 0)
    private static boolean sable$noCollisionForEntity(final Level level, final Entity entity, final AABB aabb) {
        if (!level.noCollision(entity, aabb)) {
            return false;
        }

        return SubLevelNoCollisionHelper.noCollisionWithSubLevels(level, aabb);
    }

    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;noCollision(Lnet/minecraft/world/phys/AABB;)Z"), require = 0)
    private static boolean sable$noCollision(final Level level, final AABB aabb) {
        if (!level.noCollision(aabb)) {
            return false;
        }

        return SubLevelNoCollisionHelper.noCollisionWithSubLevels(level, aabb);
    }
}
