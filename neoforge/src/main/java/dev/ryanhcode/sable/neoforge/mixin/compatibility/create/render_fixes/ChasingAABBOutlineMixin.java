package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.render_fixes;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.util.SublevelRenderOffsetHelper;
import net.createmod.catnip.outliner.AABBOutline;
import net.createmod.catnip.outliner.ChasingAABBOutline;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into {@link net.createmod.catnip.outliner.ChasingAABBOutline} to apply sub-level offsets when rendering chasing outlines.
 */

@Mixin(value = ChasingAABBOutline.class, remap = false)
public abstract class ChasingAABBOutlineMixin extends AABBOutline {

    public ChasingAABBOutlineMixin(final AABB bb) {
        super(bb);
    }

    @Inject(method = "render", at = @At(value = "HEAD"), remap = false)
    private void sable$pushPose(final PoseStack ms, final SuperRenderTypeBuffer buffer, final Vec3 camera, final float pt, final CallbackInfo ci){
        ms.pushPose();
        SublevelRenderOffsetHelper.posePlotToProjected(Sable.HELPER.getContainingClient(this.bb.getCenter()), ms);
    }

    @ModifyArg(method = "render", at = @At(value = "INVOKE", target = "Lnet/createmod/catnip/outliner/ChasingAABBOutline;renderBox(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/createmod/catnip/render/SuperRenderTypeBuffer;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Lorg/joml/Vector4f;IZ)V"))
    private AABB sable$moveBB(final AABB box) {
        return box.move(SublevelRenderOffsetHelper.translation(box.getCenter()).scale(-1.0));
    }

    @Inject(method = "render", at = @At(value = "RETURN"), remap = false)
    private void sable$popPose(final PoseStack ms, final SuperRenderTypeBuffer buffer, final Vec3 camera, final float pt, final CallbackInfo ci){
        ms.popPose();
    }
}
