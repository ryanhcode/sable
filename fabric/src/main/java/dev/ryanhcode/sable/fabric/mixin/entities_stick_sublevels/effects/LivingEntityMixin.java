package dev.ryanhcode.sable.fabric.mixin.entities_stick_sublevels.effects;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity {
    public LivingEntityMixin(final EntityType<?> entityType, final Level level) {
        super(entityType, level);
    }

    /**
     * Changes the blockpos offset to use getOnPos
     */
    @Redirect(method = "playBlockFallSound", at = @At(value = "NEW", target = "(III)Lnet/minecraft/core/BlockPos;"))
    private BlockPos sable$redirectBlockPos(final int x, final int y, final int z) {
        return this.getOnPos(0.2f);
    }
}
