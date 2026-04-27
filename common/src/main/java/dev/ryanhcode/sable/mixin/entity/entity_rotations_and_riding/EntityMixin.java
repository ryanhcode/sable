package dev.ryanhcode.sable.mixin.entity.entity_rotations_and_riding;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.mixinhelpers.entity.entity_riding_sub_level_vehicle.EntityRidingSubLevelVehicleHelper;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.tracking_points.SubLevelTrackingPointSavedData;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniondc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(Entity.class)
public abstract class EntityMixin {

    @Shadow
    private Level level;

    @Shadow
    @Nullable
    private Entity vehicle;

    @Shadow
    private Vec3 position;

    @Shadow
    public abstract void setPos(Vec3 vec3);

    @Shadow
    public abstract boolean hasPassenger(Entity entity);

    @Shadow
    public abstract Vec3 position();

    @Shadow
    protected abstract ListTag newDoubleList(double... ds);

    @Shadow
    public abstract double getX();

    @Shadow
    public abstract double getY();

    @Shadow
    public abstract double getZ();

    @Shadow public abstract Level level();

    @Shadow @Nullable public abstract Entity getVehicle();

    @Shadow public abstract Vec3 getLookAngle();

    @Shadow public abstract void lookAt(EntityAnchorArgument.Anchor arg, Vec3 arg2);

    @Shadow public abstract float getXRot();

    @Shadow public abstract float getYRot();

    @Shadow
    protected static Vec3 getInputVector(final Vec3 vec3, final float f, final float g) {
        return null;
    }

    @Shadow public abstract void setDeltaMovement(Vec3 vec3);

    @Shadow public abstract Vec3 getDeltaMovement();

    @WrapOperation(method = "move", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;horizontalDistance()D"))
    private double sable$fixWalkDistance(final Vec3 vec, final Operation<Double> original) {
        final Quaterniondc orientation = EntitySubLevelUtil.getCustomEntityOrientation((Entity) (Object) this, 1.0f);
        if (orientation == null) return original.call(vec);

        return original.call(JOMLConversion.toMojang(orientation.transformInverse(JOMLConversion.toJOML(vec))));
    }

    @Inject(method = "moveRelative", at = @At("HEAD"), cancellable = true)
    public void moveRelative(final float f, final Vec3 vec3, final CallbackInfo ci) {
        final Quaterniondc orientation = EntitySubLevelUtil.getCustomEntityOrientation((Entity) (Object) this, 1.0f);
        if (orientation == null) return;

        final Vec3 inputVector = getInputVector(vec3, f, this.getYRot());
        final Vec3 impulse = JOMLConversion.toMojang(orientation.transform(JOMLConversion.toJOML(inputVector)));
        this.setDeltaMovement(this.getDeltaMovement().add(impulse));
        ci.cancel();
    }

    @Inject(method = "rideTick", at = @At("TAIL"))
    public void sable$onRidingTick(final CallbackInfo ci) {
        if (this.vehicle == null) return;

        final ActiveSableCompanion helper = Sable.HELPER;
        final SubLevel vehicleSubLevel = helper.getContaining(this.vehicle);
        if (vehicleSubLevel == null) return;
        if (helper.getContaining(this.level, this.position) != vehicleSubLevel) return;

        final Vec3 pos = EntityRidingSubLevelVehicleHelper.kickRidingEntity((Entity) (Object) this, vehicleSubLevel);
        this.setPos(pos);
    }

    @Inject(method = "positionRider(Lnet/minecraft/world/entity/Entity;)V", at = @At("TAIL"))
    public void sable$onPositionRider(final Entity entity, final CallbackInfo ci) {
        if (!this.hasPassenger(entity)) return;

        final SubLevel subLevel = Sable.HELPER.getContaining(this.level, entity.position());
        if (subLevel == null) return;

        final Vec3 pos = EntityRidingSubLevelVehicleHelper.kickRidingEntity(entity, subLevel);
        entity.setPos(pos);
    }

    @WrapOperation(method = "saveWithoutId", at = @At(value = "INVOKE", target = "Lnet/minecraft/nbt/CompoundTag;put(Ljava/lang/String;Lnet/minecraft/nbt/Tag;)Lnet/minecraft/nbt/Tag;", ordinal = 0))
    public Tag sable$fixPassengerSaving(final CompoundTag instance, final String string, final Tag tag, Operation<Tag> original) {
        if (!EntitySubLevelUtil.shouldKick(Entity.class.cast(this))) {
            return original.call(instance, string, tag);
        }

        final SubLevel subLevel = Sable.HELPER.getContaining(this.vehicle);
        if (subLevel != null) {
            final Tag newPositionTag = this.newDoubleList(
                    this.getX(),
                    this.getY(),
                    this.getZ()
            );

            if ((Object)this instanceof final ServerPlayer serverPlayer) {
                final SubLevelTrackingPointSavedData data = SubLevelTrackingPointSavedData.getOrLoad((ServerLevel) this.level());
                final UUID loginPointUUID = data.generateTrackingPoint(serverPlayer, (ServerSubLevel) subLevel);
                if (loginPointUUID != null) {
                    instance.putUUID("LoginPoint", loginPointUUID);
                }
            }

            return original.call(instance, string, newPositionTag);
        }

        return original.call(instance, string, tag);
    }
}
