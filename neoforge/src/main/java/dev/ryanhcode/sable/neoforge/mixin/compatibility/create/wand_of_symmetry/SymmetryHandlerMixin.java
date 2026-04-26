package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.wand_of_symmetry;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.equipment.symmetryWand.SymmetryHandler;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.util.SublevelRenderOffsetHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SymmetryHandler.class)
public class SymmetryHandlerMixin {
    @WrapOperation(method = "onRenderWorld", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V", ordinal = 0))
    private static void accountForSublevels(final PoseStack instance, final double f, final double g, final double h,
                                            Operation<Void> original,
                                            final @Local(name = "pos") BlockPos pos) {
        SublevelRenderOffsetHelper.posePlotToProjected(Sable.HELPER.getContainingClient(pos), instance);
        final Vec3 translation = SublevelRenderOffsetHelper.translation(Vec3.atLowerCornerOf(pos));

        original.call(instance, f - translation.x, g - translation.y, h - translation.z);
    }
}
