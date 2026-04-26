package dev.ryanhcode.sable.mixin.clip_overwrite;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.ClipContextExtension;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.LevelPoseProviderExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.function.Predicate;

/**
 * Overwrites raycasts to take sublevels into account
 *
 * TODO: The priority is currently higher simply to take priority over lithium, but usage of Lithium's raycast replacement alongside our sub-level stuff would be far nicer.
 */
@Mixin(value = BlockGetter.class, priority = 1100)
public interface BlockGetterMixin {

    @Shadow
    BlockState getBlockState(BlockPos blockPos);

    /**
     * @author RyanH
     * @reason Wrap raycasts to take sublevels into account
     */
    @WrapMethod(method = "clip")
    default BlockHitResult clip(ClipContext clipContext, Operation<BlockHitResult> original) {

        if (!(this instanceof final Level level) || (clipContext instanceof final ClipContextExtension extension && extension.sable$doNotProject())) {
            // If the level cannot have sublevels, use the original method
            return original.call(clipContext);
        }

        final SubLevel ignoredSubLevel = clipContext instanceof final ClipContextExtension extension ?
                extension.sable$getIgnoredSubLevel() : null;

        final Predicate<SubLevel> subLevelIgnoring = clipContext instanceof final ClipContextExtension extension ?
                extension.sable$getSubLevelIgnoring() : null;

        final ActiveSableCompanion helper = Sable.HELPER;

        // if the context is already within a sub-level, project outward
        final SubLevel fromSubLevel = helper.getContaining(level, clipContext.getFrom());
        if (fromSubLevel != null) {
            Pose3dc pose = fromSubLevel.logicalPose();

            if (level instanceof final LevelPoseProviderExtension extension) {
                pose = extension.sable$getPose(fromSubLevel);
            }

            final Vector3dc from = pose.transformPosition(JOMLConversion.toJOML(clipContext.getFrom()));
            clipContext = new ClipContext(JOMLConversion.toMojang(from), clipContext.getTo(), clipContext.block, clipContext.fluid, clipContext.collisionContext);
        }

        final SubLevel toSubLevel = helper.getContaining(level, clipContext.getTo());
        if (toSubLevel != null) {
            Pose3dc pose = toSubLevel.logicalPose();

            if (level instanceof final LevelPoseProviderExtension extension) {
                pose = extension.sable$getPose(toSubLevel);
            }

            final Vector3dc to = pose.transformPosition(JOMLConversion.toJOML(clipContext.getTo()));
            clipContext = new ClipContext(clipContext.getFrom(), JOMLConversion.toMojang(to), clipContext.block, clipContext.fluid, clipContext.collisionContext);
        }

        BlockHitResult minResult;
        double minDistance = Double.MAX_VALUE;

        if (clipContext instanceof final ClipContextExtension extension && extension.sable$isIgnoreMainLevel()) {
            final Vec3 diff = clipContext.getFrom().subtract(clipContext.getTo());
            minResult = BlockHitResult.miss(clipContext.getTo(), Direction.getNearest(diff.x, diff.y, diff.z), BlockPos.containing(clipContext.getTo()));
        } else {
            minResult = original.call(clipContext);
            minDistance = minResult.getLocation().distanceTo(clipContext.getFrom());
        }


        final BoundingBox3d bounds = new BoundingBox3d(clipContext.getFrom(), clipContext.getTo());
        final Iterable<SubLevel> subLevels = helper.getAllIntersecting(level, bounds);

        for (final SubLevel subLevel : subLevels) {
            if (subLevel == ignoredSubLevel || (subLevelIgnoring != null && subLevelIgnoring.test(subLevel))) {
                continue; // skip the data we are ignoring
            }

            // Do the raycast within the data
            Pose3dc pose = subLevel.logicalPose();

            if (level instanceof final LevelPoseProviderExtension extension) {
                pose = extension.sable$getPose(subLevel);
            }

            final Vector3dc from = pose.transformPositionInverse(JOMLConversion.toJOML(clipContext.getFrom()));
            final Vector3dc to = pose.transformPositionInverse(JOMLConversion.toJOML(clipContext.getTo()));

            if (helper.getContaining(level, from) != subLevel)
                continue; // we projected the ray inward, but the start is not in the plot. something is weird.


            final ClipContext subClipContext = new ClipContext(JOMLConversion.toMojang(from), JOMLConversion.toMojang(to), clipContext.block, clipContext.fluid, clipContext.collisionContext);
            final BlockHitResult subResult = original.call(subClipContext);
            final double distance = subResult.getLocation().distanceTo(subClipContext.getFrom());

            if ((distance < minDistance || minResult.getType() == HitResult.Type.MISS) && subResult.getType() != HitResult.Type.MISS) {
                minResult = subResult;
                minDistance = distance;
            }
        }

        return minResult;
    }

}
