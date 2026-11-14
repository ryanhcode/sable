package dev.ryanhcode.sable.api.particle;

import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import org.joml.Vector3dc;

/**
 *  An interface for sub-classes of {@link net.minecraft.client.particle.Particle} to implement that indicates whether
 *  or not the particle should ever be kicked from the tracking sub-level
 */
public interface ParticleSubLevelKickable {
    default boolean sable$shouldCareAboutIntersectingSubLevels() {
        return true;
    }

    boolean sable$shouldKickFromTracking();

    boolean sable$shouldCollideWithTrackingSubLevel();

    default Vector3dc sable$getUpDirection() {
        return OrientedBoundingBox3d.UNIT_Y;
    }
}
