package dev.ryanhcode.sable.mixin.entity.entity_rotations_and_riding;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.authlib.GameProfile;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin extends Player {

    public LocalPlayerMixin(final Level level, final BlockPos blockPos, final float f, final GameProfile gameProfile) {
        super(level, blockPos, f, gameProfile);
    }

    @WrapOperation(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;add(DDD)Lnet/minecraft/world/phys/Vec3;", ordinal =  0))
    private Vec3 sable$modifyFlightDir(final Vec3 instance, final double x, final double y, final double z, Operation<Vec3> original) {
        final Quaterniondc orientation = EntitySubLevelUtil.getCustomEntityOrientation(this, 1.0f);
        if (orientation == null) {
            return original.call(instance, x, y, z);
        }

        final Vector3d dir = orientation.transform(new Vector3d(x, y, z));
        return original.call(instance, dir.x, dir.y, dir.z);
    }

    @Unique
    public final Vec3 sable$calculateViewVector2(final float f, final float g) {
        final float h = f * (float) (Math.PI / 180.0);
        final float i = -g * (float) (Math.PI / 180.0);
        final float j = Mth.cos(i);
        final float k = Mth.sin(i);
        final float l = Mth.cos(h);
        final float m = Mth.sin(h);
        return new Vec3(k * l, -m, j * l);
    }

    @Inject(method = "startRiding(Lnet/minecraft/world/entity/Entity;Z)Z", at = @At("RETURN"))
    private void sable$onStartRiding(final Entity entity, final boolean bl, final CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() || !EntitySubLevelUtil.shouldKick(this)) {
            return;
        }

        final Entity vehicle = this.getVehicle();
        if (vehicle == null) {
            return;
        }

        final SubLevel subLevel = Sable.HELPER.getContaining(vehicle);
        if (subLevel != null && EntitySubLevelUtil.shouldKick(this)) {
            final Vec3 lookDir = this.sable$calculateViewVector2(this.getXRot(), this.getYRot());
            final Vec3 localLookDir = subLevel.logicalPose().transformNormalInverse(lookDir);

            vehicle.positionRider(this);
            EntitySubLevelUtil.setOldPosNoMovement(this);
            this.lookAt(EntityAnchorArgument.Anchor.FEET, this.position().add(localLookDir));
        }
    }

    @Inject(method = "removeVehicle", at = @At("HEAD"))
    private void sable$onStopRiding(final CallbackInfo ci) {
        if (!EntitySubLevelUtil.shouldKick(this)) {
            return;
        }

        final Entity vehicle = this.getVehicle();
        if (vehicle == null) {
            return;
        }

        final SubLevel subLevel = Sable.HELPER.getContaining(vehicle);
        if (subLevel != null) {
            final Vec3 lookDir = this.sable$calculateViewVector2(this.getXRot(), this.getYRot());
            final Vec3 globalLookDir = subLevel.logicalPose().transformNormal(lookDir);

            this.lookAt(EntityAnchorArgument.Anchor.FEET, this.position().add(globalLookDir));
        }
    }

    @Unique
    private void sable$dismountVehicle(final Entity entity) {
        final ActiveSableCompanion helper = Sable.HELPER;
        final Level level = this.level();
        final Vector3d dismountPos;

        if (this.isRemoved()) {
            dismountPos = JOMLConversion.toJOML(this.position());
        } else if (!entity.isRemoved() && !level.getBlockState(entity.blockPosition()).is(BlockTags.PORTALS)) {
            dismountPos = JOMLConversion.toJOML(entity.getDismountLocationForPassenger(this));
        } else {
            final double d = Math.max(this.getY(), helper.projectOutOfSubLevel(level, entity.position()).y);
            dismountPos = new Vector3d(this.getX(), d, this.getZ());
        }

        helper.projectOutOfSubLevel(level, dismountPos);
        this.setPos(dismountPos.x, dismountPos.y, dismountPos.z);
    }

    @Override
    public void stopRiding() {
        final Entity vehicle = this.getVehicle();
        super.stopRiding();

        if (this.level().isClientSide && vehicle != null && vehicle != this.getVehicle() && Sable.HELPER.getContaining(vehicle) != null) {
            this.sable$dismountVehicle(vehicle);
        }
    }
}
