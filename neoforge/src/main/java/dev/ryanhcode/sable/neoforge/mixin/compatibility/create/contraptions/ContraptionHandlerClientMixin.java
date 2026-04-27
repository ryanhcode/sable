package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.contraptions;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ContraptionHandlerClient;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Fixes the right click interaction and contraption raytracing in Create to take into account sublevels
 */
@Mixin(ContraptionHandlerClient.class)
public abstract class ContraptionHandlerClientMixin {

    @Redirect(method = "getRayInputs", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;distanceTo(Lnet/minecraft/world/phys/Vec3;)D"))
    private static double sable$projectDistanceTo1(final Vec3 eyePos, final Vec3 itemPos) {
        return Math.sqrt(Sable.HELPER.distanceSquaredWithSubLevels(Minecraft.getInstance().level, eyePos, itemPos));
    }

    @Redirect(method = "rightClickingOnContraptionsGetsHandledLocally", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;distanceTo(Lnet/minecraft/world/phys/Vec3;)D"))
    private static double sable$projectDistanceTo2(final Vec3 eyePos, final Vec3 itemPos) {
        return Math.sqrt(Sable.HELPER.distanceSquaredWithSubLevels(Minecraft.getInstance().level, eyePos, itemPos));
    }

    @WrapOperation(method = "rightClickingOnContraptionsGetsHandledLocally",
            at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;getBoundingBox()Lnet/minecraft/world/phys/AABB;"))
    private static AABB sable$moveBoundingBoxToProjectedPos(final AbstractContraptionEntity instance, Operation<AABB> original){
        final Vec3 projectedPos = Sable.HELPER.projectOutOfSubLevel(instance.level(), instance.getAnchorVec());
        final AABB boundingBox = original.call(instance);

        return boundingBox.move(Vec3.ZERO.subtract(boundingBox.getCenter())).move(projectedPos);
    }

    @WrapOperation(method = "rayTraceContraption",
                at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;toLocalVector(Lnet/minecraft/world/phys/Vec3;F)Lnet/minecraft/world/phys/Vec3;"),
                remap = false)
    private static Vec3 sable$projectedContraptionClip(final AbstractContraptionEntity abce, Vec3 localVec, final float partialTicks, Operation<Vec3> original){
        final ActiveSableCompanion helper = Sable.HELPER;
        final SubLevel sublevel1 = helper.getContaining(abce.level(), localVec);
        final SubLevel contraptionSublevel = helper.getContaining(abce);

        if (contraptionSublevel != sublevel1) {
            if (sublevel1 != null)
                localVec = sublevel1.logicalPose().transformPosition(localVec);

            if (contraptionSublevel != null)
                localVec = contraptionSublevel.logicalPose().transformPositionInverse(localVec);
        }

        return original.call(abce, localVec, 1f);
    }
}
