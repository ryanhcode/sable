package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.depot;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.logistics.depot.DepotRenderer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(DepotRenderer.class)
public class DepotRendererMixin {
    @ModifyExpressionValue(method = "renderItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getPosition()Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 sable$renderViewEntityPosition(final Vec3 original, @Local(argsOnly = true) final Vec3 position) {
        final ClientSubLevel subLevel = Sable.HELPER.getContainingClient(position);
        if (subLevel != null) {
            return subLevel.renderPose().transformPositionInverse(original);
        } else {
            return original;
        }
    }
}
