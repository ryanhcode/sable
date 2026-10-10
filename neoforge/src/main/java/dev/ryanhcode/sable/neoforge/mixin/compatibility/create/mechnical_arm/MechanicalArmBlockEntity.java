package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.mechnical_arm;

import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin into {@link com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity} to force mechanical arms to load sub-level areas.
 */

@Mixin(ArmBlockEntity.class)
public abstract class MechanicalArmBlockEntity extends SmartBlockEntity {

    public MechanicalArmBlockEntity(final BlockEntityType<?> type, final BlockPos pos, final BlockState state) {
        super(type, pos, state);
    }

    @Inject(method = "isAreaActuallyLoaded", at = @At("HEAD"), cancellable = true)
    private void sable$forceMechArmsLoad(final BlockPos center, final int range, final CallbackInfoReturnable<Boolean> cir) {
        if (Sable.HELPER.getContaining(this.getLevel(), center) != null) {
            cir.setReturnValue(true);
        }
    }

}
