package dev.ryanhcode.sable.api.entity;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.index.SableTags;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

/**
 * Utility for operations regarding entities and sub-levels
 */
public class EntitySubLevelUtil {

    /**
     * Sets the old pos of an entity for no apparent movement, taking their tracking sub-level
     * into account.
     *
     * @param entity the entity to set the old pos of
     */
    public static void setOldPosNoMovement(final Entity entity) {
        final SubLevel trackingSubLevel = Sable.HELPER.getTrackingSubLevel(entity);

        if (trackingSubLevel != null) {
            final Vec3 entityPos = entity.position();
            final Vec3 oldPos = trackingSubLevel.lastPose().transformPosition(trackingSubLevel.logicalPose().transformPositionInverse(entityPos));

            entity.xOld = oldPos.x;
            entity.xo = oldPos.x;
            entity.yOld = oldPos.y;
            entity.yo = oldPos.y;
            entity.zOld = oldPos.z;
            entity.zo = oldPos.z;
        } else {
            entity.xOld = entity.getX();
            entity.xo = entity.getX();
            entity.yOld = entity.getY();
            entity.yo = entity.getY();
            entity.zOld = entity.getZ();
            entity.zo = entity.getZ();
        }
    }

    /**
     * Kicks an entity out of a sub-level, including velocity and position.
     *
     * @param subLevel The sub-level to kick the entity out of
     * @param entity   The entity to kick
     */
    public static void kickEntity(final SubLevel subLevel, final Entity entity) {
        final Vector3d subLevelGainedVelo = new Vector3d();
        if (entity instanceof final AbstractHurtingProjectile ahp && ahp.accelerationPower == 0) {
            Sable.HELPER.getVelocity(entity.level(), JOMLConversion.toJOML(entity.position()), subLevelGainedVelo);
        }

        // convert from m/s to m/t
        subLevelGainedVelo.mul(1.0 / 20.0);

        final Vec3 pos = entity.position();
        Vec3 anchor = Vec3.ZERO;

        if (entity instanceof FallingBlockEntity) {
            anchor = new Vec3(0.0, entity.getBbHeight() / 2.0, 0.0);
        }

        entity.moveTo(subLevel.logicalPose().transformPosition(pos.add(anchor)).subtract(anchor));
        entity.setDeltaMovement(subLevel.logicalPose().transformNormal(entity.getDeltaMovement()).add(subLevelGainedVelo.x, subLevelGainedVelo.y, subLevelGainedVelo.z));
        entity.lookAt(EntityAnchorArgument.Anchor.FEET, subLevel.logicalPose().transformNormal(entity.getLookAngle()).add(entity.position()));

        // Arrows use an incorrect Y and X rotation
        if (entity instanceof AbstractArrow) {
            final Vec3 deltaMovement = entity.getDeltaMovement();
            final double horizontal = deltaMovement.horizontalDistance();
            entity.setYRot((float) (Mth.atan2(deltaMovement.x, deltaMovement.z) * 180.0 / (float) Math.PI));
            entity.setXRot((float) (Mth.atan2(deltaMovement.y, horizontal) * 180.0 / (float) Math.PI));
        }
    }

    public static boolean shouldKick(final Entity entity) {
        return !entity.getType().is(SableTags.RETAIN_IN_SUB_LEVEL);
    }

    @Nullable
    public static Quaterniondc getCustomEntityOrientation(final Entity entity, final float partialTicks) {
        return null;
    }

    public static boolean hasCustomEntityOrientation(final Entity entity) {
        return false;
    }
}
