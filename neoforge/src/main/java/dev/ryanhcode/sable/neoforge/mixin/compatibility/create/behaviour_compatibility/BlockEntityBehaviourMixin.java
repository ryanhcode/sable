package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.behaviour_compatibility;

import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.edgeInteraction.EdgeInteractionBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.InvManipulationBehaviour;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin into {@link com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour} to find behaviours on block entities in sub-levels.
 */

@Mixin(BlockEntityBehaviour.class)
public abstract class BlockEntityBehaviourMixin {

    @Shadow
    public static <T extends BlockEntityBehaviour> T get(final BlockEntity be, final BehaviourType<T> type) {
        return null;
    }

    @Inject(method = "get(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lcom/simibubi/create/foundation/blockEntity/behaviour/BehaviourType;)Lcom/simibubi/create/foundation/blockEntity/behaviour/BlockEntityBehaviour;",
            at = @At(value = "HEAD"), remap = false, cancellable = true)
    private static <T extends BlockEntityBehaviour> void sable$accountForSubLevels(final BlockGetter reader, final BlockPos pos, final BehaviourType<T> type, final CallbackInfoReturnable<T> cir) {
        if (reader instanceof final Level level && BlockEntityBehaviourMixin.sable$checkType(type)) {
            final ActiveSableCompanion helper = Sable.HELPER;
            final BlockEntity caughtBE = helper.runIncludingSubLevels(level, pos.getCenter(), true, helper.getContaining(level, pos), (subLevel, internalPos) -> level.getBlockEntity(internalPos));

            if (caughtBE != null) {
                cir.setReturnValue(get(caughtBE, type));
            }
        }
    }

    @Unique
    private static boolean sable$checkType(final BehaviourType<?> type) {
        return type == BeltProcessingBehaviour.TYPE || type == DirectBeltInputBehaviour.TYPE ||
                type == TransportedItemStackHandlerBehaviour.TYPE || type == InvManipulationBehaviour.TYPE ||
                type == EdgeInteractionBehaviour.TYPE;
    }
}
