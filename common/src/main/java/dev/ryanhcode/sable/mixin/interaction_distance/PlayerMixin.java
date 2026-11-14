package dev.ryanhcode.sable.mixin.interaction_distance;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fixes interaction distance on entity and block interactions
 */
@Mixin(Player.class)
public abstract class PlayerMixin extends LivingEntity {

    protected PlayerMixin(final EntityType<? extends LivingEntity> entityType, final Level level) {
        super(entityType, level);
    }

    @Shadow
    public abstract double blockInteractionRange();

    @Inject(method = "canInteractWithBlock", at = @At("HEAD"), cancellable = true)
    private void sable$canInteractWithBlock(final BlockPos pos, final double slop, final CallbackInfoReturnable<Boolean> cir) {
        final SubLevel subLevel = Sable.HELPER.getContaining(this.level(), pos);

        if (subLevel != null) {
            final double rangeWithSlop = this.blockInteractionRange() + slop;
            final Pose3d pose = subLevel.logicalPose();
            Vec3 eyePos = pose.transformPositionInverse(this.getEyePosition());

            if (!pose.scale().equals(JOMLConversion.ONE)) {
                eyePos = eyePos.subtract(pos.getCenter()).multiply(pose.scale().x, pose.scale().y, pose.scale().z).add(pos.getCenter());
            }

            final boolean closeEnough = (new AABB(pos)).distanceToSqr(eyePos) < rangeWithSlop * rangeWithSlop;

            if (closeEnough) cir.setReturnValue(true);
        }
    }

    @Inject(method = "canInteractWithEntity(Lnet/minecraft/world/phys/AABB;D)Z", at = @At("HEAD"), cancellable = true)
    private void sable$canInteractWithEntity(final AABB aabb, final double slop, final CallbackInfoReturnable<Boolean> cir) {
        // should bottom center be assumed here?
        final SubLevel subLevel = Sable.HELPER.getContaining(this.level(), aabb.getBottomCenter());

        if (subLevel != null) {
            final double rangeWithSlop = this.blockInteractionRange() + slop;
            final Pose3d pose = subLevel.logicalPose();
            Vec3 eyePos = subLevel.logicalPose().transformPositionInverse(this.getEyePosition());

            if (!pose.scale().equals(JOMLConversion.ONE)) {
                eyePos = eyePos.subtract(aabb.getCenter()).multiply(pose.scale().x, pose.scale().y, pose.scale().z).add(aabb.getCenter());
            }

            final boolean closeEnough = aabb.distanceToSqr(eyePos) < rangeWithSlop * rangeWithSlop;

            if (closeEnough) cir.setReturnValue(true);
        }
    }

}
