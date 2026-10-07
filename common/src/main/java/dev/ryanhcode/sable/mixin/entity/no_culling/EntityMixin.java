package dev.ryanhcode.sable.mixin.entity.no_culling;

import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void sable$disableCullingForRetained(final EntityType<?> type, final Level level, final CallbackInfo ci) {
        final Entity self = (Entity) (Object) this;
        if (!EntitySubLevelUtil.shouldKick(self)) {
            self.noCulling = true;
        }
    }
}