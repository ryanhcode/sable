package dev.ryanhcode.sable.mixin.portal;

import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.portal.PortalForcer;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

@Mixin(PortalForcer.class)
public class PortalForcerMixin {

    @Shadow
    @Final
    private ServerLevel level;

    @Redirect(method = "findClosestPortalPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/village/poi/PoiManager;getInSquare(Ljava/util/function/Predicate;Lnet/minecraft/core/BlockPos;ILnet/minecraft/world/entity/ai/village/poi/PoiManager$Occupancy;)Ljava/util/stream/Stream;"))
    private Stream<PoiRecord> sable$findClosestPortalPosition(final PoiManager instance, final Predicate<Holder<PoiType>> typePredicate, final BlockPos pos, final int distance, final PoiManager.Occupancy status) {
        final Stream<PoiRecord> globalTargets = instance.getInSquare(typePredicate, pos, distance, status);
        final BoundingBox3d bounds = new BoundingBox3d(pos).expand(distance);

        bounds.maxY = this.level.getMaxBuildHeight();
        bounds.minY = this.level.getMinBuildHeight();

        final List<Stream<PoiRecord>> streams = new ArrayList<>();
        streams.add(globalTargets);

        // Preload sublevels so we can check them for portals, they would normally get loaded during teleportation anyway so this shouldn't impact performance
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(this.level);
        assert container != null : "Sub-level container is null";
        if (!container.inBounds(pos)) {
            final SubLevelHoldingChunkMap holdingChunkMap = container.getHoldingChunkMap();

            final int i = Math.floorDiv(distance, 16) + 1;
            ChunkPos.rangeClosed(new ChunkPos(pos), i).forEach((chunkPos) -> {
                final SubLevelHoldingChunk holdingChunk = holdingChunkMap.getOrLoadHoldingChunk(chunkPos, false);
                if (holdingChunk == null) return;
                final List<UUID> toLoad = new ArrayList<>();
                for (final HoldingSubLevel holdingSubLevel : holdingChunk.getLoadedHoldingSubLevels()) {
                    final SubLevelData subLevelData = holdingSubLevel.data();
                    if (subLevelData.bounds().intersects(bounds)) {
                        toLoad.add(subLevelData.uuid());
                    }
                }
                for (final UUID uuid : toLoad) {
                    final Collection<HoldingSubLevel> holdingSubLevels = holdingChunk.snatch(uuid);
                    if (holdingSubLevels != null) {
                        for (final HoldingSubLevel holdingSubLevel : holdingSubLevels) {
                            holdingChunkMap.loadHoldingSubLevel(holdingSubLevel);
                        }
                    }
                }
                container.getAllSubLevels().forEach(SubLevel::updateBoundingBox);
            });

            for (final SubLevel intersecting : Sable.HELPER.getAllIntersecting(this.level, bounds)) {
                /*
                 * PoiManager.getInSquare ignores height/y-coordinates, but a sublevel might not have y pointing upward in globalSpace
                 * So we instead query all Portals in the sublevel and run our own distance check
                 */
                streams.add(instance.getInSquare(typePredicate, intersecting.getPlot().getCenterBlock(), (int) (Math.pow(2, intersecting.getPlot().logSize - 1)) * 16, status).filter(record -> {
                    final Vec3 pos1 = record.getPos().getCenter();
                    final Vec3 pos2 = intersecting.logicalPose().transformPositionInverse(pos.getCenter());
                    final Quaterniond orientation = intersecting.logicalPose().orientation();

                    final Vec3 localDelta = pos1.subtract(pos2);
                    final Vector3d globalDelta = orientation.transform(new Vector3d(localDelta.x, localDelta.y, localDelta.z), new Vector3d());
                    final double horizontalDistanceSqr = globalDelta.x * globalDelta.x + globalDelta.z * globalDelta.z;

                    return horizontalDistanceSqr <= (double) distance * distance;
                }));
            }
        }

        return streams.stream().flatMap(Function.identity());
    }

    @Redirect(method = "findClosestPortalPosition", at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;min(Ljava/util/Comparator;)Ljava/util/Optional;"))
    private Optional<BlockPos> sable$findClosestPortalPosition2(final Stream<BlockPos> instance, final Comparator<? super PoiRecord> comparator, @Local(name = "exitPos") final BlockPos exitPos) {
        final Comparator<BlockPos> typedComparator = Comparator.comparingDouble((blockPos) -> Sable.HELPER.distanceSquaredWithSubLevels(this.level, blockPos.getCenter(), exitPos.getCenter()));
        return instance.min(typedComparator);
    }
}
