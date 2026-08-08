package dev.ryanhcode.sable.mixin.prevent_freezing;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Mixin into {@link net.minecraft.world.level.biome.Biome} to prevent water from freezing beneath sub-level blocks projected into the same space.
 */
@Mixin(Biome.class)
public class BiomeMixin {

    @WrapMethod(method = "shouldFreeze(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Z)Z")
    public boolean sable$preventFreezing(final LevelReader levelReader, final BlockPos blockPos, final boolean bl, final Operation<Boolean> original) {
        if (!original.call(levelReader, blockPos, bl)) {
            return false;
        }

        if (levelReader instanceof final Level level) {
            final ActiveSableCompanion helper = Sable.HELPER;
            final SubLevel parent = helper.getContaining(level, blockPos);
            BlockPos projectedPos = blockPos;
            if (parent != null) { //inverse project block pos
                projectedPos = BlockPos.containing(parent.logicalPose().transformPosition(projectedPos.getCenter()));
            }
            final BoundingBox3d bb3d = new BoundingBox3d(projectedPos);

            final Iterable<SubLevel> allIntersecting = helper.getAllIntersecting(level, bb3d);
            for (final SubLevel subLevel : allIntersecting) {
                if (subLevel == parent) {
                    continue;
                }

                bb3d.set(projectedPos.getX(), projectedPos.getY(), projectedPos.getZ(), projectedPos.getX() + 1, projectedPos.getY() + 1, projectedPos.getZ() + 1);
                bb3d.transformInverse(subLevel.logicalPose());
                if (BlockPos.betweenClosedStream(bb3d.toMojang()).anyMatch(p -> !level.getBlockState(p).canBeReplaced())) {
                    return false;
                }
            }
        }

        return true;
    }


}
