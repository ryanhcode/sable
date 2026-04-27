package dev.ryanhcode.sable.mixin.entity.entity_sublevel_collision;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin extends Entity {

    public ItemEntityMixin(final EntityType<?> entityType, final Level level) {
        super(entityType, level);
    }

    /**
     * if we're tracking a sub-level, force us to tick more often
     */
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;horizontalDistanceSqr()D"))
    private double sable$shouldTickPhysics(final Vec3 instance) {
        if (Sable.HELPER.getTrackingSubLevel(this) != null)
            return 1.0;

        return instance.horizontalDistance();
    }

}
