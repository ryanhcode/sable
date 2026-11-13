package dev.ryanhcode.sable.mixin.voxel_shape_iteration;

import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor exposing the private x/y/z size fields of {@link net.minecraft.world.phys.shapes.DiscreteVoxelShape}.
 */
@Mixin(DiscreteVoxelShape.class)
public interface DiscreteVoxelShapeAccessor {

    @Accessor
    int getXSize();

    @Accessor
    int getYSize();

    @Accessor
    int getZSize();
}
