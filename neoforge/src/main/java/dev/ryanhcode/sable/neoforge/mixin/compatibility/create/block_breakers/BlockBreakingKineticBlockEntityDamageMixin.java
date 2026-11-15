package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.block_breakers;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.kinetics.drill.DrillBlock;
import com.simibubi.create.content.kinetics.saw.SawBlock;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.math.LevelReusedVectors;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.LevelExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Mixin into {@link com.simibubi.create.content.kinetics.saw.SawBlock} and {@link com.simibubi.create.content.kinetics.drill.DrillBlock} to compute damage checks against entities in sub-levels using oriented bounding boxes.
 */

@Mixin({SawBlock.class, DrillBlock.class})
public class BlockBreakingKineticBlockEntityDamageMixin {

	@WrapOperation(method = "entityInside", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/AABB;intersects(Lnet/minecraft/world/phys/AABB;)Z"))
	public boolean sable$fixBlockBreakerDamage(final AABB blockAABB, final AABB entityAABB, final Operation<Boolean> original, @Local(argsOnly = true) final Level level) {
		final Vector3d jomlCenterPos = JOMLConversion.toJOML(blockAABB.getCenter());
		final SubLevel parentSublevel = Sable.HELPER.getContaining(level, jomlCenterPos);

		if (parentSublevel != null) {
			final LevelReusedVectors jomlSink = ((LevelExtension) level).sable$getJOMLSink();

			final Vector3d entityCenter = JOMLConversion.toJOML(entityAABB.getCenter());
			final Vector3d sideLengths = new Vector3d(entityAABB.getXsize(), entityAABB.getYsize(), entityAABB.getZsize());

			final OrientedBoundingBox3d burnerBounds = new OrientedBoundingBox3d(parentSublevel.logicalPose().transformPosition(jomlCenterPos), new Vector3d(blockAABB.getXsize()), parentSublevel.logicalPose().orientation(), jomlSink);
			final OrientedBoundingBox3d entityBounds = new OrientedBoundingBox3d(entityCenter, sideLengths, JOMLConversion.QUAT_IDENTITY, jomlSink);

			// use the rotated player hit-box for consistency with sub-level collision
			jomlSink.entityBoxOrientation.identity();
			final double yaw = SubLevelEntityCollision.getHitBoxYaw(parentSublevel.logicalPose());
			jomlSink.entityBoxOrientation.rotateY(yaw);
			entityBounds.setOrientation(jomlSink.entityBoxOrientation);

			if (OrientedBoundingBox3d.sat(burnerBounds, entityBounds).lengthSquared() > 0.0) {
				return true;
			}
		}

		return original.call(blockAABB, entityAABB);
	}
}
