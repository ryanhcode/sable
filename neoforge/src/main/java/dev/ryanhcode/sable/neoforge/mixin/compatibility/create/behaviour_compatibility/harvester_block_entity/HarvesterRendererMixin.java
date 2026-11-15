package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.behaviour_compatibility.harvester_block_entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.contraptions.actors.harvester.HarvesterBlockEntity;
import com.simibubi.create.content.contraptions.actors.harvester.HarvesterRenderer;
import dev.ryanhcode.sable.neoforge.mixinhelper.compatibility.create.harvester.HarvesterLerpedSpeed;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Mixin into {@link com.simibubi.create.content.contraptions.actors.harvester.HarvesterRenderer} to drive harvester animations with smoothed sub-level speed.
 */

@Mixin(HarvesterRenderer.class)
public class HarvesterRendererMixin {

    @WrapOperation(method = "renderSafe(Lcom/simibubi/create/content/contraptions/actors/harvester/HarvesterBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/contraptions/actors/harvester/HarvesterRenderer;transform(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/Direction;Lnet/createmod/catnip/render/SuperByteBuffer;FLnet/minecraft/world/phys/Vec3;)V"))
    public void sable$smoothSpeed(final Level world, final Direction facing, final SuperByteBuffer superBuffer, final float speed, final Vec3 pivot, final Operation<Void> original, @Local final HarvesterBlockEntity be, @Local final float pt) {
        if (be.getAnimatedSpeed() != 0) {
            original.call(world, facing, superBuffer, speed, pivot);
        } else { // use our own transformation
            final float originOffset = 1.0f / 16.0f;
            final Vec3 rotOffset = new Vec3(0, pivot.y * originOffset, pivot.z * originOffset);

            superBuffer.rotateCentered(AngleHelper.rad(AngleHelper.horizontalAngle(facing)), Direction.UP)
                    .translate(rotOffset.x, rotOffset.y, rotOffset.z)
                    .rotate(AngleHelper.rad(-((HarvesterLerpedSpeed) be).sable$getLerpedFloat().getValue(pt)), Direction.WEST)
                    .translate(-rotOffset.x, -rotOffset.y, -rotOffset.z);
        }
    }
}
