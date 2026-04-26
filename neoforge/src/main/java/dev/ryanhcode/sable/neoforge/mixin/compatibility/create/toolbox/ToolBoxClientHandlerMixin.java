package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.toolbox;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.equipment.toolbox.ToolboxHandlerClient;
import dev.ryanhcode.sable.Sable;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Fixes the range check in Create's {@link ToolboxHandlerClient} handling to account for sub-levels.
 */
@Mixin(ToolboxHandlerClient.class)
public class ToolBoxClientHandlerMixin {

    @WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/equipment/toolbox/ToolboxHandler;distance(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/core/BlockPos;)D"))
    private static double sable$sublevelDistance(final Vec3 location, final BlockPos p, Operation<Double> original) {
        return Sable.HELPER.distanceSquaredWithSubLevels(Minecraft.getInstance().level, location, p.getX() + 0.5, p.getY(), p.getZ() + 0.5, original);
    }

}
