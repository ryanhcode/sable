package dev.ryanhcode.sable.api.physics.force;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.joml.Vector3dc;

import java.util.List;

/**
 * A grouping of applied point forces, alongside a force total to be applied.
 */
public class QueuedForceGroup {
    private final List<PointForce> appliedForces = new ObjectArrayList<>();
    private final ForceTotal forceTotal = new ForceTotal();
    private final ServerSubLevel subLevel;

    public QueuedForceGroup(final ServerSubLevel serverSubLevel) {
        this.subLevel = serverSubLevel;
    }

    public ForceTotal getForceTotal() {
        return this.forceTotal;
    }

    public void applyAndRecordPointForce(final Vector3dc point, final Vector3dc force) {
        this.forceTotal.applyImpulseAtPoint(this.subLevel.getMassTracker(), point, force);
        this.recordPointForce(point, force);
    }

    public void recordPointForce(final Vector3dc point, final Vector3dc force) {
        if (!this.subLevel.isTrackingIndividualQueuedForces()) {
            return;
        }

        if (force.lengthSquared() > 0.001 * 0.001) {
            this.appliedForces.add(new PointForce(point, force));
        }
    }

    public List<PointForce> getRecordedPointForces() {
        return this.appliedForces;
    }

    public void reset() {
        this.forceTotal.reset();
        this.appliedForces.clear();
    }

    public record PointForce(Vector3dc point, Vector3dc force) {
    }
}
