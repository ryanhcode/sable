package dev.ryanhcode.sable.mixin.entity.entity_sublevel_collision;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractMinecart.class)
public abstract class AbstractMinecartMixin extends Entity {

    public AbstractMinecartMixin(final EntityType<?> entityType, final Level level) {
        super(entityType, level);
    }

    @Shadow public abstract boolean isOnRails();

    @Inject(method = "tick", at = @At("TAIL"))
    private void sable$postTick(final CallbackInfo ci) {
        if (this.level().isClientSide) return;

        final SubLevel containingSubLevel = Sable.HELPER.getContaining(this);
        if (containingSubLevel == null) return;

        if (this.isOnRails()) return;

        EntitySubLevelUtil.kickEntity(containingSubLevel, this);
    }
}
