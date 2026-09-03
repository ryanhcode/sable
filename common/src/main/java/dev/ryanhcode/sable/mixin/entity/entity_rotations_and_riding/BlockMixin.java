package dev.ryanhcode.sable.mixin.entity.entity_rotations_and_riding;

import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Mixin into {@link net.minecraft.world.level.block.Block} to rotate the landing velocity damping along with the entity's orientation.
 */
@Mixin(Block.class)
public class BlockMixin {

    @Redirect(method = "updateEntityAfterFallOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;multiply(DDD)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 sable$rotateWithEntity(final Vec3 instance, final double x, final double y, final double z, @Local(argsOnly = true) final Entity entity) {
        final Quaterniondc orientation = EntitySubLevelUtil.getCustomEntityOrientation(entity, 1.0f);

        if (orientation == null) {
            return instance.multiply(x, y, z);
        }

        final Vector3d up = orientation.transform(OrientedBoundingBox3d.UP, new Vector3d());
        final double dot = up.dot(instance.x, instance.y, instance.z);
        return instance.subtract(up.x * dot, up.y * dot, up.z * dot);
    }

}
