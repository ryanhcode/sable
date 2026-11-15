package dev.ryanhcode.sable.mixin.entity.entity_tracking;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Mixin into {@link net.minecraft.server.level.ChunkMap.TrackedEntity} to make server entity tracking transform the positions of entities inside sub-levels into global coordinates.
 */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public class TrackedEntityMixin {

    @Redirect(method = "updatePlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;position()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 sable$trackSubLevelEntities(final Entity instance) {
        final Vec3 pos = instance.position();
        final SubLevel subLevel = Sable.HELPER.getContaining(instance.level(), pos);

        if (subLevel != null) {
            return subLevel.logicalPose().transformPosition(pos);
        } else {
            return instance.position();
        }
    }
}
