package dev.ryanhcode.sable.api.physics.object.rope;

import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.api.physics.object.ArbitraryPhysicsObject;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import net.minecraft.world.level.ChunkPos;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.Collection;

/**
 * A rope made of points. Some may say.
 */
public class RopePhysicsObject implements ArbitraryPhysicsObject {
    protected final ObjectList<Vector3d> points;
    protected final ObjectList<Vector3d> pointsView;
    protected final double collisionRadius;
    protected boolean active;
    protected RopeHandle handle;

    protected Vector3dc startAttachmentLocation = null;
    protected ServerSubLevel startAttachmentSubLevel = null;

    public RopePhysicsObject(final Collection<Vector3d> points, final double collisionRadius) {
        this.points = new ObjectArrayList<>(points);
        this.pointsView = ObjectLists.unmodifiable(this.points);
        this.collisionRadius = collisionRadius;
        this.active = false;
    }

    /**
     * Gathers the bounding box that the arbitrary physics object requires to be loaded.
     * Chunk sections intersecting this bounding box will be synced to the physics engine.
     *
     * @param dest the destination the bounding box should be written into
     */
    @Override
    public void getBoundingBox(final BoundingBox3d dest) {
        final Vector3d first = this.points.getFirst();
        dest.set(first.x, first.y, first.z, first.x, first.y, first.z);
        for (final Vector3d point : this.points) {
            dest.expandTo(point.x - this.collisionRadius, point.y - this.collisionRadius, point.z - this.collisionRadius);
            dest.expandTo(point.x + this.collisionRadius, point.y + this.collisionRadius, point.z + this.collisionRadius);
        }
    }

    public double getCollisionRadius() {
        return this.collisionRadius;
    }
    /**
     * @return A view of ServerSubLevel points
     */
    public ServerSubLevel getStartAttachmentSubLevel() {
        return this.startAttachmentSubLevel;
    }
    /**
     * @return A view of all points
     */
    public ObjectList<Vector3d> getPoints() {
        return this.pointsView;
    }

    /**
     * Updates the points of the rope
     */
    public void updatePose() {
        this.handle.readPose(this.points);
    }

    /**
     * Sets the extension constraint length of the first segment
     */
    public void setFirstSegmentLength(final double length) {
        this.handle.setFirstSegmentLength(length);
    }

    /**
     * Removes the point at the beginning of the rope
     */
    public void removeFirstPoint() {
        this.points.removeFirst();

        if (this.isActive()) {
            this.handle.removeFirstPoint();
        }

        if (this.startAttachmentLocation != null) {
            this.setAttachment(RopeHandle.AttachmentPoint.START, this.startAttachmentLocation, this.startAttachmentSubLevel);
        }
    }

    /**
     * Adds a point to the beginning of the rope
     */
    public void addPoint(final Vector3dc position) {
        this.points.addFirst(new Vector3d(position));

        if (this.isActive()) {
            this.handle.addPoint(position);
        }

        if (this.startAttachmentLocation != null) {
            this.setAttachment(RopeHandle.AttachmentPoint.START, this.startAttachmentLocation, this.startAttachmentSubLevel);
        }
    }

    /**
     * Sets an attachment
     */
    public void setAttachment(final RopeHandle.AttachmentPoint attachmentPoint, final Vector3dc location, final ServerSubLevel subLevel) {
        if (attachmentPoint == RopeHandle.AttachmentPoint.START) {
            this.startAttachmentSubLevel = subLevel;
            this.startAttachmentLocation = new Vector3d(location);
        }

        if (this.isActive()) {
            this.handle.setAttachment(attachmentPoint, location, subLevel);
        }
    }

    /**
     * Called upon the physics object entering unloaded chnks
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
        this.handle = physicsSystem.getPipeline().addRope(this);
    }

    /**
     * Called to wake up the physics object when nearby blocks were modified
     */
    @Override
    public void wakeUp() {
        this.handle.wakeUp();
    }

    public boolean isActive() {
        return this.active;
    }
}
