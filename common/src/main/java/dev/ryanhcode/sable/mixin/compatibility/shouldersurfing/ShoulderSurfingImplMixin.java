package dev.ryanhcode.sable.mixin.compatibility.shouldersurfing;

import com.github.exopandora.shouldersurfing.api.client.IClientConfig;
import com.github.exopandora.shouldersurfing.api.model.Perspective;
import com.github.exopandora.shouldersurfing.client.ShoulderSurfingImpl;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.mixinhelpers.compatibility.shouldersurfing.SablePerspectives;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ShoulderSurfingImpl.class)
public class ShoulderSurfingImplMixin {

    @WrapOperation(method = "togglePerspective", at = @At(value = "INVOKE", target = "Lcom/github/exopandora/shouldersurfing/api/model/Perspective;next(Lcom/github/exopandora/shouldersurfing/api/client/IClientConfig;)Lcom/github/exopandora/shouldersurfing/api/model/Perspective;"))
    public Perspective next(final Perspective instance, final IClientConfig config, final Operation<Perspective> original) {
        final Entity cameraEntity = Minecraft.getInstance().cameraEntity;

        Perspective next = original.call(instance, config);
        while (next == SablePerspectives.SUB_LEVEL_VIEW || next == SablePerspectives.SUB_LEVEL_VIEW_UNLOCKED) {
            if (cameraEntity != null && Sable.HELPER.getVehicleSubLevel(cameraEntity) != null) {
                break;
            }
            next = original.call(next, config);
        }
        return next;
    }
}
