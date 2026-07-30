package dev.ryanhcode.sable.mixin.camera.camera_rotation;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
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

import java.util.function.Function;

@Mixin(Entity.class)
public abstract class EntityMixin  {

    @Shadow private Level level;

    @ModifyReturnValue(method = "calculateViewVector", at = @At("RETURN"))
    public Vec3 sable$calculateViewVector(final Vec3 original) {
        final Function<SubLevel, Pose3dc> provider;

        if (this.level instanceof final LevelPoseProviderExtension levelPoseProvider) {
            provider = levelPoseProvider::sable$getPose;
        } else {
            provider = SubLevel::logicalPose;
        }

        final Quaterniond orientation = EntitySubLevelRotationHelper.getEntityOrientation((Entity) (Object) this, provider, 0.0f, EntitySubLevelRotationHelper.Type.CAMERA);

        if (orientation != null) {
            return JOMLConversion.toMojang(orientation.transform(JOMLConversion.toJOML(original)));
        }

        return original;
    }

}
