package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.schematics;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.schematics.client.SchematicTransformation;
import dev.engine_room.flywheel.lib.transform.PoseTransformStack;
import dev.engine_room.flywheel.lib.transform.Translate;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.util.SublevelRenderOffsetHelper;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Mixin into {@link com.simibubi.create.content.schematics.client.SchematicTransformation} to apply sub-level offsets when rendering schematic transformations.
 */

@Mixin(SchematicTransformation.class)
public abstract class SchematicTransformMixin {

    @Shadow private Vec3 prevChasingPos;

    @Shadow private Vec3 chasingPos;

    @Shadow public abstract BlockPos getAnchor();

    @WrapOperation(method = "applyTransformations", at = @At(value = "INVOKE", target = "Ldev/engine_room/flywheel/lib/transform/PoseTransformStack;translate(Lnet/minecraft/world/phys/Vec3;)Ldev/engine_room/flywheel/lib/transform/Translate;", ordinal = 0))
    public Translate<PoseTransformStack> sable$transformFromSublevel(final PoseTransformStack instance, final Vec3 vec3, final Operation<Translate<PoseTransformStack>> original, @Local(argsOnly = true) final Vec3 camera, @Local final float pt, @Local final PoseStack ms) {
        final Vec3 center = this.getAnchor().getCenter();

        SublevelRenderOffsetHelper.posePlotToProjected(Sable.HELPER.getContainingClient(center), ms);

        return instance.translate(VecHelper.lerp(pt, this.prevChasingPos, this.chasingPos).subtract(SublevelRenderOffsetHelper.translation(center)).subtract(camera));
    }
}
