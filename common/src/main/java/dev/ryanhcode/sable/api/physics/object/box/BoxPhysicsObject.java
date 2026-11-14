package dev.ryanhcode.sable.api.physics.object.box;

import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.api.physics.object.ArbitraryPhysicsObject;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Matrix3dc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * A box cuboid physics object. Some may say.
 */
public class BoxPhysicsObject implements ArbitraryPhysicsObject, PhysicsPipelineBody {

    protected BoxHandle handle;
    private final Pose3d pose = new Pose3d();
    private final Vector3d halfExtents = new Vector3d();
    private final double mass;
    private boolean active = false;

    /**
     * Constructs a box physics object
     * @param pose the pose where the rotation point and scale are ignored
     * @param halfExtents the half-extents of the box
     * @param mass the mass of the box
     */
    public BoxPhysicsObject(final Pose3dc pose, final Vector3dc halfExtents, final double mass) {
        this.pose.set(pose);
        this.halfExtents.set(halfExtents);
        this.mass = mass;
    }

    /**
     * Gathers the bounding box that the arbitrary physics object requires to be loaded.
     * Chunk sections intersecting this bounding box will be synced to the physics engine.
     *
     * @param dest the destination the bounding box should be written into
     */
    @Override
    public void getBoundingBox(final BoundingBox3d dest) {
        final double max = this.halfExtents.get(this.halfExtents.maxComponent());

        final Vector3d center = this.pose.position();
        dest.set(center.x, center.y, center.z, center.x, center.y, center.z);
        dest.expand(max * 1.7321);
    }

    /**
     * Updates the pose of the box
     */
    public void updatePose() {
        this.handle.readPose(this.pose);
    }

    /**
     * Called upon the physics object entering unloaded chunks
     */
    @Override
    public void onUnloaded(final SubLevelHoldingChunkMap holdingChunkMap, final ChunkPos chunkPos) {
        this.remove();
    }

    /**
     * Called upon the physics object being removed from the system through means other than unloading
     */
    @Override
    public void onRemoved() {
        this.remove();
    }

    protected void remove() {
        this.active = false;
        this.handle.remove();
        this.handle = null;
    }

    /**
     * Called upon the physics object being added to the world
     */
    @Override
    public void onAddition(final SubLevelPhysicsSystem physicsSystem) {
        this.active = true;
        this.handle = physicsSystem.getPipeline().addBox(this);
    }

    /**
     * Called to wake up the physics object when nearby blocks were modified
     */
    @Override
    public void wakeUp() {
        this.handle.wakeUp();
    }

    /**
     * @return the last updated pose
     */
    public Pose3dc getPose() {
        return this.pose;
    }

    /**
     * @return the half extents of the cube
     */
    public Vector3dc getHalfExtents() {
        return this.halfExtents;
    }

    /**
     * @return the mass of the cube
     */
    public double getMass() {
        return this.mass;
    }

    public boolean isActive() {
        return this.active;
    }

    @Override
    public int getRuntimeId() {
        if (this.handle == null) {
            return PhysicsPipelineBody.NULL_RUNTIME_ID;
        }
        return this.handle.getRuntimeId();
    }

    @Override
    public MassData getMassTracker() {
        return new BoxMassData();
    }

    @Override
    public Vector3dc getScale() {
        return JOMLConversion.ONE;
    }

    @Override
    public boolean isRemoved() {
        return !this.active;
    }

    /**
     * Mass data for a box physics object
     */
    private class BoxMassData implements MassData {
        private final Matrix3dc inertia = new Matrix3d().scale(BoxPhysicsObject.this.mass / 6.0);
        private final Matrix3dc inverseInertia = this.inertia.invert(new Matrix3d());

        @Override
        public double getMass() {
            return BoxPhysicsObject.this.mass;
        }

        @Override
        public double getInverseMass() {
            return 1.0 / BoxPhysicsObject.this.mass;
        }

        @Override
        public Matrix3dc getInertiaTensor() {
            return this.inertia;
        }

        @Override
        public Matrix3dc getInverseInertiaTensor() {
            return this.inverseInertia;
        }

        @Override
        public @Nullable Vector3dc getCenterOfMass() {
            return JOMLConversion.ZERO;
        }
    }
}
