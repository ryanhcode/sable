package dev.ryanhcode.sable.mixin.entity.entity_sublevel_collision;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.index.SableTags;
import dev.ryanhcode.sable.mixinterface.EntityExtension;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.LevelExtension;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.LivingEntityMovementExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Iterator;
import java.util.UUID;

@Mixin(value = Entity.class, priority = 1100)
public abstract class EntityMixin implements EntityMovementExtension {

//    /**
//     * Pitch angle threshold for kicking
//     */
//    private static final double TRACKING_LOCAL_UP_ANGLE_THRESHOLD = Math.cos(Math.toRadians(30.0));

    @Shadow
    public boolean horizontalCollision;
    @Shadow
    public boolean verticalCollision;
    @Shadow
    public boolean verticalCollisionBelow;
    @Shadow
    public boolean minorHorizontalCollision;
    @Unique
    private SubLevel sable$trackingSubLevel = null;
    @Unique
    private UUID sable$lastTrackingSubLevelId = null;
    @Shadow
    private Level level;
    @Shadow
    private Vec3 position;
    @Shadow
    @Nullable
    private BlockState inBlockState;
    @Shadow
    private BlockPos blockPosition;
    @Unique
    private SubLevelEntityCollision.CollisionInfo sable$collisionInfo = null;

    @Shadow
    protected abstract Vec3 collide(Vec3 vec3);

    @Shadow
    protected abstract boolean isHorizontalCollisionMinor(Vec3 arg);

    @Unique
    private BlockPos sable$inBlockStatePos = BlockPos.ZERO;

//    @Unique
//    private Vector3d sable$trackStartUpDirection = null;
//
//    @Unique
//    private final Vector3d sable$localUpDirectionStorage = new Vector3d();

    @Shadow
    public abstract Level level();

    @WrapOperation(method = "move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;setOnGroundWithMovement(ZLnet/minecraft/world/phys/Vec3;)V"))
    public void sable$moveInject(final Entity instance, final boolean bl, final Vec3 arg, final Operation<Void> original) {
        this.horizontalCollision = this.sable$collisionInfo.horizontalCollision;
        this.verticalCollision = this.sable$collisionInfo.verticalCollision;
        this.verticalCollisionBelow = this.sable$collisionInfo.verticalCollisionBelow;
        this.minorHorizontalCollision = this.sable$collisionInfo.minorHorizontalCollision;

        original.call(instance, this.verticalCollisionBelow, arg);
    }

    @WrapOperation(method = "move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;updateEntityAfterFallOn(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;)V"))
    public void updateEntityAfterFallOn(final Block instance, final BlockGetter arg, final Entity arg2, final Operation<Void> original) {
        if (this.verticalCollision) {
            original.call(instance, arg, arg2);
        }
    }

    @Redirect(method = "move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;collide(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"))
    public Vec3 sable$collideRedirect(final Entity entity, final Vec3 collisionMotion) {
        final Entity self = (Entity) (Object) this;
        final Vec3 motion = collisionMotion;

        Vec3 velocity = Vec3.ZERO;

        if (self instanceof final LivingEntity livingEntity) {
            velocity = JOMLConversion.toMojang(((LivingEntityMovementExtension) livingEntity).sable$getInheritedVelocity());
        }

        final SubLevel preTrackingSubLevel = this.sable$trackingSubLevel;
        final Vec3 preDeltaMovement = this.getDeltaMovement();

//        if (this.sable$trackingSubLevel != null) {
        // TODO: this would be nice to prevent "swing-around" but would need to happen *in* the substep loop, so there's
        //  no ticks on a continuous tilting airship flight where we're not tracking the ship.
        //  in testing, it also doesn't fully solve the intended issue either.
//            if (this.sable$collisionInfo != null && this.sable$collisionInfo.trackingLocalUpDirection == null) {
//                final Pose3d pose = this.sable$collisionInfo.trackingSubLevel.logicalPose();
//
//                if (this.sable$trackStartUpDirection != null && pose.transformNormalInverse(OrientedBoundingBox3d.UP, this.sable$localUpDirectionStorage).dot(this.sable$trackStartUpDirection) < TRACKING_LOCAL_UP_ANGLE_THRESHOLD) {
//                    this.sable$trackingSubLevel = null;
//                }
//            }
//        }

        this.sable$collisionInfo = SubLevelEntityCollision.collide(entity, motion, velocity, ((LevelExtension) this.level).sable$getJOMLSink());
        this.sable$collisionInfo.preTrackingSubLevel = preTrackingSubLevel;
        this.sable$collisionInfo.preDeltaMovement = preDeltaMovement;

        if (this.sable$collisionInfo.trackingSubLevel != null) {
            if (this.sable$collisionInfo.verticalCollisionBelow) {
                this.sable$trackingSubLevel = this.sable$collisionInfo.trackingSubLevel;
            }

//            if (this.sable$collisionInfo.trackingLocalUpDirection != null) {
//                if (this.sable$trackStartUpDirection == null) this.sable$trackStartUpDirection = new Vector3d();
//                this.sable$trackStartUpDirection.set(this.sable$collisionInfo.trackingLocalUpDirection);
//            }
        } else if (!(entity instanceof ServerPlayer)) {
            this.sable$trackingSubLevel = null;
        }

//        if (this.sable$trackingSubLevel == null) {
//            this.sable$trackStartUpDirection = null;
//        }

        final Vec3 beforeVanillaCollision = this.sable$collisionInfo.motion;
        final Vec3 afterVanillaCollision = this.collide(beforeVanillaCollision);

        final boolean xCollision = !Mth.equal(beforeVanillaCollision.x, afterVanillaCollision.x);
        final boolean zCollision = !Mth.equal(beforeVanillaCollision.z, afterVanillaCollision.z);
        this.sable$collisionInfo.horizontalCollision |= xCollision || zCollision;

        if (beforeVanillaCollision.y != afterVanillaCollision.y) {
            this.sable$trackingSubLevel = null;
        }

        this.sable$collisionInfo.verticalCollision |= beforeVanillaCollision.y != afterVanillaCollision.y;
        this.sable$collisionInfo.verticalCollisionBelow |= this.sable$collisionInfo.verticalCollision && collisionMotion.y < 0.0;
        if (this.horizontalCollision) {
            this.sable$collisionInfo.minorHorizontalCollision = this.isHorizontalCollisionMinor(afterVanillaCollision);
        }

        if (this.sable$trackingSubLevel != null) {
            if (this.sable$trackingSubLevel.isRemoved())
                this.sable$trackingSubLevel = null;
        }

        return afterVanillaCollision;
    }

    @Inject(method = "move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V", at = @At(value = "TAIL"))
    public void sable$moveInject(final MoverType moverType, final Vec3 vec3, final CallbackInfo ci) {
        if (this.sable$collisionInfo != null) {
            this.horizontalCollision |= this.sable$collisionInfo.subLevelHorizontalCollision;
        }

        if (!(((Object) this) instanceof LivingEntity)) {
            if (this.sable$collisionInfo != null && this.sable$collisionInfo.inheritedMotion != null) {
                if (this.sable$collisionInfo.inheritedMotion.lengthSqr() > Math.pow(0.000001, 2)) {
                    this.setPos(this.position.add(((EntityExtension) this).sable$vanillaCollide(this.sable$collisionInfo.inheritedMotion)));
                }
            }
        }
    }

    @Shadow
    public abstract void setPos(Vec3 vec3);

    @Shadow
    @Nullable
    public abstract Entity getVehicle();

    @Shadow
    public abstract Vec3 getDeltaMovement();

    @Shadow
    public abstract AABB getBoundingBox();

    @Shadow
    public abstract void remove(Entity.RemovalReason removalReason);

    @Shadow
    public abstract EntityType<?> getType();

    @Shadow
    public abstract void kill();

    @Inject(method = "tick", at = @At("TAIL"))
    public void sable$tickInject(final CallbackInfo ci) {
        final ActiveSableCompanion helper = Sable.HELPER;
        final Entity vehicle = this.getVehicle();
        final SubLevel containingSubLevel = helper.getContaining((Entity) (Object) this);

        // we can't both be tracking a sub-level and be in one
        if (containingSubLevel != null) {
            this.sable$trackingSubLevel = null;
        } else if (vehicle != null) {
            final SubLevel vehicleSubLevel = helper.getContaining(vehicle);

            if (vehicleSubLevel != null) {
                this.sable$trackingSubLevel = vehicleSubLevel;
            } else {
                this.sable$trackingSubLevel = Sable.HELPER.getTrackingSubLevel(vehicle);
            }
        }

        if (this.sable$trackingSubLevel != null) {
            if (this.sable$trackingSubLevel.isRemoved())
                this.sable$trackingSubLevel = null;
        }

        // Destroy us if we're in #sable:destroy_when_leaving_plot and we've left the plot
        if (containingSubLevel != null) {
            if (!this.getBoundingBox().intersects(containingSubLevel.getPlot().getBoundingBox().toAABB().inflate(1.0)) && this.getType().is(SableTags.DESTROY_WHEN_LEAVING_PLOT)) {
                this.kill();
            }
        }
    }

    /**
     * @return the position that the state returned by getInBlockState was gotten from
     */
    @Override
    public BlockPos sable$getInBlockStatePos() {
        return this.sable$inBlockStatePos;
    }

    /**
     * @author RyanH
     * @reason Take into account sub-levels
     */
    @Overwrite
    public BlockState getInBlockState() {
        final Level level = this.level();

        if (this.inBlockState == null || this.sable$trackingSubLevel != null) {
            this.inBlockState = level.getBlockState(this.blockPosition);
            this.sable$inBlockStatePos = this.blockPosition;

            final Iterable<SubLevel> intersecting = Sable.HELPER.getAllIntersecting(this.level, new BoundingBox3d(this.blockPosition));

            final Iterator<SubLevel> iter = intersecting.iterator();
            while (this.inBlockState.isAir() && iter.hasNext()) {
                final SubLevel subLevel = iter.next();
                final BlockPos localBlockPos = BlockPos.containing(subLevel.logicalPose().transformPositionInverse(this.position.add(0.0, 0.001, 0.0)));
                this.inBlockState = level.getBlockState(localBlockPos);
                this.sable$inBlockStatePos = localBlockPos;
            }
        }

        return this.inBlockState;
    }

    /**
     * @return the sub-level the entity is standing on or locked to
     */
    @Override
    public SubLevel sable$getTrackingSubLevel() {
        return this.sable$trackingSubLevel;
    }

    @Override
    public UUID sable$getLastTrackingSubLevelID() {
        return this.sable$lastTrackingSubLevelId;
    }

    @Override
    public void sable$setTrackingSubLevel(final SubLevel subLevel) {
        this.sable$trackingSubLevel = subLevel;
        if (subLevel != null) {
            this.sable$setLastTrackingSubLevelID(subLevel.getUniqueId());
        }
    }

    @Override
    public void sable$setLastTrackingSubLevelID(final UUID uuid) {
        this.sable$lastTrackingSubLevelId = uuid;
    }

    @Override
    public SubLevelEntityCollision.CollisionInfo sable$getCollisionInfo() {
        return this.sable$collisionInfo;
    }

    @Override
    public void sable$setPosField(final Vec3 newPosition) {
        this.position = newPosition;
    }
}
