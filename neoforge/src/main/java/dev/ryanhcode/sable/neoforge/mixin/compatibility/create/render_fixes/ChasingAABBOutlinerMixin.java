package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.render_fixes;

import net.createmod.catnip.outliner.ChasingAABBOutline;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin into {@link net.createmod.catnip.outliner.ChasingAABBOutline} to avoid interpolating bounding boxes across sub-levels.
 */

@Mixin(ChasingAABBOutline.class)
public class ChasingAABBOutlinerMixin {

    @Inject(method = "interpolateBBs", at = @At("HEAD"), remap = false, cancellable = true)
    private static void sable$bbDistanceCheck(final AABB current, final AABB target, final float pt, final CallbackInfoReturnable<AABB> cir) {
        if (current.getCenter().distanceTo(target.getCenter()) > 100)
            cir.setReturnValue(target);
    }
}
