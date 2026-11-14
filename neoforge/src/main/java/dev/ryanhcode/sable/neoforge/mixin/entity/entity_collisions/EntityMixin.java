package dev.ryanhcode.sable.neoforge.mixin.entity.entity_collisions;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.Sable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Entity.class)
public class EntityMixin {

    // Projects the target delta out of a sublevel when looking up solid entity collisions. This fixes teleporting to sublevels causing insane amounts of lag.
    @WrapOperation(method = "collide", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/AABB;expandTowards(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/AABB;"))
    public AABB sublevelEntityCollisionFix(final AABB instance, final Vec3 vector, final Operation<AABB> original) {
        final var entity = (Entity)(Object) this;
        return original.call(instance, Sable.HELPER.projectOutOfSubLevel(entity.level(), entity.position().add(vector)));
    }
}
