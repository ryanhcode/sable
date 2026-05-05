package dev.ryanhcode.sable.physics.config.block_properties;

import dev.ryanhcode.sable.mixinterface.block_properties.BlockStateExtension;
import dev.ryanhcode.sable.physics.chunk.VoxelNeighborhoodState;
import dev.ryanhcode.sable.physics.config.FloatingBlockMaterialDataHandler;
import dev.ryanhcode.sable.physics.floating_block.FloatingBlockMaterial;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class PhysicsBlockPropertyHelper {

    /**
     * Gets the mass of a block in [kpg]
     *
     * @param level The level
     * @param pos   The position of the block
     * @param state The state of the block
     * @return The mass of the block
     */
    public static double getMass(final BlockGetter level, final BlockPos pos, final BlockState state) {
        final boolean solid = VoxelNeighborhoodState.isSolid(level, pos, state);

        if (!solid) {
            // TODO: Doing this means that sub-levels can end up with an existent bounding box but without any mass, invalidating them
            return 0.0;
        }

        return ((BlockStateExtension) state).sable$getProperty(PhysicsBlockPropertyTypes.MASS.get());
    }

    /**
     * Gets the inertia of a block, if specified
     *
     * @param level The level
     * @param pos   The position of the block
     * @param state The state of the block
     * @return The inertia of the block
     */
    @Nullable
    public static Vec3 getInertia(final BlockGetter level, final BlockPos pos, final BlockState state) {
        final boolean solid = VoxelNeighborhoodState.isSolid(level, pos, state);

        if (!solid) {
            return null;
        }

        return ((BlockStateExtension) state).sable$getProperty(PhysicsBlockPropertyTypes.INERTIA.get());
    }

    /**
     * Gets the friction multiplier of a block
     *
     * @param state The state of the block
     * @return The friction multiplier of the block
     */
    public static double getFriction(final BlockState state) {
        return ((BlockStateExtension) state).sable$getProperty(PhysicsBlockPropertyTypes.FRICTION.get());
    }

    /**
     * Gets the buoyancy volume multiplier of a block
     *
     * @param state The state of the block
     * @return The buoyancy volume of the block
     */
    public static double getVolume(final BlockState state) {
        return ((BlockStateExtension) state).sable$getProperty(PhysicsBlockPropertyTypes.VOLUME.get());
    }

    /**
     * Gets the restitution of a block
     *
     * @param state The state of the block
     * @return The restitution of the block
     */
    public static double getRestitution(final BlockState state) {
        return ((BlockStateExtension) state).sable$getProperty(PhysicsBlockPropertyTypes.RESTITUTION.get());
    }

    /**
     * Gets the scale of the floating of a block
     * @param state The state of the block
     * @return The floating multiplier of the block
     */
    public static double getFloatingScale(final BlockState state) {
        return ((BlockStateExtension) state).sable$getProperty(PhysicsBlockPropertyTypes.FLOATING_SCALE.get());
    }

    /**
     * Gets the floating material of a block
     * @param state The state of the block
     * @return The floating material of the block
     */
    public static FloatingBlockMaterial getFloatingMaterial(final BlockState state) {
        final ResourceLocation location = ((BlockStateExtension) state).sable$getProperty(PhysicsBlockPropertyTypes.FLOATING_MATERIAL.get());
        if (location == null)
            return null;
        return FloatingBlockMaterialDataHandler.allMaterials.get(location);
    }

    /**
     * Gets the buoyancy of a fluid block
     *
     * @param state The state of the block
     * @return The buoyancy of the fluid
     */
    public static double getFluidBuoyancy(final BlockState state) {
        if (state.getBlock() instanceof BubbleColumnBlock) {
            if (state.getValue(BubbleColumnBlock.DRAG_DOWN)) {
                return -1.0;
            } else {
                return 3.0;
            }
        }

        return ((BlockStateExtension) state).sable$getProperty(PhysicsBlockPropertyTypes.BUOYANCY.get());
    }

    /**
     * Gets the viscosity of a fluid block
     *
     * @param state The state of the block
     * @return The viscosity of the fluid
     */
    public static double getFluidViscosity(final BlockState state) {
        if (state.getBlock() instanceof BubbleColumnBlock) {
            return 1.0;
        }
        
        return ((BlockStateExtension) state).sable$getProperty(PhysicsBlockPropertyTypes.VISCOSITY.get());
    }

}
