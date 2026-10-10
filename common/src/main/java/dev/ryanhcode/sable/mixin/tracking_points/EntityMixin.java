package dev.ryanhcode.sable.mixin.tracking_points;

import dev.ryanhcode.sable.mixinterface.player_freezing.PlayerFreezeExtension;
import dev.ryanhcode.sable.sublevel.tracking_points.SubLevelTrackingPointSavedData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into {@link net.minecraft.world.entity.Entity} to restore a player's position from a saved tracking point on load, freezing them into the corresponding sub-level.
 */
@Mixin(Entity.class)
public abstract class EntityMixin {

    @Shadow private Level level;

    @Shadow public abstract void setPosRaw(double d, double e, double f);

    @Inject(method = "load", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;setPosRaw(DDD)V", shift = At.Shift.AFTER))
    private void sable$load(final CompoundTag compoundTag, final CallbackInfo ci) {
        if (compoundTag.contains("LoginPoint")) {
            final SubLevelTrackingPointSavedData data = SubLevelTrackingPointSavedData.getOrLoad((ServerLevel) this.level);
            final SubLevelTrackingPointSavedData.TakenLoginPoint point = data.take(compoundTag.getUUID("LoginPoint"), true);

            if (point != null) {
                final Vector3dc position = point.position();
                this.setPosRaw(position.x(), position.y(), position.z());

                if (point.subLevelId() != null && this instanceof final PlayerFreezeExtension extension) {
                    extension.sable$freezeTo(point.subLevelId(), point.localAnchor().add(0.0, 0.2, 0.0));
                }

                // kick them out of vehicle
                compoundTag.remove("RootVehicle");
            }
        }
    }
}
