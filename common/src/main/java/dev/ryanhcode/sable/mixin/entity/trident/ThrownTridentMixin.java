package dev.ryanhcode.sable.mixin.entity.trident;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into {@link net.minecraft.world.entity.projectile.ThrownTrident} to kick tridents out of sub-levels when they start returning.
 */
@Mixin(ThrownTrident.class)
public abstract class ThrownTridentMixin extends Entity {
    public ThrownTridentMixin(final EntityType<?> entityType, final Level level) {
        super(entityType, level);
    }

    @Inject(method = "tick", at= @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/projectile/ThrownTrident;setNoPhysics(Z)V"))
    private void sable$startReturning(final CallbackInfo ci) {
        final SubLevel subLevel = Sable.HELPER.getContaining(this);

        if (subLevel != null) {
            EntitySubLevelUtil.kickEntity(subLevel, this);
        }
    }
}
