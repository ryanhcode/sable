package dev.ryanhcode.sable.mixin.entity.entity_sublevel_collision;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.mixinterface.EntityExtension;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.LivingEntityMovementExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity implements LivingEntityMovementExtension {
    @Shadow public abstract LivingEntity.Fallsounds getFallSounds();

    /**
     * [m/t]
     */
    @Unique
    private final Vector3d sable$inheritedVelocity = new Vector3d();

    @Unique
    private final Vector3d sable$tempPlayerVelocity = new Vector3d();

    @Unique
    private final Vector3d sable$tempSubLevelVelocity = new Vector3d();

    public LivingEntityMixin(final EntityType<?> entityType, final Level level) {
        super(entityType, level);
    }

    /**
     * Before animation
     */
    @Inject(method = "travel", at = @At(value = "RETURN"))
    public void sable$beforeAnimation(final Vec3 vec3, final CallbackInfo ci) {
        final SubLevelEntityCollision.CollisionInfo info = ((EntityMovementExtension) this).sable$getCollisionInfo();

        if (info != null && info.inheritedMotion != null) {
            this.setPos(this.position().add(((EntityExtension) this).sable$vanillaCollide(info.inheritedMotion)));
            this.sable$inheritedVelocity.set(info.inheritedMotion.x, info.inheritedMotion.y, info.inheritedMotion.z);
        }

        if (info != null && info.firstCollisions != null && !info.firstCollisions.isEmpty()) {
            for (final var firstCollision : info.firstCollisions.entrySet()) {
                final SubLevelEntityCollision.FirstCollisionInfo collisionInfo = firstCollision.getValue();
                final SubLevel subLevel = firstCollision.getKey();

                if (!collisionInfo.horizontal() || subLevel == info.preTrackingSubLevel) {
                    continue;
                }

                this.sable$computeCollisionEffects(info, subLevel, collisionInfo);
            }
        }

        final double threshold = 0.0000001;
        if (this.sable$inheritedVelocity.lengthSquared() <= threshold) {
            this.sable$inheritedVelocity.zero();
        }

        if ((info == null || info.inheritedMotion == null) && this.sable$inheritedVelocity.lengthSquared() > threshold) {
            this.sable$applyDrag();
        }
    }

    @Unique
    private void sable$applyDrag() {
        if (this.verticalCollision || this.onGround()) {
            final double drag = 0.7;
            this.sable$inheritedVelocity.mul(drag, 0.0, drag);
        }

        if (this.horizontalCollision) {
            final double drag = 0.8;
            this.sable$inheritedVelocity.mul(drag, 0.6, drag);
        }

        if ((Object) this instanceof final Player player && player.getAbilities().flying) {
            this.sable$inheritedVelocity.mul(0.9);
        }

        if (this.wasTouchingWater) {
            this.sable$inheritedVelocity.mul(0.9);
        }

        this.sable$inheritedVelocity.mul(0.99);

        if (Math.abs(this.sable$inheritedVelocity.y) < 0.01) {
            this.sable$inheritedVelocity.y = 0.0;
        }
    }

    /**
     * Computes collision damage & bounce
     */
    @Unique
    private void sable$computeCollisionEffects(final SubLevelEntityCollision.CollisionInfo info, final SubLevel collidedSubLevel, final SubLevelEntityCollision.FirstCollisionInfo collisionInfo) {
        final Vector3d playerVelocity = JOMLConversion.toJOML(info.preDeltaMovement, this.sable$tempPlayerVelocity);
        playerVelocity.add(this.sable$inheritedVelocity);

        final Level level = this.level();
        final Vector3d pointVelocity = Sable.HELPER.getVelocity(level,
                        collidedSubLevel,
                        collisionInfo.localLocation(),
                        this.sable$tempSubLevelVelocity)
                .mul(1.0 / 20.0);

        final Vector3d relativeVelocity = playerVelocity.sub(pointVelocity).negate();
        final double magnitude = collisionInfo.globalDirection().dot(relativeVelocity);

        if (magnitude > 3.0 / 20.0) {
            relativeVelocity.set(collisionInfo.globalDirection()).mul(-magnitude);

            if (collisionInfo.bouncy()) {
                final SoundEvent sound = collisionInfo.block().getSoundType().getFallSound();
                level.playSound((Entity) this instanceof final Player player ? player : null, this.getX(), this.getY(), this.getZ(), sound, SoundSource.BLOCKS, .75f, 1);

                this.addDeltaMovement(JOMLConversion.toMojang(collisionInfo.globalDirection()).scale(relativeVelocity.length() * 0.65));

                if (Sable.HELPER.getTrackingSubLevel(this) == null) {
                    this.addDeltaMovement(JOMLConversion.toMojang(pointVelocity));
                }
            } else {
                final float damageAmount = (float) (magnitude * 12.0 - 8.0);

                if (damageAmount > 0.0) {
                    this.playSound(damageAmount > 4 ? this.getFallSounds().big() : this.getFallSounds().small(), 1.0F, 1.0F);
                    this.hurt(this.damageSources().flyIntoWall(), damageAmount);
                }
            }
        }
    }

    @Override
    public Vector3d sable$getInheritedVelocity() {
        return this.sable$inheritedVelocity;
    }
}
