package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.tracks;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.trains.track.TrackTargetingClient;
import dev.engine_room.flywheel.lib.transform.PoseTransformStack;
import dev.engine_room.flywheel.lib.transform.Translate;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(TrackTargetingClient.class)
public class TrackTargetingClientMixin {

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Ldev/engine_room/flywheel/lib/transform/PoseTransformStack;translate(Lnet/minecraft/world/phys/Vec3;)Ldev/engine_room/flywheel/lib/transform/Translate;"))
    private static Translate sable$manipulateMatrixStack(final PoseTransformStack instance,
                                                         final Vec3 vec3,
                                                         Operation<Translate> original,
                                                         @Local(ordinal = 0) final Minecraft minecraft, @Local(ordinal = 0) final BlockPos pos,
                                                         @Local(argsOnly = true) final Vec3 camera) {
        final ClientLevel level = minecraft.level;
        final SubLevel subLevel = Sable.HELPER.getContaining(level, pos);

        if (subLevel instanceof final ClientSubLevel clientSubLevel) {
            final Pose3dc renderPose = clientSubLevel.renderPose();
            final Vec3 renderPos = renderPose.transformPosition(Vec3.atLowerCornerOf(pos));
            final Quaternionf renderOrientation = new Quaternionf(renderPose.orientation());
            return instance.translate(renderPos.x() - camera.x(), renderPos.y() - camera.y(), renderPos.z() - camera.z()).rotate(renderOrientation);
        }

        return original.call(instance, vec3);
    }

}
