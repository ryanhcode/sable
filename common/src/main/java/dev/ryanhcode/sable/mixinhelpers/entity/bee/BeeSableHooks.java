package dev.ryanhcode.sable.mixinhelpers.entity.bee;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class BeeSableHooks {
    private BeeSableHooks() {
    }

    public static boolean closerThan(final Bee bee, final BlockPos pos, final int distance) {
        return closerThan(bee, pos, (double) distance);
    }

    public static boolean closerThan(final Bee bee, final BlockPos pos, final double distance) {
        final double maxDistanceSquared = distance * distance;
        return Sable.HELPER.distanceSquaredWithSubLevels(bee.level(), pos.getCenter(), bee.position()) < maxDistanceSquared;
    }

    public static List<BlockPos> findNearbyHivesWithSpace(final Bee bee) {
        final Level level = bee.level();
        if (!(level instanceof ServerLevel serverLevel)) {
            return List.of();
        }

        final SubLevelAccess trackingSubLevel = Sable.HELPER.getTrackingOrVehicleSubLevel(bee);
        final Position origin = getSearchOrigin(bee, trackingSubLevel);
        final List<BlockPos> hives = Sable.HELPER.runIncludingSubLevels(level, origin, true, trackingSubLevel,
                (subLevel, candidateOrigin) -> findNearbyHivesWithSpace(serverLevel, bee, candidateOrigin));
        return hives == null ? List.of() : hives;
    }

    public static Optional<BlockPos> findNearestFlower(final Bee bee, final Predicate<BlockState> predicate, final double range) {
        final SubLevelAccess trackingSubLevel = Sable.HELPER.getTrackingOrVehicleSubLevel(bee);
        final Position origin = getSearchOrigin(bee, trackingSubLevel);
        final Optional<BlockPos> flower = Sable.HELPER.runIncludingSubLevels(bee.level(), origin, true, trackingSubLevel,
                (subLevel, candidateOrigin) -> findNearestBlock(bee, predicate, range, candidateOrigin));
        return flower == null ? Optional.empty() : flower;
    }

    public static Vec3 projectHoverTarget(final Bee bee, final BlockPos flowerPos) {
        return Sable.HELPER.projectOutOfSubLevel(bee.level(), Vec3.atBottomCenterOf(flowerPos));
    }

    private static Position getSearchOrigin(final Bee bee, final SubLevelAccess trackingSubLevel) {
        if (trackingSubLevel == null) {
            return bee.position();
        }

        return trackingSubLevel.logicalPose().transformPositionInverse(bee.position());
    }

    private static List<BlockPos> findNearbyHivesWithSpace(final ServerLevel level, final Bee bee, final BlockPos origin) {
        final PoiManager poiManager = level.getPoiManager();
        final Stream<PoiRecord> records = poiManager.getInRange(record -> record.is(PoiTypeTags.BEE_HOME), origin, 20, PoiManager.Occupancy.ANY);
        final List<BlockPos> hives = records.map(PoiRecord::getPos)
                .filter(pos -> hasHiveSpace(level, pos))
                .sorted(Comparator.comparingDouble(pos -> Sable.HELPER.distanceSquaredWithSubLevels(bee.level(), pos.getCenter(), bee.position())))
                .collect(Collectors.toList());
        return hives.isEmpty() ? null : hives;
    }

    private static boolean hasHiveSpace(final Level level, final BlockPos pos) {
        if (!level.getBlockState(pos).is(BlockTags.BEEHIVES)) {
            return false;
        }

        final BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity instanceof BeehiveBlockEntity beehive && !beehive.isFull();
    }

    private static Optional<BlockPos> findNearestBlock(final Bee bee, final Predicate<BlockState> predicate, final double range, final BlockPos origin) {
        final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int y = 0; (double) y <= range; y = y > 0 ? -y : 1 - y) {
            for (int radius = 0; (double) radius < range; radius++) {
                for (int x = 0; x <= radius; x = x > 0 ? -x : 1 - x) {
                    for (int z = x < radius && x > -radius ? radius : 0; z <= radius; z = z > 0 ? -z : 1 - z) {
                        mutablePos.setWithOffset(origin, x, y - 1, z);
                        if (isWithinSableAwareRange(bee.level(), bee.position(), mutablePos.getCenter(), range)
                                && predicate.test(bee.level().getBlockState(mutablePos))) {
                            return Optional.of(mutablePos.immutable());
                        }
                    }
                }
            }
        }

        return Optional.empty();
    }

    private static boolean isWithinSableAwareRange(final Level level, final Position origin, final Position candidate, final double range) {
        return Sable.HELPER.distanceSquaredWithSubLevels(level, origin, candidate) < range * range;
    }
}
