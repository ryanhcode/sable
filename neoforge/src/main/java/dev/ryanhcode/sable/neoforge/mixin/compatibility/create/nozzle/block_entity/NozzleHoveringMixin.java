package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.nozzle.block_entity;

import com.simibubi.create.content.kinetics.fan.NozzleBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.neoforge.mixinhelper.compatibility.create.nozzles.NozzleHoveringHelper;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Mixin into {@link com.simibubi.create.content.kinetics.fan.NozzleBlockEntity} to provide wind lift as a sub-level actor.
 */

@Mixin(NozzleBlockEntity.class)
public abstract class NozzleHoveringMixin extends SmartBlockEntity implements BlockEntitySubLevelActor {

	@Shadow private boolean pushing;
	@Shadow private float range;
	@Unique
	private List<Couple<Vec3>> sable$rayPoints = null;

	public NozzleHoveringMixin(final BlockEntityType<?> type, final BlockPos pos, final BlockState state) {
		super(type, pos, state);
	}

	@Inject(method = "<init>",  at = @At("TAIL"))
	public void sable$generateRays(final BlockEntityType type, final BlockPos pos, final BlockState state, final CallbackInfo ci) {
        this.sable$rayPoints = NozzleHoveringHelper.gatherRaycastPoints(state);
	}

	@Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;nextInt(I)I"))
	private void addPhysicsParticles(final CallbackInfo ci) {
        final ActiveSableCompanion helper = Sable.HELPER;
        if (helper.getContaining(this) != null && this.pushing) {
			final Vec3 blockCorner = Vec3.atLowerCornerOf(this.getBlockPos());
			final Couple<Vec3> ray = this.sable$rayPoints.get(this.level.random.nextInt(this.sable$rayPoints.size()));
			final Vec3 start = ray.getFirst().add(blockCorner);
			final Vec3 end = ray.getSecond().add(blockCorner);
			final ClipContext context = new ClipContext(
					start,
					end,
					ClipContext.Block.OUTLINE,
					ClipContext.Fluid.ANY,
					CollisionContext.empty()
			);
			final BlockHitResult clip = this.level.clip(context);
			NozzleHoveringHelper.spawnWindHitParticle(
					this.level, helper.getContaining(this), clip,
					JOMLConversion.toJOML(start), this.range / 40
			);
		}
	}

	@Override
	public void sable$physicsTick(final ServerSubLevel subLevel, final RigidBodyHandle handle, final double timeStep) {
		final Vector3d force = NozzleHoveringHelper.gatherForceFromRays(subLevel, timeStep, this.getLevel(), this.getBlockPos(), (NozzleBlockEntity) (Object) this, this.sable$rayPoints);

		if (force != null) {
			final QueuedForceGroup forceGroup = subLevel.getOrCreateQueuedForceGroup(ForceGroups.PROPULSION.get());
			forceGroup.applyAndRecordPointForce(JOMLConversion.toJOML(Vec3.atCenterOf(this.getBlockPos())), force);
		}
	}
}