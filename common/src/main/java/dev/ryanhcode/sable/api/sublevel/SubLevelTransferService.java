package dev.ryanhcode.sable.api.sublevel;

import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.ryanhcode.sable.sublevel.tracking_points.SubLevelTrackingPointSavedData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Atomically replaces a group of loaded server sub-levels with equivalent instances in another level.
 */
public final class SubLevelTransferService {
    private SubLevelTransferService() {
    }

    /**
     * Transfers a sub-level and its complete loading-dependency chain to another level.
     * The destination root pose defines the rigid transform applied to every body in the chain.
     * This method must run on the server thread and outside a physics step.
     *
     * @param root                root sub-level to transfer
     * @param destinationLevel    destination parent level
     * @param destinationRootPose desired pose of the root in the destination level
     * @return all replacement sub-level instances
     */
    public static SubLevelTransferResult transfer(
            final ServerSubLevel root,
            final ServerLevel destinationLevel,
            final Pose3dc destinationRootPose) {
        validateRequest(root, destinationLevel);

        final ServerLevel sourceLevel = root.getLevel();
        final ServerSubLevelContainer sourceContainer = requireContainer(sourceLevel);
        final ServerSubLevelContainer destinationContainer = requireContainer(destinationLevel);
        final List<ServerSubLevel> sources = collectSources(root, sourceLevel, destinationContainer);
        final List<UUID> dependencyIds = sources.stream().map(SubLevel::getUniqueId).toList();
        final Map<UUID, SubLevelData> snapshots = new LinkedHashMap<>();
        final Map<UUID, Pose3d> destinationPoses = new LinkedHashMap<>();

        final Pose3dc sourceRootPose = root.logicalPose();
        final Quaterniond worldRotation = new Quaterniond(destinationRootPose.orientation())
                .mul(new Quaterniond(sourceRootPose.orientation()).conjugate());

        for (final ServerSubLevel source : sources) {
            snapshots.put(source.getUniqueId(), SubLevelSerializer.toData(source, dependencyIds));
            destinationPoses.put(source.getUniqueId(), transformPose(
                    source.logicalPose(), sourceRootPose, destinationRootPose, worldRotation));
        }

        final Map<UUID, ServerSubLevel> replacements = new LinkedHashMap<>();
        final List<TransferredEntity> transferredEntities = new ArrayList<>();
        try {
            for (final ServerSubLevel source : sources) {
                final UUID uuid = source.getUniqueId();
                final ServerSubLevel replacement = SubLevelSerializer.fullyLoadForTransfer(
                        destinationLevel,
                        snapshots.get(uuid),
                        destinationPoses.get(uuid),
                        worldRotation);
                if (replacement == null) {
                    throw new IllegalStateException("Unable to allocate destination sub-level " + uuid);
                }
                replacements.put(uuid, replacement);
            }
            transferPlotEntities(sources, replacements, destinationLevel, transferredEntities);
        } catch (final RuntimeException exception) {
            rollbackEntities(transferredEntities);
            rollback(destinationContainer, replacements);
            throw exception;
        }

        for (final ServerSubLevel source : sources) {
            sourceContainer.transferTicketsTo(source, destinationContainer, replacements.get(source.getUniqueId()));
            SubLevelTrackingPointSavedData.transferSubLevelPoints(source, replacements.get(source.getUniqueId()));
        }
        for (final ServerSubLevel source : sources) {
            sourceContainer.removeSubLevel(source, SubLevelRemovalReason.TRANSFERRED);
        }
        for (final ServerSubLevel source : sources) {
            final ServerSubLevel replacement = replacements.get(source.getUniqueId());
            sourceContainer.notifySubLevelTransferred(source, replacement);
            destinationContainer.notifySubLevelTransferred(source, replacement);
        }

        return new SubLevelTransferResult(replacements.get(root.getUniqueId()), replacements);
    }

    private static void validateRequest(final ServerSubLevel root, final ServerLevel destinationLevel) {
        if (root.isRemoved()) {
            throw new IllegalArgumentException("Cannot transfer a removed sub-level");
        }

        final ServerLevel sourceLevel = root.getLevel();
        final MinecraftServer server = sourceLevel.getServer();
        if (server != destinationLevel.getServer()) {
            throw new IllegalArgumentException("Source and destination levels belong to different servers");
        }
        if (sourceLevel == destinationLevel) {
            throw new IllegalArgumentException("Source and destination levels must be different");
        }
        if (!server.isSameThread()) {
            throw new IllegalStateException("Sub-level transfer must run on the server thread");
        }
        if (SubLevelPhysicsSystem.IN_PHYSICS_STEP) {
            throw new IllegalStateException("Sub-level transfer cannot run during a physics step");
        }
    }

    private static ServerSubLevelContainer requireContainer(final ServerLevel level) {
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            throw new IllegalStateException("Level " + level.dimension().location() + " has no sub-level container");
        }
        return container;
    }

    private static List<ServerSubLevel> collectSources(
            final ServerSubLevel root,
            final ServerLevel sourceLevel,
            final ServerSubLevelContainer destinationContainer) {
        final List<ServerSubLevel> sources = new ArrayList<>();
        for (final ServerSubLevel source : SubLevelHelper.getLoadingDependencyChain(root)) {
            if (source.getLevel() != sourceLevel) {
                throw new IllegalStateException("A loading dependency already belongs to another level: " + source.getUniqueId());
            }
            if (destinationContainer.getSubLevel(source.getUniqueId()) != null) {
                throw new IllegalStateException("Destination already contains sub-level " + source.getUniqueId());
            }
            sources.add(source);
        }
        return sources;
    }

    private static Pose3d transformPose(
            final Pose3dc source,
            final Pose3dc sourceRoot,
            final Pose3dc destinationRoot,
            final Quaterniond worldRotation) {
        final Vector3d transformedPosition = sourceRoot.transformPositionInverse(new Vector3d(source.position()));
        destinationRoot.transformPosition(transformedPosition);

        final Pose3d transformed = new Pose3d(source);
        transformed.position().set(transformedPosition);
        transformed.orientation().set(worldRotation).mul(source.orientation());
        return transformed;
    }

    private static void rollback(
            final ServerSubLevelContainer destinationContainer,
            final Map<UUID, ServerSubLevel> replacements) {
        final List<ServerSubLevel> reverseOrder = new ArrayList<>(replacements.values());
        for (int i = reverseOrder.size() - 1; i >= 0; i--) {
            final ServerSubLevel replacement = reverseOrder.get(i);
            if (!replacement.isRemoved()) {
                destinationContainer.removeSubLevel(replacement, SubLevelRemovalReason.REMOVED);
            }
        }
    }

    private static void transferPlotEntities(
            final List<ServerSubLevel> sources,
            final Map<UUID, ServerSubLevel> replacements,
            final ServerLevel destinationLevel,
            final List<TransferredEntity> transferredEntities) {
        for (final ServerSubLevel source : sources) {
            final ServerSubLevel replacement = replacements.get(source.getUniqueId());
            final BlockPosOffset offset = BlockPosOffset.between(
                    source.getPlot().getCenterBlock(), replacement.getPlot().getCenterBlock());

            for (final Entity entity : source.getPlot().collectRootEntities()) {
                final Vec3 sourcePosition = entity.position();
                final Vec3 sourceVelocity = entity.getDeltaMovement();
                final float sourceYRot = entity.getYRot();
                final float sourceXRot = entity.getXRot();
                final Vec3 destinationPosition = sourcePosition.add(offset.x(), offset.y(), offset.z());
                final Entity destinationEntity = entity.changeDimension(new DimensionTransition(
                        destinationLevel,
                        destinationPosition,
                        sourceVelocity,
                        sourceYRot,
                        sourceXRot,
                        DimensionTransition.DO_NOTHING));
                if (destinationEntity == null) {
                    throw new IllegalStateException("Entity " + entity.getUUID() + " rejected sub-level transfer");
                }
                transferredEntities.add(new TransferredEntity(
                        destinationEntity,
                        source.getLevel(),
                        sourcePosition,
                        sourceVelocity,
                        sourceYRot,
                        sourceXRot));
            }
        }
    }

    private static void rollbackEntities(final List<TransferredEntity> transferredEntities) {
        for (int i = transferredEntities.size() - 1; i >= 0; i--) {
            final TransferredEntity transfer = transferredEntities.get(i);
            transfer.entity().changeDimension(new DimensionTransition(
                    transfer.sourceLevel(),
                    transfer.sourcePosition(),
                    transfer.sourceVelocity(),
                    transfer.yRot(),
                    transfer.xRot(),
                    DimensionTransition.DO_NOTHING));
        }
    }

    private record BlockPosOffset(int x, int y, int z) {
        private static BlockPosOffset between(
                final net.minecraft.core.BlockPos source,
                final net.minecraft.core.BlockPos destination) {
            return new BlockPosOffset(
                    destination.getX() - source.getX(),
                    destination.getY() - source.getY(),
                    destination.getZ() - source.getZ());
        }
    }

    private record TransferredEntity(
            Entity entity,
            ServerLevel sourceLevel,
            Vec3 sourcePosition,
            Vec3 sourceVelocity,
            float yRot,
            float xRot) {
    }
}
