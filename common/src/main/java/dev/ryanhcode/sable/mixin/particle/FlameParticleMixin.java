package dev.ryanhcode.sable.mixin.particle;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinterface.particle.ParticleExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.FlameParticle;
import net.minecraft.client.particle.Particle;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Mixin into {@link net.minecraft.client.particle.FlameParticle} to make flame particles move along with their tracked sub-level.
 */
@Mixin(FlameParticle.class)
public abstract class FlameParticleMixin extends Particle implements ParticleExtension {

    protected FlameParticleMixin(final ClientLevel clientLevel, final double d, final double e, final double f) {
        super(clientLevel, d, e, f);
    }

    @WrapMethod(method = "move")
    public void move(final double motionX, final double motionY, final double motionZ, final Operation<Void> original) {
        final SubLevel trackingSubLevel = this.sable$getTrackingSubLevel();
        if (trackingSubLevel == null || trackingSubLevel.isRemoved()) {
            original.call(motionX, motionY, motionZ);
            return;
        }

        final Pose3dc pose = trackingSubLevel.logicalPose();
        final Pose3dc last = trackingSubLevel.lastPose();

        // Move with our current tracking sub-level
        final Vector3dc globalBoundsCenter = JOMLConversion.getAABBCenter(this.getBoundingBox());
        final Vector3d localPosition = last.transformPositionInverse(globalBoundsCenter, new Vector3d());
        final Vector3d newGlobalPosition = pose.transformPosition(localPosition);

        original.call(motionX + newGlobalPosition.x - globalBoundsCenter.x(),
                motionY + newGlobalPosition.y - globalBoundsCenter.y(),
                motionZ + newGlobalPosition.z - globalBoundsCenter.z());
    }
}
