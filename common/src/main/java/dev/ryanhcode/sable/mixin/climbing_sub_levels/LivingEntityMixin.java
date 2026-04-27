package dev.ryanhcode.sable.mixin.climbing_sub_levels;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.platform.SablePlatform;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Allows living entities to climb ladders on sub-levels
 * <p>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity {

    public LivingEntityMixin(final EntityType<?> entityType, final Level level) {
        super(entityType, level);
    }

    @Redirect(method = "onClimbable", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;blockPosition()Lnet/minecraft/core/BlockPos;"))
    private BlockPos sable$redirectPos(final LivingEntity instance, @Share("subLevelBlockState") final LocalRef<BlockState> subLevelBlockState) {
        final Level level = this.level();
        final LivingEntity self = (LivingEntity) (Object) this;

        final BlockPos defaultPos = ((EntityMovementExtension) this).sable$getInBlockStatePos();
        final BlockState defaultState = this.getInBlockState();

        if (defaultState.is(BlockTags.CLIMBABLE) && SablePlatform.INSTANCE.isBlockstateLadder(defaultState, level, defaultPos, self)) {
            return defaultPos;
        }

        final Vector3d position = new Vector3d();
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (final SubLevel subLevel : Sable.HELPER.getAllIntersecting(level, new BoundingBox3d(this.getBoundingBox()))) {
            subLevel.logicalPose().transformPositionInverse(JOMLConversion.toJOML(this.position(), position));
            pos.set(position.x, position.y, position.z);
            final BlockState state = level.getBlockState(pos);

            if (state.is(BlockTags.CLIMBABLE) && SablePlatform.INSTANCE.isBlockstateLadder(state, level, pos, self)) {
                subLevelBlockState.set(state);
                return pos.immutable();
            }
        }

        return defaultPos;
    }


    @WrapOperation(method = "onClimbable", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getInBlockState()Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState getInBlockState(final LivingEntity instance, final Operation<BlockState> original, @Share("subLevelBlockState") final LocalRef<BlockState> subLevelBlockState) {
        final BlockState state = subLevelBlockState.get();
        return state != null ? state :  original.call(instance);
    }
}
