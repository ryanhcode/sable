package dev.ryanhcode.sable.mixin.clip_overwrite;

import dev.ryanhcode.sable.Sable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Entity.class)
public class EntityMixin {

    @Redirect(method = "pick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getEyePosition(F)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 sable$getEyePosition(final Entity instance, final float partialTicks) {
        return Sable.HELPER.getEyePositionInterpolated(instance, partialTicks);
    }
}
