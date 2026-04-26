package dev.ryanhcode.sable.mixin.camera.camera_rotation;

import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinhelpers.camera.camera_rotation.EntitySubLevelRotationHelper;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.LevelPoseProviderExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;

@Mixin(Entity.class)
public abstract class EntityMixin  {

    @Shadow private Level level;

    @Inject(method = "calculateViewVector", at = @At("RETURN"), cancellable = true)
    public void sable$calculateViewVector(final float f, final float g, final CallbackInfoReturnable<Vec3> cir) {
        final Function<SubLevel, Pose3dc> provider;

        if (this.level instanceof final LevelPoseProviderExtension levelPoseProvider) {
            provider = levelPoseProvider::sable$getPose;
        } else {
            provider = SubLevel::logicalPose;
        }

        final Quaterniond orientation = EntitySubLevelRotationHelper.getEntityOrientation(Entity.class.cast(this), provider, 0.0f, EntitySubLevelRotationHelper.Type.CAMERA);

        if (orientation != null) {
            final Vec3 viewVector = cir.getReturnValue();
            cir.setReturnValue(JOMLConversion.toMojang(orientation.transform(JOMLConversion.toJOML(viewVector))));
        }
    }

}
