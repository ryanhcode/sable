package dev.ryanhcode.sable.neoforge.mixinhelper.compatibility.create.raycasts;

import com.simibubi.create.foundation.utility.RaycastHelper;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.LevelPoseProviderExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiPredicate;
import java.util.function.Predicate;

public class SableRaycastHelper {

    public static @Nullable RaycastHelper.PredicateTraceResult rayCastUntilWithSublevels(final Level level, final Vec3 start, final Vec3 end, final BiPredicate<@Nullable SubLevel, BlockPos> predicate) {
        return rayCastUntilWithSublevels(level, start, end, (pos) -> predicate.test(null, pos), predicate);
    }

    public static @Nullable RaycastHelper.PredicateTraceResult rayCastUntilWithSublevels(final Level level, final Vec3 start, final Vec3 end, final Predicate<BlockPos> predicate) {
        return rayCastUntilWithSublevels(level, start, end, predicate, (sublevel, pos) -> predicate.test(pos));
    }

    public static @Nullable RaycastHelper.PredicateTraceResult rayCastUntilWithSublevels(final Level level, final Vec3 start, final Vec3 end, final Predicate<BlockPos> predicate, final BiPredicate<SubLevel, BlockPos> subLevelPredicate) {
        RaycastHelper.PredicateTraceResult closestRay = RaycastHelper.rayTraceUntil(start, end, predicate);
        double closestDistance = closestRay != null && !closestRay.missed() ? Vec3.atCenterOf(closestRay.getPos()).distanceToSqr(start) : Double.MAX_VALUE;

        final Iterable<SubLevel> sublevels = Sable.HELPER.getAllIntersecting(level, new BoundingBox3d(start, end));

        for (final SubLevel subLevel : sublevels) {
            final Vec3 plotStart;
            final Vec3 plotEnd;

            if (level instanceof final LevelPoseProviderExtension poseProvider) {
                plotStart = poseProvider.sable$getPose(subLevel).transformPositionInverse(start);
                plotEnd = poseProvider.sable$getPose(subLevel).transformPositionInverse(end);
            } else {
                plotStart = subLevel.logicalPose().transformPositionInverse(start);
                plotEnd = subLevel.logicalPose().transformPositionInverse(end);
            }

            final RaycastHelper.PredicateTraceResult plotRay = RaycastHelper.rayTraceUntil(plotStart, plotEnd, (pos) -> subLevelPredicate.test(subLevel, pos));

            final double plotDistance = plotRay.getPos() != null ? Vec3.atCenterOf(plotRay.getPos()).distanceToSqr(plotStart) : Double.MAX_VALUE;

            if (plotDistance < closestDistance) {
                closestRay = plotRay;
                closestDistance = plotDistance;
            }
        }

        return closestRay;
    }

}
