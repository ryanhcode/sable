package dev.ryanhcode.sable.mixin.entity.entity_pathfinding;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.RandomPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(RandomPos.class)
public class RandomPosMixin {

    /**
     * @author RyanH
     * @reason Wandering on sub-levels
     */
    @Overwrite
    public static BlockPos generateRandomPosTowardDirection(final PathfinderMob mob, final int someInteger, final RandomSource random, final BlockPos pos) {
        final SubLevel trackingSubLevel = Sable.HELPER.getTrackingSubLevel(mob);
        Vec3 effectiveMobPos = mob.position();

        if (trackingSubLevel != null) {
            effectiveMobPos = trackingSubLevel.logicalPose().transformPositionInverse(effectiveMobPos);
        }

        int ox = pos.getX();
        int oz = pos.getZ();

        if (mob.hasRestriction() && someInteger > 1) {
            final BlockPos blockPos = mob.getRestrictCenter();
            if (effectiveMobPos.x() > (double) blockPos.getX()) {
                ox -= random.nextInt(someInteger / 2);
            } else {
                ox += random.nextInt(someInteger / 2);
            }

            if (effectiveMobPos.z() > (double) blockPos.getZ()) {
                oz -= random.nextInt(someInteger / 2);
            } else {
                oz += random.nextInt(someInteger / 2);
            }
        }

        return BlockPos.containing((double) ox + effectiveMobPos.x(), (double) pos.getY() + effectiveMobPos.y(), (double) oz + effectiveMobPos.z());
    }

}
