package dev.ryanhcode.sable.mixin.entity.entity_rotations_and_riding;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fixes the bounding box used for touching nearby entities when riding an entity mounted to a sub-level
 */
@Mixin(Player.class)
public abstract class PlayerMixin extends LivingEntity {

    protected PlayerMixin(final EntityType<? extends LivingEntity> entityType, final Level level) {
        super(entityType, level);
    }

    @Inject(method = "travel", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getDeltaMovement()Lnet/minecraft/world/phys/Vec3;", ordinal = 1))
    private void sable$storeUpDeltaMovement(final Vec3 vec3,
                                            final CallbackInfo ci,
                                            @Share("upDir") final LocalRef<Vector3d> upDir,
                                            @Share("upDeltaMovement") final LocalRef<Vector3d> upDeltaMovement) {
        final Quaterniondc orientation = EntitySubLevelUtil.getCustomEntityOrientation(this, 1.0f);
        if (orientation == null) {
            return;
        }

        final Vector3d dir = orientation.transform(new Vector3d(OrientedBoundingBox3d.UP));
        upDir.set(new Vector3d(dir));

        final Vec3 deltaMovement = this.getDeltaMovement();
        upDeltaMovement.set(dir.mul(dir.dot(deltaMovement.x, deltaMovement.y, deltaMovement.z)));
    }

    @WrapOperation(method = "travel", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;setDeltaMovement(DDD)V"))
    private void sable$modifyTravelSetDeltaMovement(final Player instance,
                                                    final double x,
                                                    final double y,
                                                    final double z,
                                                    Operation<Void> original,
                                                    @Share("upDir") final LocalRef<Vector3d> upDir,
                                                    @Share("upDeltaMovement") final LocalRef<Vector3d> upDeltaMovement) {
        if (upDeltaMovement.get() == null) {
            original.call(instance, x, y, z);
            return;
        }

        final Vec3 deltaMovement = this.getDeltaMovement();
        final double dot = upDir.get().dot(deltaMovement.x, deltaMovement.y, deltaMovement.z);

        final double scalar = 0.6;
        final Vec3 finalDeltaMovement = deltaMovement
                .subtract(dot * upDir.get().x, dot * upDir.get().y, dot * upDir.get().z)
                .add(upDeltaMovement.get().x * scalar, upDeltaMovement.get().y * scalar, upDeltaMovement.get().z * scalar);
        original.call(instance, finalDeltaMovement.x, finalDeltaMovement.y, finalDeltaMovement.z);
    }

    @WrapOperation(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/AABB;minmax(Lnet/minecraft/world/phys/AABB;)Lnet/minecraft/world/phys/AABB;"))
    public AABB sable$fixRidingBoundingBox(final AABB usBoundingBox, AABB vehicleBoundingBox, Operation<AABB> original) {
        final Entity vehicle = this.getVehicle();
        final SubLevel vehicleSubLevel = Sable.HELPER.getContaining(vehicle);
        if (vehicleSubLevel == null) return original.call(usBoundingBox, vehicleBoundingBox);

        final BoundingBox3d bb = new BoundingBox3d(vehicleBoundingBox);
        vehicleBoundingBox = bb.transform(vehicleSubLevel.logicalPose(), bb).toMojang();

        return original.call(usBoundingBox, vehicleBoundingBox);
    }
}
