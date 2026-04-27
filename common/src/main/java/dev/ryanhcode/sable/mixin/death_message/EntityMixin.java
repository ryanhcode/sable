package dev.ryanhcode.sable.mixin.death_message;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin {

    @Inject(method = "tick", at = @At("RETURN"))
    private void sable$updateLastSubLevelId(final CallbackInfo ci) {
        final Entity self = (Entity) (Object) this;

        if (Sable.HELPER.getTrackingSubLevel(self) == null && self.onGround()) {
            ((EntityMovementExtension) self).sable$setLastTrackingSubLevelID(null);
        }
    }

}
