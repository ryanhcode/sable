package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.frogports;

import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import org.joml.Quaterniondc;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Mixin into {@link com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer} so name plates render correctly in sub-levels.
 */

@Mixin(SmartBlockEntityRenderer.class)
public class SmartBlockEntityRendererMixin<T extends SmartBlockEntity> {

    @Redirect(method = "renderNameplateOnHover", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;cameraOrientation()Lorg/joml/Quaternionf;"))
    private Quaternionf sable$renderNameTag(final EntityRenderDispatcher instance, @Local(argsOnly = true) final T be) {
        final SubLevel subLevel = Sable.HELPER.getContaining(be);

        if (subLevel == null) {
            return instance.cameraOrientation();
        }

        final Quaterniondc subLevelOrientation = ((ClientSubLevel) subLevel).renderPose().orientation();
        return instance.cameraOrientation().premul(new Quaternionf(subLevelOrientation).conjugate());
    }

}
