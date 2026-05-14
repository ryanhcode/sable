package dev.ryanhcode.sable.mixin.entity.entity_tracking;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public class TrackedEntityMixin {

    @WrapOperation(method = "updatePlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;position()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 sable$trackSubLevelEntities(final Entity instance, final Operation<Vec3> original) {
        final Vec3 pos = original.call(instance);
        final SubLevel subLevel = Sable.HELPER.getContaining(instance.level(), pos);

        if (subLevel != null) {
            return subLevel.logicalPose().transformPosition(pos);
        } else {
            return pos;
        }
    }
}
