package dev.ryanhcode.sable.mixin.voxel_shape_iteration;

import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.mixinhelpers.voxel_shape_iteration.FastVoxelShapeIterator;
import dev.ryanhcode.sable.mixinterface.voxel_shape_iteration.FastVoxelShapeIterable;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.Iterator;

@Mixin(VoxelShape.class)
public abstract class VoxelShapeMixin implements FastVoxelShapeIterable {

    @Unique
    private final Long2ObjectMap<FastVoxelShapeIterator> sable$boxIterator = new Long2ObjectArrayMap<>();
    @Shadow
    @Final
    protected DiscreteVoxelShape shape;

    @Shadow
    public abstract DoubleList getCoords(Direction.Axis axis);

    @Override
    public Iterator<BoundingBox3dc> sable$allBoxes() {
        synchronized (this) {
            
            if(sable$boxIterator == null) {
                return null;
                //This is null when this mixin is relevant to little tiles
            }
            
            final long id = Thread.currentThread().threadId();
            FastVoxelShapeIterator iterator = this.sable$boxIterator.get(id);

            if (iterator == null) {
                // Make sure the client and server thread don't try to create a new iterator at the same time
                iterator = this.sable$boxIterator.get(id);
                if (iterator == null) {
                    this.sable$boxIterator.put(id, iterator = new FastVoxelShapeIterator(
                            this.shape,
                            this.getCoords(Direction.Axis.X).toDoubleArray(),
                            this.getCoords(Direction.Axis.Y).toDoubleArray(),
                            this.getCoords(Direction.Axis.Z).toDoubleArray()));
                }
            }

            iterator.reset();
            return iterator;
        }
    }
}
