package dev.ryanhcode.sable.mixin.block_placement;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/**
 * Disallows placing blocks on sub-levels inside of entities
 */
@Mixin(EntityGetter.class)
public interface EntityGetterMixin {

    @Shadow
    List<Entity> getEntities(@org.jetbrains.annotations.Nullable Entity pEntity, AABB pArea);

    @Shadow
    List<? extends Player> players();

    @WrapOperation(method = "isUnobstructed", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/shapes/Shapes;joinIsNotEmpty(Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/BooleanOp;)Z"))
    default boolean joinIsNotEmptyIncludeSubLevel(final VoxelShape voxelShape, final VoxelShape EntityShape,
                                                  final BooleanOp op, Operation<Boolean> original,
                                                  @Local(ordinal = 1) final Entity entity) {
        final AABB entityBounds = entity.getBoundingBox();
        boolean fine = original.call(voxelShape, EntityShape, op);

        final BoundingBox3d queryBounds = new BoundingBox3d(entityBounds);
        queryBounds.expand(1.5, queryBounds);
        final Iterable<SubLevel> intersecting = Sable.HELPER.getAllIntersecting(entity.level(), queryBounds);

        for (final SubLevel subLevel : intersecting) {
            if (fine) break;

            final BoundingBox3d bb = new BoundingBox3d(entityBounds);
            bb.transformInverse(subLevel.logicalPose(), bb);
            bb.expand(-0.75 / 16.0, bb);
            if (Shapes.joinIsNotEmpty(voxelShape, Shapes.create(bb.toMojang()), BooleanOp.AND))
                fine = true;
        }

        return fine;
    }
}
