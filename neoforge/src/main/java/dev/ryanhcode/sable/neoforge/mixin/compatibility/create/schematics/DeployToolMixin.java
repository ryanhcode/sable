package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.schematics;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.schematics.client.tools.DeployTool;
import com.simibubi.create.content.schematics.client.tools.SchematicToolBase;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.neoforge.mixinhelper.compatibility.create.renderers.AABBOutlineRenderingOptions;
import dev.ryanhcode.sable.util.SublevelRenderOffsetHelper;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Mixin into {@link com.simibubi.create.content.schematics.client.tools.DeployTool} so the deploy tool outline renders correctly in sub-levels.
 */

@Mixin(DeployTool.class)
public abstract class DeployToolMixin extends SchematicToolBase {

    @WrapOperation(method = "renderTool", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V"))
    public void sable$manualTransformBB(final PoseStack instance, final double x, final double y, final double z, final Operation<Void> original, @Local(ordinal = 0) final int centerX, @Local(ordinal = 1) final int centerZ, @Local(argsOnly = true) final Vec3 camera) {
        SublevelRenderOffsetHelper.posePlotToProjected(Sable.HELPER.getContainingClient(this.selectedPos), instance);
        final Vec3 trans = SublevelRenderOffsetHelper.translation(this.selectedPos.getCenter());

        original.call(instance, x - trans.x, y - trans.y, z - trans.z);
        ((AABBOutlineRenderingOptions) this.schematicHandler.getOutline()).sable$shouldTransform(false);
    }
}
