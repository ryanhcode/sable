package dev.ryanhcode.sable.physics.config;

import dev.ryanhcode.sable.SableConfig;
import dev.ryanhcode.sable.SableServerConfig;

public class PhysicsConfigData {
    /**
     * The number of solver iterations run by the constraints solver for calculating forces.
     */
    public int solverIterations = 18;

    public int pgsIterations = 2;
    public int stabilizationIterations = 2;

    /**
     * The damping ratio used by the springs for contact constraint stabilization.
     */
    public double contactSpringDampingRatio = 5.0;

    /**
     * The natural frequency used by the springs for contact constraint regularization.
     */
    public double contactSpringFrequency = 40.0;

    /**
     * Minimum number of dynamic bodies in each active island.
     */
    public int minDynamicBodiesPerIsland = 128;

    /**
     * Physics ticks done per game tick in the physics pipeline.
     */
    public int substepsPerTick = 2;

    public void updateFromConfig() {
        this.substepsPerTick = SableServerConfig.SUB_LEVEL_SUBSTEPS_PER_TICK.getAsInt();
    }
}
