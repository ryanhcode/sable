package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.render_fixes;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import net.createmod.catnip.placement.PlacementClient;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PlacementClient.class)
public class PlacementClientMixin {

    @WrapOperation(method = "drawDirectionIndicator",
            at = @At(value = "INVOKE", target = "Lnet/createmod/catnip/math/VecHelper;getCenterOf(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/Vec3;"),
            remap = false)
    private static Vec3 sable$projectLastTargetedPos(final Vec3i pos, Operation<Vec3> original) {
        return JOMLConversion.toMojang(Sable.HELPER.projectOutOfSubLevel(Minecraft.getInstance().level, JOMLConversion.toJOML(original.call(pos))));
    }
}
