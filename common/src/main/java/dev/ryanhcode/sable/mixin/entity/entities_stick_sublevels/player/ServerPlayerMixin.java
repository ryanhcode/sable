package dev.ryanhcode.sable.mixin.entity.entities_stick_sublevels.player;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin extends Entity {

    @Unique
    private final Vector3d sable$trackedSubLevelPos = new Vector3d();

    public ServerPlayerMixin(final EntityType<?> entityType, final Level level) {
        super(entityType, level);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    public void tick(final CallbackInfo ci) {
        final SubLevel trackingSubLevel = Sable.HELPER.getTrackingSubLevel(this);

        if (trackingSubLevel != null && !trackingSubLevel.isRemoved()) {
            final Vector3d entityCenter = JOMLConversion.getAABBCenter(this.getBoundingBox(), this.sable$trackedSubLevelPos);
            final double entityCenterX = entityCenter.x();
            final double entityCenterY = entityCenter.y();
            final double entityCenterZ = entityCenter.z();

            final Pose3dc pose = trackingSubLevel.logicalPose();
            final Pose3dc lastPose = trackingSubLevel.lastPose();

            final Vector3d inherited = pose.transformPosition(lastPose.transformPositionInverse(entityCenter, entityCenter));

            final Vec3 position = this.position();
            this.setPos(new Vec3(
                    position.x + inherited.x - entityCenterX,
                    position.y + inherited.y - entityCenterY,
                    position.z + inherited.z - entityCenterZ));
        }
    }
}
