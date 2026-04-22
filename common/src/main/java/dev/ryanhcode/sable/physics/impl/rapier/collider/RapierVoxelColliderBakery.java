package dev.ryanhcode.sable.physics.impl.rapier.collider;

import dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape;
import dev.ryanhcode.sable.api.block.BlockWithSubLevelCollisionCallback;
import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback;
import dev.ryanhcode.sable.api.physics.collider.SableCollisionContext;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.physics.chunk.VoxelNeighborhoodState;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import dev.ryanhcode.sable.physics.impl.rapier.Rapier3D;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.function.Function;

/**
 * A collider bakery that creates and caches collision shapes for blocks in Rapier
 *
 * @author RyanH
 */
public class RapierVoxelColliderBakery {
    private final @NotNull BlockGetter level;
    private final Function<BlockState, RapierVoxelColliderData> blockPhysicsDataBuilder = Util.memoize(this::buildPhysicsDataForBlock);

    /**
     * Creates a new level collider for the given level
     *
     * @param blockGetter the level to collide with
     */
    public RapierVoxelColliderBakery(@NotNull final BlockGetter blockGetter) {
        this.level = blockGetter;
    }

    /**
     * @return the level this collider is for
     */
    public @NotNull BlockGetter getLevel() {
        return this.level;
    }

    /**
     * Builds a box or compound collision shape
     *
     * @param childState the state to build the shape for
     * @return the physics data ID for the block at the given position, or null for empty
     */
    private @NotNull RapierVoxelColliderData buildPhysicsDataForBlock(final BlockState childState) {
        final int liquid = VoxelNeighborhoodState.getLiquidType(childState);

        final double friction = PhysicsBlockPropertyHelper.getFriction(childState);
        final double volume = PhysicsBlockPropertyHelper.getVolume(childState);
        final double restitution = PhysicsBlockPropertyHelper.getRestitution(childState);
        final BlockSubLevelCollisionCallback callback = BlockWithSubLevelCollisionCallback.sable$getCallback(childState);
        final RapierVoxelColliderData entry = Rapier3D.createVoxelColliderEntry(friction, volume, restitution, liquid, callback);
        

        if (liquid > 0) {
            entry.addBox(JOMLConversion.ZERO, new Vector3d(1.0, 1.0, 1.0));
            return entry;
        }

        final VoxelShape shape;

        if (childState.getBlock() instanceof final BlockSubLevelCollisionShape extension) {
            shape = extension.getSubLevelCollisionShape(this.level, childState);
        } else {
            shape = childState.getCollisionShape(this.level, BlockPos.ZERO, SableCollisionContext.get());
        }

        if (shape.isEmpty()) {
            return RapierVoxelColliderData.EMPTY;
        }

        shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
            // limit each block to the bounds of the unit cube,
            // so that fences / walls do not have unexpected behaviour
            entry.addBox(
                    new Vector3d(Math.max(minX, 0.0), Math.max(minY, 0.0), Math.max(minZ, 0.0)),
                    new Vector3d(Math.min(maxX, 1.0), Math.min(maxY, 1.0), Math.min(maxZ, 1.0))
            );
        });

        return entry;
    }

    /**
     * Builds / gets a saved box or compound collision shape for a block
     *
     * @param state the state to build the shape for
     * @return the physics data ID for the block at the given position, or null for empty
     */
    public @Nullable RapierVoxelColliderData getPhysicsDataForBlock(final BlockState state) {
        final RapierVoxelColliderData data = this.blockPhysicsDataBuilder.apply(state);
        return data == RapierVoxelColliderData.EMPTY ? null : data;
    }
}
