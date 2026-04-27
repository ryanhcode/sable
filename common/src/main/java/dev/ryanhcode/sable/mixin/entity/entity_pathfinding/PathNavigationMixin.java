package dev.ryanhcode.sable.mixin.entity.entity_pathfinding;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.mixinterface.entity.pathfinding.PathExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Iterator;
import java.util.Set;

@Mixin(PathNavigation.class)
public abstract class PathNavigationMixin {

    @Shadow
    @Final
    protected Mob mob;
    @Shadow
    @Nullable
    protected Path path;
    @Shadow
    @Final
    protected Level level;
    @Shadow
    @Nullable
    private BlockPos targetPos;
    @Shadow
    private int reachRange;
    @Shadow
    @Final
    private PathFinder pathFinder;
    @Shadow
    private float maxVisitedNodesMultiplier;

    @Shadow
    protected abstract boolean canUpdatePath();

    @Shadow
    protected abstract void resetStuckTimeout();

    @Inject(method = "createPath(Ljava/util/Set;IZIF)Lnet/minecraft/world/level/pathfinder/Path;", at = @At("HEAD"), cancellable = true)
    private void sable$createPath(final Set<BlockPos> globalSet, final int i, final boolean bl, final int j, final float f, final CallbackInfoReturnable<Path> cir) {
        SubLevel trackingSubLevel = Sable.HELPER.getTrackingSubLevel(this.mob);

        final Iterator<BlockPos> iter = globalSet.iterator();
        while (trackingSubLevel == null && iter.hasNext()) {
            final BlockPos globalPos = iter.next();
            trackingSubLevel = Sable.HELPER.getContaining(this.level, globalPos);
        }

        if (trackingSubLevel != null) {
            if (globalSet.isEmpty()) {
                cir.setReturnValue(null);
            } else if (!this.canUpdatePath()) {
                cir.setReturnValue(null);
            } else if (this.path != null && !this.path.isDone() && globalSet.contains(this.targetPos)) {
                cir.setReturnValue(this.path);
            } else {
                final Pose3d pose = trackingSubLevel.logicalPose();
                final Vec3 localMobPosition = pose.transformPositionInverse(this.mob.position());
                final BlockPos localMobBlockPosition = BlockPos.containing(localMobPosition);

                this.level.getProfiler().push("pathfind_sub_level");

                // turn global set to local
                final Set<BlockPos> localSet = new ObjectOpenHashSet<>();

                for (final BlockPos globalPos : globalSet) {
                    if (Sable.HELPER.getContaining(this.level, globalPos) == trackingSubLevel) {
                        localSet.add(globalPos);
                        continue;
                    }
                    final Vec3 globalPosVec = globalPos.getCenter();
                    final Vec3 localPosVec = pose.transformPositionInverse(globalPosVec);
                    localSet.add(BlockPos.containing(localPosVec));
                }

                final BlockPos blockPos = bl ? localMobBlockPosition.above() : localMobBlockPosition;
                final int k = (int) (f + (float) i);
                final PathNavigationRegion pathNavigationRegion = new PathNavigationRegion(this.level, blockPos.offset(-k, -k, -k), blockPos.offset(k, k, k));
                final Path path = this.pathFinder.findPath(pathNavigationRegion, this.mob, localSet, f, j, this.maxVisitedNodesMultiplier);
                this.level.getProfiler().pop();
                if (path != null && path.getTarget() != null) {
                    this.targetPos = path.getTarget();
                    this.reachRange = j;
                    this.resetStuckTimeout();
                    ((PathExtension) path).sable$setLocalPath(this.level, true);
                }


                cir.setReturnValue(path);
            }
        }
    }

}
