package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.behaviour_compatibility.block_breaking_behaviour;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.kinetics.saw.SawMovementBehaviour;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SawMovementBehaviour.class)
public class SawMovementBehaviourMixin {
    @Redirect(method = "dropItemFromCutTree", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;distanceTo(Lnet/minecraft/world/phys/Vec3;)D"))
    public double sable$fixSpeed(final Vec3 instance, final Vec3 vec3, @Local(argsOnly = true) final MovementContext context) {
        return Math.sqrt(Sable.HELPER.distanceSquaredWithSubLevels(context.world, instance, vec3));
    }

    @WrapOperation(method = "dropItemFromCutTree", at = @At(value = "FIELD", target = "Lcom/simibubi/create/content/contraptions/behaviour/MovementContext;relativeMotion:Lnet/minecraft/world/phys/Vec3;"))
    public Vec3 sable$fixRelativeMotion(final MovementContext instance, Operation<Vec3> original, @Local(argsOnly = true) final MovementContext context, @Local(ordinal = 0) final Vec3 dropPos) {
        final ActiveSableCompanion helper = Sable.HELPER;
        final SubLevel parentSublevel = helper.getContaining(context.world, context.contraption.anchor);
        final SubLevel targetSublevel = helper.getContaining(context.world, dropPos);
        Vec3 orignalMotion = original.call(context);

        if (parentSublevel != null) {
            orignalMotion = parentSublevel.logicalPose().transformNormal(orignalMotion);
        }

        if (targetSublevel != null) {
            orignalMotion = targetSublevel.logicalPose().transformNormalInverse(orignalMotion);
        }

        return orignalMotion;
    }
}
