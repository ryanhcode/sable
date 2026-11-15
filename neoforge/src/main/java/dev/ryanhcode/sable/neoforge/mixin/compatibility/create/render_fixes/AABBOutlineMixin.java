package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.render_fixes;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.neoforge.mixinhelper.compatibility.create.renderers.AABBOutlineRenderingOptions;
import dev.ryanhcode.sable.util.SublevelRenderOffsetHelper;
import net.createmod.catnip.outliner.AABBOutline;
import net.createmod.catnip.outliner.Outline;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into {@link net.createmod.catnip.outliner.AABBOutline} to apply sub-level offsets when rendering outlines.
 */

@Mixin(value = AABBOutline.class, remap = false)
public abstract class AABBOutlineMixin extends Outline implements AABBOutlineRenderingOptions {

    @Unique
    private boolean sable$renderWithTransform;

    @Shadow
    protected AABB bb;

    @Shadow public abstract void render(PoseStack ms, SuperRenderTypeBuffer buffer, Vec3 camera, float pt);

    @Override
    public void sable$shouldTransform(final boolean newValue) {
        this.sable$renderWithTransform = newValue;
    }

    @Inject(method = "render", at = @At(value = "HEAD"), remap = false)
    private void sable$pushPose(final PoseStack ms, final SuperRenderTypeBuffer buffer, final Vec3 camera, final float pt, final CallbackInfo ci) {
        ms.pushPose();

        if (this.sable$renderWithTransform) {
            SublevelRenderOffsetHelper.posePlotToProjected(Sable.HELPER.getContainingClient(this.bb.getCenter()), ms);
        }
    }

    @ModifyArg(method = "render", at = @At(value = "INVOKE", target = "Lnet/createmod/catnip/outliner/AABBOutline;renderBox(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/createmod/catnip/render/SuperRenderTypeBuffer;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Lorg/joml/Vector4f;IZ)V"))
    private AABB sable$moveBB(final AABB box) {
        return box.move(SublevelRenderOffsetHelper.translation(box.getCenter()).scale(-1.0));
    }

    @Inject(method = "render", at = @At(value = "RETURN"), remap = false)
    private void sable$popPose(final PoseStack ms, final SuperRenderTypeBuffer buffer, final Vec3 camera, final float pt, final CallbackInfo ci) {
        ms.popPose();
    }
}
