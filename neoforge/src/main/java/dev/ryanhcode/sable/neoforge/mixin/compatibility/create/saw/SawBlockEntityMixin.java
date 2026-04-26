package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.saw;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.kinetics.base.BlockBreakingKineticBlockEntity;
import com.simibubi.create.content.kinetics.saw.SawBlockEntity;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Fixes the velocity applied to items when dropped from a cut tree with a Create {@link SawBlockEntity}
 */
@Mixin(SawBlockEntity.class)
public abstract class SawBlockEntityMixin extends BlockBreakingKineticBlockEntity {

    public SawBlockEntityMixin(final BlockEntityType<?> type, final BlockPos pos, final BlockState state) {
        super(type, pos, state);
    }

    @WrapOperation(method = "dropItemFromCutTree", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;subtract(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/core/BlockPos;"))
    private BlockPos sable$traformPos(BlockPos instance, Vec3i vector, Operation<BlockPos> original) {
        final ActiveSableCompanion helper = Sable.HELPER;
        return BlockPos.containing(JOMLConversion.toMojang(helper.projectOutOfSubLevel(this.level, JOMLConversion.atCenterOf(this.breakingPos))
                .sub(helper.projectOutOfSubLevel(this.level, JOMLConversion.atCenterOf(this.worldPosition)))));
    }

    @WrapOperation(method = "dropItemFromCutTree", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;atLowerCornerOf(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 sable$itemDeltaMovement(final Vec3i vec3i, Operation<Vec3> original) {
        final ActiveSableCompanion helper = Sable.HELPER;

        final SubLevel subLevel = helper.getContaining(this);

        // we're spawning the items inside the sub-level, so we need to transform the velocity back into the sub-level
        // if we're in one
        if (subLevel != null) {
            return subLevel.logicalPose().transformNormalInverse(original.call(vec3i));
        }

        return original.call(vec3i);
    }
}
