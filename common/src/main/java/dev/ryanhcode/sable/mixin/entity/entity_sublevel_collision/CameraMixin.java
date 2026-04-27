package dev.ryanhcode.sable.mixin.entity.entity_sublevel_collision;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Camera.class)
public class CameraMixin {

    @Shadow private float eyeHeightOld;

    @Shadow private float eyeHeight;

    @Unique
    private final Vector3d sable$startPos = new Vector3d();

    @Unique
    private final Vector3d sable$endPos = new Vector3d();

    @WrapOperation(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setPosition(DDD)V"))
    private void sable$setPosition(final Camera instance,
                                   final double d,
                                   final double e,
                                   final double f,
                                   final Operation<Void> original,
                                   @Local(argsOnly = true) final Entity entity,
                                   @Local(argsOnly = true) final float partialTicks) {

        final SubLevel trackingSubLevel = Sable.HELPER.getTrackingOrVehicleSubLevel(entity);

        if (trackingSubLevel instanceof final ClientSubLevel clientSubLevel) {
            final double yOffset = Mth.lerp(partialTicks, this.eyeHeightOld, this.eyeHeight);

            this.sable$startPos.set(entity.xo, entity.yo + yOffset, entity.zo);
            this.sable$endPos.set(entity.getX(), entity.getY() + yOffset, entity.getZ());

            final Pose3dc renderPose = clientSubLevel.renderPose(partialTicks);
            clientSubLevel.lastPose().transformPositionInverse(this.sable$startPos);
            clientSubLevel.logicalPose().transformPositionInverse(this.sable$endPos);

            this.sable$startPos.lerp(this.sable$endPos, partialTicks);
            renderPose.transformPosition(this.sable$startPos);

            original.call(instance, this.sable$startPos.x, this.sable$startPos.y, this.sable$startPos.z);
            return;
        }

        original.call(instance, d, e, f);

    }

}
