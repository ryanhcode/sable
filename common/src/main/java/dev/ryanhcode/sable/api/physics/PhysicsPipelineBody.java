package dev.ryanhcode.sable.api.physics;

import dev.ryanhcode.sable.api.physics.mass.MassData;
import org.joml.Vector3dc;

/**
 * A rigid-body tracked by a {@link PhysicsPipeline}
 */
public interface PhysicsPipelineBody {

    int NULL_RUNTIME_ID = -1;

    /**
     * The runtime integer ID tracked by the {@link PhysicsPipeline}
     */
    int getRuntimeId();

    /**
     * @return the mass data for this physics body
     */
    MassData getMassTracker();

    /**
     * @return the scale, if any scale should be used to divide positions before applying their force
     */
    Vector3dc getScale();

    /**
     * @return if this body has been removed by the pipeline
     */
    boolean isRemoved();
}
