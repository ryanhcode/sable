package dev.ryanhcode.sable.mixin.entity.entity_pathfinding;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PathfindingContext.class)
public class PathfindingContextMixin {

    @Shadow @Final @Mutable
    private BlockPos mobPosition;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void sable$init(final CollisionGetter collisionGetter, final Mob mob, final CallbackInfo ci) {
        final SubLevel trackingSubLevel = Sable.HELPER.getTrackingSubLevel(mob);

        if (trackingSubLevel != null) {
            this.mobPosition = BlockPos.containing(trackingSubLevel.logicalPose().transformPositionInverse(mob.position()));
        }
    }

}
