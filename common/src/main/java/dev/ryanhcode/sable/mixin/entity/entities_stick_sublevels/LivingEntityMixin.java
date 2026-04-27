package dev.ryanhcode.sable.mixin.entity.entities_stick_sublevels;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinterface.entity.entities_stick_sublevels.LivingEntityStickExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity implements LivingEntityStickExtension {


    @Shadow
    protected int lerpSteps;
    @Shadow
    protected double lerpYRot;
    @Shadow
    protected double lerpXRot;

    @Shadow protected abstract void updateWalkAnimation(float f);

    @Unique
    private Vec3 sable$lerpTarget = Vec3.ZERO;

    @Unique
    private int sable$sableLerpSteps;

    @Unique
    private int sable$sableRotLerpSteps;

    public LivingEntityMixin(final EntityType<?> entityType,
                             final Level level) {
        super(entityType, level);
    }

    @Override
    public void sable$setupLerp() {
        // Prevent vanilla lerp from happening
        if (this.sable$getPlotPosition() != null && this.lerpSteps > 0) {
            this.sable$sableRotLerpSteps = this.lerpSteps;
            this.lerpSteps = 0;
        }
    }

    @Override
    public void sable$applyLerp() {
        final Vec3 plotPos = this.sable$getPlotPosition();
        if (plotPos == null) {
            this.sable$sableLerpSteps = 0;
            this.sable$sableRotLerpSteps = 0;
            return;
        }

        if (this.sable$sableLerpSteps > 0) {
            this.sable$setPlotPosition(plotPos.lerp(this.sable$lerpTarget, 1.0 / this.sable$sableLerpSteps));
            --this.sable$sableLerpSteps;
        }
        if (this.sable$sableRotLerpSteps > 0) {
            final double difference = Mth.wrapDegrees(this.lerpYRot - (double) this.getYRot());
            this.setYRot(this.getYRot() + (float) difference / (float) this.sable$sableRotLerpSteps);
            this.setXRot(this.getXRot() + (float) (this.lerpXRot - (double) this.getXRot()) / (float) this.sable$sableRotLerpSteps);
            --this.sable$sableRotLerpSteps;
            this.setRot(this.getYRot(), this.getXRot());
        }
    }


    @Override
    public Vec3 sable$getLerpTarget() {
        return this.sable$lerpTarget;
    }

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void sable$updateRotLerp(final CallbackInfo ci) {
        this.sable$setupLerp();
    }

    @Inject(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;setDeltaMovement(DDD)V", shift = At.Shift.BEFORE))
    private void sable$updatePlotPosition(final CallbackInfo ci) {
        this.sable$applyLerp();
    }


    @Override
    public void sable$plotLerpTo(final Vec3 pos, final int lerpSteps) {
        this.sable$lerpTarget = pos;
        this.sable$sableLerpSteps = lerpSteps;
    }

    @ModifyVariable(method = "tick", at = @At("STORE"), ordinal = 0)
    private double sable$modifyXDifference(final double x) {
        return this.sable$getDifference(true).x;
    }


    @ModifyVariable(method = "tick", at = @At("STORE"), ordinal = 1)
    private double sable$modifyZDifference(final double x) {
        return this.sable$getDifference(true).z;
    }

    @Redirect(method = "calculateEntityAnimation", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;updateWalkAnimation(F)V"))
    private void sable$walkAnimation(final LivingEntity instance, final float g, final boolean pIncludeHeight) {
        final Vec3 delta = this.sable$getDifference(false);
        final float f = (float) Mth.length(delta.x, pIncludeHeight ? delta.y : 0.0D, delta.z);

        this.updateWalkAnimation(f);

    }

    @Unique
    private Vec3 sable$getDifference(final boolean countLocalPlayer) {
        Vec3 currentPos = this.position();
        Vec3 oldPos = new Vec3(this.xo, this.yo, this.zo);

        Vec3 delta = currentPos.subtract(oldPos);

        final SubLevel trackingSubLevel = Sable.HELPER.getTrackingSubLevel(this);

        if (trackingSubLevel != null && (countLocalPlayer || !(((Object)this) instanceof final Player player && player.isLocalPlayer()))) {
            final Pose3d pose = trackingSubLevel.logicalPose();
            final Pose3dc lastPose = trackingSubLevel.lastPose();
            currentPos = pose.transformPositionInverse(currentPos);
            oldPos = lastPose.transformPositionInverse(oldPos);
            delta = currentPos.subtract(oldPos);
            delta = pose.transformNormal(delta);
        }

        final Quaterniondc orientation = EntitySubLevelUtil.getCustomEntityOrientation(this, 1.0f);
        if (orientation != null) {
            delta = JOMLConversion.toMojang(orientation.transformInverse(JOMLConversion.toJOML(delta)));
        }


        return delta;
    }

    @Inject(method = "recreateFromPacket", at = @At("TAIL"))
    public void sable$recreateFromPacket(final ClientboundAddEntityPacket packet, final CallbackInfo ci) {
        if(!EntitySubLevelUtil.shouldKick(this)) return;

        final double packetX = packet.getX();
        final double packetY = packet.getY();
        final double packetZ = packet.getZ();

        final SubLevel packetSubLevel = Sable.HELPER.getContaining(this.level(), packetX, packetZ);
        if (packetSubLevel != null) {
            final Vector3d globalPacketPos = packetSubLevel.logicalPose().transformPosition(new Vector3d(packetX, packetY, packetZ));
            this.setPos(globalPacketPos.x, globalPacketPos.y, globalPacketPos.z);
        }
    }
}
