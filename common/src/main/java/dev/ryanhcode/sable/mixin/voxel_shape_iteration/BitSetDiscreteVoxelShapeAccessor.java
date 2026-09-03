package dev.ryanhcode.sable.mixin.voxel_shape_iteration;

import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.BitSet;

/**
 * Accessor and invoker interface exposing the private storage field and strip-rectangle methods of {@link net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape}.
 */
@Mixin(BitSetDiscreteVoxelShape.class)
public interface BitSetDiscreteVoxelShapeAccessor extends DiscreteVoxelShapeAccessor {

    @Accessor
    BitSet getStorage();

    @Invoker
    boolean invokeIsZStripFull(int i, int j, int k, int l);

    @Invoker
    boolean invokeIsXZRectangleFull(int i, int j, int k, int l, int m);

    @Invoker
    void invokeClearZStrip(int i, int j, int k, int l);
}
