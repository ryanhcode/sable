package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.elevator_controls;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.elevator.ElevatorControlsHandler;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Fixes the range check on Elevator Controls in Create to take into account sub-levels
 */
@Mixin(ElevatorControlsHandler.class)
public class ElevatorControlsHandlerMixin {

    @WrapOperation(method = "onScroll",
            at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;getBoundingBox()Lnet/minecraft/world/phys/AABB;"))
    private static AABB sable$projectAABB(final AbstractContraptionEntity instance, Operation<AABB> original) {
        final SubLevel subLevel = Sable.HELPER.getContaining(instance.level(), original.call(instance).getCenter());
        final AABB projectedBB = original.call(instance);

        if (subLevel != null) {
            final BoundingBox3d bb = new BoundingBox3d(projectedBB);
            return bb.transform(subLevel.logicalPose(), bb).toMojang();
        }

        return projectedBB;
    }
}
