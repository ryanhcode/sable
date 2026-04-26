package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.contraptions;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.contraptions.actors.contraptionControls.ContraptionControlsRenderer;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.render.ContraptionMatrices;
import dev.ryanhcode.sable.Sable;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Fixes the rendering for the Contraption Controls to take sublevels into account
 */
@Mixin(value = ContraptionControlsRenderer.class, remap = false)
public class ContraptionControlsRendererMixin {
    @WrapOperation(method = "renderInContraption",
            at = @At(value = "FIELD", target = "Lcom/simibubi/create/content/contraptions/behaviour/MovementContext;position:Lnet/minecraft/world/phys/Vec3;", ordinal = 1))
    private static Vec3 sable$distanceRemix(final MovementContext instance, Operation<Vec3> original) {
        return Sable.HELPER.projectOutOfSubLevel(instance.world, original.call(instance));
    }
}