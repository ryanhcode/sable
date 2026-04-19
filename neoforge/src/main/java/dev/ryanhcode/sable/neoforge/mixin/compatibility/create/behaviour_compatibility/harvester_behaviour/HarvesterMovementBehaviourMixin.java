package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.behaviour_compatibility.harvester_behaviour;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.content.contraptions.actors.harvester.HarvesterMovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(HarvesterMovementBehaviour.class)
public class HarvesterMovementBehaviourMixin {

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @WrapMethod(method = "visitNewPosition")
    public void sable$checkAllPositions(final MovementContext context, final BlockPos pos, final Operation<Void> original) {
        if (context != null) {
            final ActiveSableCompanion helper = Sable.HELPER;
            helper.runIncludingSubLevels(context.world, pos.getCenter(), true, helper.getContaining(context.contraption.entity), (sublevel, blockPos) -> {
                original.call(context, blockPos);
                return null;
            });
        } else {
            original.call(null, pos);
        }
    }
}
