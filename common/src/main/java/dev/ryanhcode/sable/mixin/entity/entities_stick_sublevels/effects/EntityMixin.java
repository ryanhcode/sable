package dev.ryanhcode.sable.mixin.entity.entities_stick_sublevels.effects;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(Entity.class)
public abstract class EntityMixin {

    @Shadow
    @Final
    protected RandomSource random;
    @Shadow
    private Level level;
    @Shadow
    private BlockPos blockPosition;

    @Shadow
    public abstract Vec3 position();

    @Shadow
    @Deprecated
    public abstract BlockPos getOnPosLegacy();

    @Shadow
    public abstract Level level();

    @Shadow
    public abstract Vec3 getDeltaMovement();

    @Shadow
    public abstract double getX();

    @Shadow
    public abstract double getZ();

    @Shadow
    public abstract BlockPos blockPosition();

    @Shadow public Optional<BlockPos> mainSupportingBlockPos;

    @Shadow public abstract Vec3 getEyePosition();

    @Inject(method = "spawnSprintParticle", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;", shift = At.Shift.AFTER))
    private void sable$spawnSprintParticle(final CallbackInfo ci, @Local(ordinal = 0) final BlockPos blockPos, @Share("localPosition") final LocalRef<Vec3> localPosition) {
        final SubLevel subLevel = Sable.HELPER.getContaining(this.level, blockPos);

        final Vec3 feetPos = JOMLConversion.toMojang(Sable.HELPER.getFeetPos((Entity) (Object) this, 0.0f));
        localPosition.set(feetPos);

        if (subLevel != null) {
            localPosition.set(subLevel.logicalPose().transformPositionInverse(feetPos));
        }
    }

    @Redirect(method = "spawnSprintParticle", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getX()D"))
    private double sable$getX(final Entity entity, @Share("localPosition") final LocalRef<Vec3> localPosition) {
        return localPosition.get().x;
    }

    @Redirect(method = "spawnSprintParticle", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getZ()D"))
    private double sable$getZ(final Entity entity, @Share("localPosition") final LocalRef<Vec3> localPosition) {
        return localPosition.get().z;
    }

    @Redirect(method = "spawnSprintParticle", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getY()D"))
    private double sable$getY(final Entity entity, @Share("localPosition") final LocalRef<Vec3> localPosition) {
        return localPosition.get().y;
    }


    @Redirect(method = "spawnSprintParticle", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
    private void sable$addParticle(final Level instance, final ParticleOptions particleOptions, final double d, final double e, final double f, final double g, final double h, final double i, @Share("localPosition") final LocalRef<Vec3> localPosition, @Local(ordinal = 0) final BlockPos pos) {
        final SubLevel subLevel = Sable.HELPER.getContaining(this.level, pos);

        if (subLevel == null) {
            instance.addParticle(particleOptions, d, e, f, g, h, i);
            return;
        }

        Vec3 upDir = new Vec3(0, 1, 0);

        final Quaterniondc orientation = EntitySubLevelUtil.getCustomEntityOrientation((Entity) (Object) this, 1.0f);
        if (orientation != null) {
            final Vector3d upDirJOML = orientation.transform(OrientedBoundingBox3d.UP, new Vector3d());
            upDir = JOMLConversion.toMojang(upDirJOML);
        }

        // magic number offset in original is 0.1
        final Vec3 p = new Vec3(d, e - 0.1, f).add(subLevel.logicalPose().transformNormalInverse(upDir.scale(0.1)));
        Vec3 v = subLevel.logicalPose().transformNormalInverse(new Vec3(g, h, i));

        if (orientation != null) {
            v = this.getDeltaMovement().scale(-4.0);

            final double dot = v.dot(upDir);
            v = v.subtract(upDir.x * dot, upDir.y * dot, upDir.z * dot).add(upDir.x * 1.5, upDir.y * 1.5, upDir.z * 1.5);
        }

        instance.addParticle(particleOptions, p.x, p.y, p.z, v.x, v.y, v.z);
    }

    /**
     * Makes getOnPos fall back on a simple, single block check when standing on a sub-level
     */
    @Inject(method = "getOnPos(F)Lnet/minecraft/core/BlockPos;", at = @At("HEAD"), cancellable = true)
    private void sable$preGetOnPos(final float distance, final CallbackInfoReturnable<BlockPos> cir) {
        final SubLevel trackingSubLevel = Sable.HELPER.getTrackingSubLevel((Entity) (Object) this);
        final Vec3 feetPos = JOMLConversion.toMojang(Sable.HELPER.getFeetPos((Entity) (Object) this, Math.max(0.1f, distance)));

        if (trackingSubLevel != null) {
            final Vec3 localPos = trackingSubLevel.logicalPose().transformPositionInverse(feetPos);
            cir.setReturnValue(BlockPos.containing(localPos));
            return;
        }

        if (this.mainSupportingBlockPos.isEmpty()) {
            final BoundingBox3d bounds = new BoundingBox3d(this.blockPosition);
            bounds.expand(distance, bounds);

            final Iterable<SubLevel> intersectingSubLevels = Sable.HELPER.getAllIntersecting(this.level, bounds);

            for (final SubLevel subLevel : intersectingSubLevels) {
                final Vec3 localPos = subLevel.logicalPose().transformPositionInverse(feetPos);
                final BlockPos localBlockPos = BlockPos.containing(localPos);

                // TODO: This should check if the collision shape is not empty, not if it's air
                if (!this.level.getBlockState(localBlockPos).isAir()) {
                    cir.setReturnValue(localBlockPos);
                    return;
                }
            }
        }
    }

}
