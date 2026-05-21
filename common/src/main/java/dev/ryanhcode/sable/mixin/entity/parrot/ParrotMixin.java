package dev.ryanhcode.sable.mixin.entity.parrot;

import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.animal.ShoulderRidingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Parrot.class)
public abstract class ParrotMixin extends ShoulderRidingEntity {

    protected ParrotMixin(final EntityType<? extends ShoulderRidingEntity> entityType, final Level level) {
        super(entityType, level);
    }

    @Redirect(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;closerToCenterThan(Lnet/minecraft/core/Position;D)Z"))
    public boolean closerToCenterThan(final BlockPos instance, final Position position, final double distance) {
        return Sable.HELPER.distanceSquaredWithSubLevels(this.level(), position, instance.getX() + 0.5, instance.getY() + 0.5, instance.getZ() + 0.5) < distance * distance;
    }
}
