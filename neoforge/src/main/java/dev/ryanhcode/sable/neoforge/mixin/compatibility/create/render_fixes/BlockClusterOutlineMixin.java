package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.render_fixes;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.util.SublevelRenderOffsetHelper;
import net.createmod.catnip.outliner.BlockClusterOutline;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into {@link net.createmod.catnip.outliner.BlockClusterOutline} so block cluster outlines render correctly in sub-levels.
 */

@Mixin(BlockClusterOutline.class)
public class BlockClusterOutlineMixin {

    @Unique
    private Iterable<BlockPos> sable$collection = null;

    @Unique
    private Vec3 sable$center = Vec3.ZERO;

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void sable$gatherSublevel(final Iterable<BlockPos> selection, final CallbackInfo ci) {
        this.sable$collection = selection;
    }

    @Inject(method = "render", at = @At("HEAD"), remap = false)
    private void sable$projectFromSublevel(final PoseStack ms, final SuperRenderTypeBuffer buffer, final Vec3 camera, final float pt, final CallbackInfo ci) {
        ms.pushPose();
        for (final BlockPos pos : this.sable$collection) {
            final SubLevel sublevel = Sable.HELPER.getContainingClient(pos.getX() + 0.5, pos.getZ() + 0.5);
            if (sublevel != null) {
                this.sable$center = Vec3.atCenterOf(pos);
                SublevelRenderOffsetHelper.posePlotToProjected(sublevel, ms);
                break;
            }
        }
    }

    @ModifyVariable(method = "render", at = @At("HEAD"), remap = false, argsOnly = true)
    private Vec3 sable$modifyCamera(final Vec3 camera) {
        if (this.sable$center == null) {
            for (final BlockPos pos : this.sable$collection) {
                final SubLevel sublevel = Sable.HELPER.getContainingClient(pos.getX() + 0.5, pos.getZ() + 0.5);
                if (sublevel != null) {
                    this.sable$center = Vec3.atCenterOf(pos);
                    break;
                }
            }
        }
        return camera.add(SublevelRenderOffsetHelper.translation(this.sable$center));
    }

    @Inject(method = "render", at = @At("RETURN"), remap = false)
    private void sable$popPose(final PoseStack ms, final SuperRenderTypeBuffer buffer, final Vec3 camera, final float pt, final CallbackInfo ci) {
        ms.popPose();
        this.sable$center = Vec3.ZERO;
    }
}
