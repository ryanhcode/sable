package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.render_fixes;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.util.SublevelRenderOffsetHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.createmod.catnip.ghostblock.GhostBlockRenderer$TransparentGhostBlockRenderer")
public abstract class GhostBlockValueBoxMixin {

    // Additional pose stack has already been pushed by here
    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V", ordinal = 0))
    public void sable$translate(final PoseStack ms, final double pX, final double pY, final double pZ, Operation<Void> original) {
        final Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();

        final Vec3 center = new Vec3(pX + camera.x, pY + camera.y, pZ + camera.z);
        SublevelRenderOffsetHelper.posePlotToProjected(Sable.HELPER.getContainingClient(center), ms);

        final Vec3 translation = SublevelRenderOffsetHelper.translation(center);

        original.call(ms, pX - translation.x, pY - translation.y, pZ - translation.z);
    }
}
