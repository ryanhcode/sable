package dev.ryanhcode.sable.api;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.platform.SableAssemblyPlatform;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.ryanhcode.sable.sublevel.tracking_points.SubLevelTrackingPointSavedData;
import dev.ryanhcode.sable.sublevel.tracking_points.TrackingPoint;
import dev.ryanhcode.sable.util.BoundedBitVolume3i;
import dev.ryanhcode.sable.util.LevelAccelerator;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Clearable;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2i;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.*;

/**
 * Utility class for mass movement of collections of blocks between world and plot.
 */
public class SubLevelAssemblyHelper {

    /**
     * Assembles a collection of blocks into a sub-level.
     *
     * @param level  the level in which the blocks are located
     * @param anchor the block that will be placed at the center of the sub-level
     * @param blocks all blocks that will be assembled into the sub-level
     * @param bounds the bounds in which {@link TrackingPoint tracking points} and retained entities will be moved
     */
    public static ServerSubLevel assembleBlocks(final ServerLevel level, final BlockPos anchor, final Iterable<BlockPos> blocks, final BoundingBox3ic bounds) {
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        assert container != null;

        final SubLevel containingSubLevel = Sable.HELPER.getContaining(level, anchor);
        final Pose3d pose = new Pose3d();

        pose.position().set(anchor.getX() + 0.5, anchor.getY() + 0.5, anchor.getZ() + 0.5);
        if (containingSubLevel != null) {
            final Pose3d containingPose = containingSubLevel.logicalPose();
            containingPose.transformPosition(pose.position());
            pose.orientation().set(containingPose.orientation());
        }

        final ServerSubLevel subLevel = (ServerSubLevel) container.allocateNewSubLevel(pose);

        final LevelPlot plot = subLevel.getPlot();
        plot.newEmptyChunk(plot.getCenterChunk());

        final BlockPos plotAnchor = plot.getCenterBlock();
        final SubLevelAssemblyHelper.AssemblyTransform transform = new SubLevelAssemblyHelper.AssemblyTransform(anchor, plotAnchor, 0, Rotation.NONE, level);
        SubLevelAssemblyHelper.moveOtherStuff(level, transform, blocks, bounds);
        SubLevelAssemblyHelper.moveBlocks(level, transform, blocks);

        final Vector3dc centerOfMass = subLevel.getMassTracker().getCenterOfMass();
        Vec3 subLevelCenter = Vec3.atLowerCornerOf(anchor);

        if (centerOfMass != null) {
            subLevelCenter = subLevelCenter
                    .subtract(Vec3.atLowerCornerOf(plotAnchor))
                    .add(centerOfMass.x(), centerOfMass.y(), centerOfMass.z());
        } else {
            subLevel.logicalPose().rotationPoint()
                    .set(plotAnchor.getX() + 0.5, plotAnchor.getY() + 0.5, plotAnchor.getZ() + 0.5);
        }

        subLevel.logicalPose().position().set(subLevelCenter.x, subLevelCenter.y, subLevelCenter.z);

        final SubLevelPhysicsSystem physicsSystem = container.physicsSystem();
        final PhysicsPipeline pipeline = physicsSystem.getPipeline();

        if (containingSubLevel != null) {
            kickFromContainingSubLevel(level, physicsSystem, pipeline, subLevel, containingSubLevel);
        }

        pipeline.teleport(subLevel, subLevel.logicalPose().position(), subLevel.logicalPose().orientation());
        subLevel.updateLastPose();

        SubLevelAssemblyHelper.moveTrackingPoints(level, bounds, subLevel, transform);

        return subLevel;
    }

    @ApiStatus.Internal
    public static void kickFromContainingSubLevel(final ServerLevel level,
                                                  final SubLevelPhysicsSystem physicsSystem,
                                                  final PhysicsPipeline pipeline,
                                                  final ServerSubLevel subLevel,
                                                  final SubLevel containingSubLevel) {
        final Pose3d originalPose = new Pose3d(subLevel.logicalPose());

        final Vector3d velocity = Sable.HELPER.getVelocity(level, subLevel.logicalPose().position(), new Vector3d());
        final RigidBodyHandle containingHandle = physicsSystem.getPhysicsHandle((ServerSubLevel) containingSubLevel);
        pipeline.addLinearAndAngularVelocity(subLevel, velocity, containingHandle.getAngularVelocity());

        // re-transform after center of mass is fixed
        // we don't need to set the orientation again as it couldn't have changed
        final Pose3d containingPose = containingSubLevel.logicalPose();
        containingPose.transformPosition(subLevel.logicalPose().position());

        subLevel.setSplitFrom((ServerSubLevel) containingSubLevel, originalPose);
    }

    /**
     * Attempts to gather all connected blocks from a given assembly origin. <br/>
     * searches in a 3x3x3 area around every block
     *
     * @param gatherOrigin            Origin of the gathering process.
     * @param level                   The level this gathering is taking place in.
     * @param maximumBlocksToAssemble the maximum blocks to gather.
     * @param frontierPredicate       A specific predicate analysed per blockpos visited that is not an AIR block. Exposes the current BlockPos candidate and its blockstate.
     * @return a {@link GatherResult gather result} that holds the blocks gathered, bounds of the volume, and an error state if gathering was unsuccessful.
     */
    public static @NotNull SubLevelAssemblyHelper.GatherResult gatherConnectedBlocks(final BlockPos gatherOrigin, final ServerLevel level, final int maximumBlocksToAssemble, @Nullable final FrontierPredicate frontierPredicate) {
        final LinkedHashSet<Pair<BlockPos, BlockState>> frontier = new LinkedHashSet<>(1 << 12);
        final Set<BlockPos> blocks = new ObjectOpenHashSet<>(1 << 10);
        final LevelAccelerator accelerator = new LevelAccelerator(level);

        final BlockState gatherOriginState = accelerator.getBlockState(gatherOrigin);

        if (gatherOriginState.isAir()) {
            return new GatherResult(null, 0, null, GatherResult.State.NO_BLOCKS);
        }

        frontier.add(Pair.of(gatherOrigin, gatherOriginState));

        int minX = gatherOrigin.getX(), minY = gatherOrigin.getY(), minZ = gatherOrigin.getZ();
        int maxX = gatherOrigin.getX(), maxY = gatherOrigin.getY(), maxZ = gatherOrigin.getZ();


        int blockCount = 0;
        final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        while (!frontier.isEmpty()) {
            final Pair<BlockPos, BlockState> pair = frontier.removeFirst();
            final BlockPos pos = pair.key();

            blockCount++;
            if (blockCount > maximumBlocksToAssemble) {
                return new GatherResult(null, blockCount, null, GatherResult.State.TOO_MANY_BLOCKS);
            }

            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());

            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());

            blocks.add(pos);

            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        if (x == 0 && y == 0 && z == 0) {
                            continue;
                        }

                        // don't connect corners, only edges
                        final int absTotal = Math.abs(x) + Math.abs(y) + Math.abs(z);
                        if (absTotal == 3) {
                            continue;
                        }

                        final BlockPos candidate = mutablePos.set(pos.getX() + x, pos.getY() + y, pos.getZ() + z);

                        if (frontier.contains(candidate)) {
                            continue;
                        }

                        final Direction direction = absTotal == 1 ? Direction.fromDelta(x, y, z) : null;
                        final BlockState candidateState = accelerator.getBlockState(candidate);

                        if (candidateState.isAir()) {
                            continue;
                        }

                        if (frontierPredicate != null && !frontierPredicate.isValidConnection(pos, pair.second(), candidate, candidateState, direction)) {
                            continue;
                        }

                        if (!blocks.contains(candidate)) {
                            frontier.add(Pair.of(candidate.immutable(), candidateState));
                        }
                    }
                }
            }
        }

        final BoundingBox3i bounds = new BoundingBox3i(
                minX, minY, minZ,
                maxX, maxY, maxZ
        );

        if (blocks.isEmpty()) {
            return new GatherResult(null, blockCount, null, GatherResult.State.NO_BLOCKS);
        }

        return new GatherResult(blocks, blockCount, bounds, GatherResult.State.SUCCESS);
    }

    public static void moveTrackingPoints(final ServerLevel level, final BoundingBox3ic bounds, final ServerSubLevel subLevel, final AssemblyTransform transform) {
        final SubLevelTrackingPointSavedData data = SubLevelTrackingPointSavedData.getOrLoad(level);
        final Iterable<Pair<UUID, TrackingPoint>> points = data.getAllTrackingPoints(bounds);

        for (final Pair<UUID, TrackingPoint> entry : points) {
            final UUID key = entry.key();
            final TrackingPoint point = new TrackingPoint(
                    subLevel != null,
                    subLevel != null ? subLevel.getUniqueId() : null,
                    subLevel != null ? subLevel.getLastSerializationPointer() : null,
                    JOMLConversion.toJOML(transform.apply(JOMLConversion.toMojang(entry.value().point()))),
                    entry.value().globalPlaceholderPosition()
            );

            data.setTrackingPoint(key, point);
        }
    }

    public static void moveOtherStuff(final ServerLevel level, final AssemblyTransform transform, final Iterable<BlockPos> blocks, final BoundingBox3ic bounds) {
        final List<Entity> entities = level.getEntitiesOfClass(Entity.class, bounds.toAABB().inflate(2.0));
        final boolean needsBitSet = needsBitSet(level, bounds, entities);

        if (!needsBitSet) return;

        final BoundedBitVolume3i volume = BoundedBitVolume3i.fromBlocks(blocks);
        assert volume != null;

        for (final Entity entity : entities) {
            boolean moveEntity = false;

            if (entity instanceof final HangingEntity hangingEntity) {
                moveEntity = BlockPos.betweenClosedStream(hangingEntity.calculateSupportBox()).anyMatch(blockPos ->
                        volume.getOccupied(blockPos.getX(), blockPos.getY(), blockPos.getZ()));
            }

            if (moveEntity) {
                entity.setPos(transform.apply(entity.position()));
            }
        }
    }

    private static boolean needsBitSet(final ServerLevel level, final BoundingBox3ic bounds, final List<Entity> entities) {
        return !entities.isEmpty();
    }

    /**
     * For what good is the movement of a king if his people do not follow?
     */
    public static void moveBlocks(final ServerLevel level, final AssemblyTransform transform, final Iterable<BlockPos> blocks) {
        final ServerLevel resultingLevel = transform.resultingLevel;

        final LevelAccelerator accelerator = new LevelAccelerator(level);
        final LevelAccelerator resultingAccelerator = new LevelAccelerator(resultingLevel);

        final List<BlockState> states = new ArrayList<>();

        BlockPos firstBlock = null;
        Vector2i chunkBoundsMin = null;
        Vector2i chunkBoundsMax = null;
        for (final BlockPos block : blocks) {
            if (firstBlock == null) {
                firstBlock = block;
            }
            final ChunkPos chunk = new ChunkPos(transform.apply(block));

            final Vector2i jomlChunkPos = new Vector2i(chunk.x, chunk.z);
            if (chunkBoundsMin == null) {
                chunkBoundsMin = new Vector2i(jomlChunkPos);
                chunkBoundsMax = new Vector2i(jomlChunkPos);
            }

            chunkBoundsMin.min(jomlChunkPos);
            chunkBoundsMax.max(jomlChunkPos);
        }

        final SubLevel subLevel = Sable.HELPER.getContaining(level, transform.apply(firstBlock));
        if (subLevel != null) {
            final LevelPlot plot = subLevel.getPlot();

            for (int chunkX = chunkBoundsMin.x; chunkX <= chunkBoundsMax.x; chunkX++) {
                for (int chunkZ = chunkBoundsMin.y; chunkZ <= chunkBoundsMax.y; chunkZ++) {
                    if (plot.getChunkHolder(plot.toLocal(new ChunkPos(chunkX, chunkZ))) == null) {
                        plot.newEmptyChunk(new ChunkPos(chunkX, chunkZ));
                    }
                }
            }
        }

        SableAssemblyPlatform.INSTANCE.setIgnoreOnPlace(resultingLevel, true);
        for (final BlockPos block : blocks) {
            final BlockState state = accelerator.getBlockState(block);
            final BlockPos newPos = transform.apply(block);

            try {
                final BlockState subLevelState = transform.apply(state);

                if (state.getBlock() instanceof final BlockSubLevelAssemblyListener listener) {
                    listener.beforeMove(level, resultingLevel, state, block, newPos);
                }

                final BlockEntity blockEntity = level.getBlockEntity(block);

                CompoundTag tag = null;

                if (blockEntity != null) {
                    tag = blockEntity.saveWithFullMetadata(level.registryAccess());

                    tag.putInt("x", newPos.getX());
                    tag.putInt("y", newPos.getY());
                    tag.putInt("z", newPos.getZ());
                }

                if (blockEntity instanceof final RandomizableContainer container) {
                    container.setLootTable(null);
                }
                if (blockEntity instanceof final Clearable clearable) {
                    clearable.clearContent();
                }

                final LevelChunk chunk = resultingAccelerator.getChunk(SectionPos.blockToSectionCoord(newPos.getX()), SectionPos.blockToSectionCoord(newPos.getZ()));

                chunk.setBlockState(newPos, subLevelState, true);
                states.add(subLevelState);

                final BlockEntity newBlockEntity = resultingLevel.getBlockEntity(newPos);

                if (newBlockEntity != null && tag != null) {
                    newBlockEntity.loadWithComponents(tag, level.registryAccess());
                }

                if (state.getBlock() instanceof final BlockSubLevelAssemblyListener listener) {
                    listener.afterMove(level, resultingLevel, state, block, newPos);
                }
            } catch (final Exception e) {
                Sable.LOGGER.error("Failed to move block {} at {} to {}", state, block, newPos, e);
            }
        }
        SableAssemblyPlatform.INSTANCE.setIgnoreOnPlace(resultingLevel, false);

        int i = 0;
        for (final BlockPos untransformed : blocks) {
            final BlockPos pos = transform.apply(untransformed);

            try {
                final LevelChunk levelchunk = resultingAccelerator.getChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
                final BlockState subLevelState = states.get(i);
                SubLevelAssemblyHelper.markAndNotifyBlock(resultingLevel, pos, levelchunk, Blocks.AIR.defaultBlockState(), subLevelState, 3, 512);
            } catch (final Exception e) {
                Sable.LOGGER.error("Failed to mark & notify block {} (untransformed = {})", pos, untransformed, e);
            }

            i++;
        }

        SableAssemblyPlatform.INSTANCE.setIgnoreOnPlace(resultingLevel, true);
        // destroy all the old blocks
        for (final BlockPos block : blocks) {
            final BlockState subLevelState = Blocks.AIR.defaultBlockState();

            try {
                final LevelChunk chunk = accelerator.getChunk(SectionPos.blockToSectionCoord(block.getX()),
                        SectionPos.blockToSectionCoord(block.getZ()));

                chunk.setBlockState(block, subLevelState, true);
            } catch (final Exception e) {
                Sable.LOGGER.error("Failed to destroy old block during assembly {}", block, e);
            }
        }
        SableAssemblyPlatform.INSTANCE.setIgnoreOnPlace(resultingLevel, false);

        for (final BlockPos block : blocks) {
            final BlockState subLevelState = Blocks.AIR.defaultBlockState();
            resultingLevel.sendBlockUpdated(block, Blocks.STONE.defaultBlockState(), subLevelState, 3);
        }
    }

    public static void markAndNotifyBlock(final Level level, final BlockPos pPos, @Nullable final LevelChunk levelchunk, final BlockState oldState, final BlockState newState, final int pFlags, final int pRecursionLeft) {
        final Block block = newState.getBlock();
        final BlockState worldState = level.getBlockState(pPos);
        if (worldState == newState) {
            if (oldState != worldState) {
                level.setBlocksDirty(pPos, oldState, worldState);
            }

            if ((pFlags & 2) != 0 && levelchunk.getFullStatus() != null && levelchunk.getFullStatus().isOrAfter(FullChunkStatus.BLOCK_TICKING)) {
                level.sendBlockUpdated(pPos, oldState, newState, pFlags);
            }

            if ((pFlags & 1) != 0) {
                level.blockUpdated(pPos, oldState.getBlock());
                if (newState.hasAnalogOutputSignal()) {
                    level.updateNeighbourForOutputSignal(pPos, block);
                }
            }

            if ((pFlags & 16) == 0 && pRecursionLeft > 0) {
                final int i = pFlags & -34;
                oldState.updateIndirectNeighbourShapes(level, pPos, i, pRecursionLeft - 1);
                newState.updateNeighbourShapes(level, pPos, i, pRecursionLeft - 1);
                newState.updateIndirectNeighbourShapes(level, pPos, i, pRecursionLeft - 1);
            }

            level.onBlockStateChange(pPos, oldState, worldState);
        }
    }

    @FunctionalInterface
    public interface FrontierPredicate {

        /**
         * @param originPos     the pos that is attempting to connect to `pos`
         * @param originState   the state that is attempting to connect to `pos`
         * @param pos           the block we are trying to connect to
         * @param state         the state of the block we are trying to connect to
         * @param directionFrom the direction we are checking connection from, or null if the connection is along diagonals
         * @return if the connection is valid
         */
        boolean isValidConnection(BlockPos originPos, BlockState originState, BlockPos pos, BlockState state, @Nullable Direction directionFrom);

    }

    /**
     * Transform for assembly/dissasembly
     */
    public static class AssemblyTransform {

        private final BlockPos anchorPos;
        private final BlockPos resultingAnchorPos;

        /**
         * 90-degree counter clockwise increments
         */
        private final int angle;
        private final Rotation rotation;

        private final ServerLevel resultingLevel;

        public AssemblyTransform(final BlockPos anchorPos,
                                 final BlockPos resultingAnchorPos,
                                 final int angle,
                                 final Rotation rotation,
                                 final ServerLevel resultingLevel) {
            this.anchorPos = anchorPos;
            this.resultingAnchorPos = resultingAnchorPos;
            this.angle = angle;
            this.rotation = rotation;
            this.resultingLevel = resultingLevel;
        }

        public Vec3 apply(Vec3 pos) {
            pos = pos.subtract(this.anchorPos.getCenter())
                    .yRot((float) (this.angle * Math.PI / 2.0))
                    .add(this.resultingAnchorPos.getCenter());
            return pos;
        }

        public BlockPos apply(final BlockPos pos) {
            return BlockPos.containing(this.apply(pos.getCenter()));
        }

        public BlockState apply(BlockState state) {
            final Block block = state.getBlock();

            if (block instanceof BellBlock) {
                if (state.getValue(BlockStateProperties.BELL_ATTACHMENT) == BellAttachType.DOUBLE_WALL)
                    state = state.setValue(BlockStateProperties.BELL_ATTACHMENT, BellAttachType.SINGLE_WALL);
                return state.setValue(BellBlock.FACING,
                        this.rotation.rotate(state.getValue(BellBlock.FACING)));
            }

            return state.rotate(this.rotation);
        }

        public ServerLevel getLevel() {
            return this.resultingLevel;
        }

        public Rotation getRotation() {
            return this.rotation;
        }
    }

    /**
     * The result of {@link SubLevelAssemblyHelper#gatherConnectedBlocks(BlockPos, ServerLevel, int, FrontierPredicate)}  gather connected blocks.
     *
     * @param blocks        The blocks gathered during the process.
     * @param boundingBox   The total bounding box for this gathering
     * @param checkedBlocks How many blocks were checked in the process.
     * @param assemblyState The error state of this process.
     */
    public record GatherResult(@Nullable Set<BlockPos> blocks, int checkedBlocks, @Nullable BoundingBox3i boundingBox,
                               State assemblyState) {
        public enum State {
            SUCCESS("commands.sable.sub_level.assemble.connected.success"),
            TOO_MANY_BLOCKS("commands.sable.sub_level.assemble.connected.too_many_blocks"),
            NO_BLOCKS("commands.sable.sub_level.assemble.no_blocks");

            public final String errorKey;

            State(final String errorKey) {
                this.errorKey = errorKey;
            }
        }
    }
}
