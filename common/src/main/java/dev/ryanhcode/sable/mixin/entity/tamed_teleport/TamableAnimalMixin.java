package dev.ryanhcode.sable.mixin.entity.tamed_teleport;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TamableAnimal.class)
public class TamableAnimalMixin {

	@Unique
	private static final BoundingBox3d sable$BOX = new BoundingBox3d();

	@WrapOperation(method = "maybeTeleportTo", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/TamableAnimal;canTeleportTo(Lnet/minecraft/core/BlockPos;)Z"))
	private static boolean sable$blockPosition(final TamableAnimal instance, final BlockPos blockPos, final Operation<Boolean> original) {
		final SubLevel subLevel = Sable.HELPER.getTrackingSubLevel(instance.getOwner());
		if(subLevel != null) {
			final BlockPos pos = BlockPos.containing(subLevel.logicalPose().transformPositionInverse(blockPos.getCenter()));
			if (original.call(instance, pos)) {
				final double dot = subLevel.logicalPose().transformNormal(new Vector3d(0, 1, 0)).dot(OrientedBoundingBox3d.UP);

				if (dot > 0.85) {
					return true;
				}
			}
		}

        sable$BOX.set(instance.getBoundingBox().move(blockPos.subtract(instance.blockPosition())));
        final Iterable<SubLevel> subLevels = Sable.HELPER.getAllIntersecting(instance.level(), sable$BOX);
        for (final SubLevel subLevel1 : subLevels) {
            final Vector3d center = sable$BOX.center();
            final BlockPos pos = BlockPos.containing(subLevel1.logicalPose().transformPositionInverse(new Vec3(center.x(), center.y(), center.z())));
            if (!instance.level().getBlockState(pos).isAir()) {
                return false;
            }
        }

		return original.call(instance, blockPos);
	}

}
