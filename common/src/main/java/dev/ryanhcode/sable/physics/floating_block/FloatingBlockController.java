package dev.ryanhcode.sable.physics.floating_block;

import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import dev.ryanhcode.sable.api.sublevel.KinematicContraption;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.util.SableMathUtils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.*;

import java.lang.Math;
import java.util.ArrayList;
import java.util.List;

/**
 * Clusters & manages forces caused by floating block materials
 */
public class FloatingBlockController {
    private static final Vector3d frictionForce = new Vector3d();
    private static final Vector3d frictionTorque = new Vector3d();
    private static final Vector3d clusterFrictionForce = new Vector3d();
    private static final Vector3d clusterFrictionTorque = new Vector3d();
    private static final Vector3d localGravity = new Vector3d();
    private static final Vector3d localLinearVelocity = new Vector3d();
    private static final Vector3d localAngularVelocity = new Vector3d();
    private final FloatingClusterContainer sublevelContainer = new FloatingClusterContainer();
    private final List<FloatingClusterContainer> containers = new ArrayList<>();
    private final ServerSubLevel subLevel;
    private final Vector3d previousCenterOfMass = new Vector3d();

    public FloatingBlockController(final ServerSubLevel subLevel) {
        this.subLevel = subLevel;
    }

    public void physicsTick(final double partialPhysicsTick, final double timeStep, final Vector3dc linearVelocity, final Vector3dc angularVelocity, final Vector3d linearImpulse, final Vector3d angularImpulse) {
        this.containers.clear();
        this.containers.add(this.sublevelContainer);

        for (final KinematicContraption contraption : this.subLevel.getPlot().getContraptions()) {
            final FloatingClusterContainer container = contraption.sable$getFloatingClusterContainer();

            final Vector3dc lastPosition = new Vector3d(contraption.sable$getPosition(partialPhysicsTick - 1.0f));
            final Quaterniondc lastOrientation = new Quaterniond(contraption.sable$getOrientation(partialPhysicsTick - 1.0f));

            container.positionOffset.set(contraption.sable$getPosition(partialPhysicsTick));
            container.rotationOffset.set(contraption.sable$getOrientation(partialPhysicsTick));

            container.positionOffset.sub(lastPosition, container.velocity);
            SableMathUtils.getAngularVelocity(lastOrientation, container.rotationOffset, container.angularVelocity);

            container.velocity.mul(20.0);
            container.angularVelocity.mul(20.0);

            container.positionOffset.sub(this.subLevel.getMassTracker().getCenterOfMass());

            this.containers.add(container);
        }


        this.processBlockChanges();

        localGravity.set(DimensionPhysicsData.getGravity(this.subLevel.getLevel(), this.subLevel.logicalPose().position()));
        this.subLevel.logicalPose().orientation().transformInverse(localGravity);
        if (!this.needsTicking())
            return;

        this.subLevel.logicalPose().orientation().transformInverse(linearVelocity, localLinearVelocity);
        this.subLevel.logicalPose().orientation().transformInverse(angularVelocity, localAngularVelocity);

        frictionForce.zero();
        frictionTorque.zero();

        final QueuedForceGroup dragGroup = this.subLevel.getOrCreateQueuedForceGroup(ForceGroups.DRAG.get());
        final List<Vector3d> recordedFrictionForces = new ObjectArrayList<>();

        for (final FloatingClusterContainer container : this.containers) {

            for (final FloatingBlockCluster cluster : container.clusters) {
                if (cluster.getMaterial().scaleWithPressure())
                    cluster.getBlockData().computePressureScale(this.subLevel);

                this.applyFriction(container, cluster, localGravity, localLinearVelocity, localAngularVelocity, clusterFrictionForce, clusterFrictionTorque);

                final Vector3d recordedClusterFrictionForce = new Vector3d(clusterFrictionForce);
                this.recordForce(container, cluster, dragGroup, recordedClusterFrictionForce);
                recordedFrictionForces.add(recordedClusterFrictionForce);

                frictionForce.add(clusterFrictionForce);
                frictionTorque.add(clusterFrictionTorque);
            }
        }
        /*expectedVelocity.set(frictionForce);
        expectedVelocity.mul(1 / this.subLevel.getMassTracker().getMass());
        expectedVelocity.mul(timeStep);
        final double forceScale = this.getClampingFactor(localLinearVelocity, expectedVelocity);

        expectedVelocity.set(frictionTorque);
        this.subLevel.getMassTracker().getInverseInertiaTensor().transform(expectedVelocity);
        expectedVelocity.mul(timeStep);
        final double torqueScale = this.getClampingFactor(localAngularVelocity, expectedVelocity);

        frictionForce.mul(forceScale);
        frictionTorque.mul(torqueScale);*/

        //final double forceScale = this.getKineticClampingFactor(localLinearVelocity,localAngularVelocity,frictionForce,frictionTorque,timeStep);
        //frictionForce.mul(forceScale);
        //frictionTorque.mul(forceScale);

        // scale recorded forces
        for (final Vector3d force : recordedFrictionForces) {
            force.mul(timeStep);//forceScale *
        }
        if (localGravity.lengthSquared() > 0)
            this.applyLift(localGravity, linearImpulse, angularImpulse, timeStep);

        linearImpulse.fma(timeStep, frictionForce);
        angularImpulse.fma(timeStep, frictionTorque);
    }

    public boolean needsTicking() {
        if (this.sublevelContainer.needsTicking())
            return true;
        for (final FloatingClusterContainer container : this.containers) {
            if (container.needsTicking())
                return true;
        }
        return false;
    }

    private void processBlockChanges() {

        this.previousCenterOfMass.sub(this.subLevel.getMassTracker().getCenterOfMass());
        for (final FloatingBlockCluster cluster : this.sublevelContainer.clusters) {
            cluster.getBlockData().translateOrigin(this.previousCenterOfMass);
        }
        this.sublevelContainer.processBlockChanges(this.subLevel.getMassTracker().getCenterOfMass());
        this.previousCenterOfMass.set(this.subLevel.getMassTracker().getCenterOfMass());

    }
    //note on units:
    // strength unit is the material strength
    // weight unit is total material amount, scaled by individual block values
    // strength and weight together is total mass lifting capacity for this cluster

    private static final Vector3d totalWeightedForce = new Vector3d();//strength * weight * position
    private static final Vector3d averageForcePos = new Vector3d();//position
    private static final Vector3d liftingForce = new Vector3d();//strength * weight * gravity
    private static final Vector3d liftingTorque = new Vector3d();//strength * weight * gravity * position
    private static final Vector3d torqueTemp = new Vector3d();
    private static final Vector3d weightedPositionTemp = new Vector3d();//weight * position
    private static final Vector3d totalAcceleration = new Vector3d();

    private void applyLift(final Vector3d localGravity, final Vector3d linearImpulse, final Vector3d angularImpulse, final double timeStep) {
        double totalForce = 0;//strength * weight

        totalWeightedForce.set(0);

        for (final FloatingClusterContainer container : this.containers) {

            for (final FloatingBlockCluster cluster : container.clusters) {
                final FloatingBlockMaterial material = cluster.getMaterial();

                if (material.liftStrength() == 0) continue;

                //unit: strength
                double clusterForce = material.liftStrength();
                if (material.scaleWithPressure())
                    clusterForce *= cluster.getBlockData().getPressureScale();

                //unit: strength * weight
                final double weightedForce = clusterForce * cluster.getBlockData().totalScale;

                this.getTrueWeightedClusterPosition(container, cluster, weightedPositionTemp);

                if (material.preventSelfLift()) {
                    totalForce += weightedForce;
                    totalWeightedForce.fma(clusterForce, weightedPositionTemp);
                } else {

                    linearImpulse.fma(-weightedForce * timeStep, localGravity);

                    if (this.subLevel.isTrackingIndividualQueuedForces()) {
                        final QueuedForceGroup levitationGroup = this.subLevel.getOrCreateQueuedForceGroup(ForceGroups.LEVITATION.get());

                        this.recordForce(container, cluster, levitationGroup, new Vector3d(localGravity).mul(-weightedForce * timeStep));
                    }

                    localGravity.cross(weightedPositionTemp, torqueTemp);//torqueTemp unit: weight * position * gravity
                    angularImpulse.fma(clusterForce * timeStep, torqueTemp);
                }

            }
        }
        if (totalForce <= 0)
            return;

        totalWeightedForce.div(totalForce, averageForcePos);

        liftingForce.set(localGravity).mul(-totalForce);
        averageForcePos.cross(liftingForce, liftingTorque);

        this.subLevel.getMassTracker().getInverseInertiaTensor().transform(liftingTorque, torqueTemp).cross(averageForcePos, totalAcceleration);
        totalAcceleration.fma(1 / this.subLevel.getMassTracker().getMass(), liftingForce);

        double scaleFactor = -localGravity.lengthSquared() / localGravity.dot(totalAcceleration);

        if (scaleFactor > 1) {
            scaleFactor = 1;
        }

        liftingForce.mul(scaleFactor);
        liftingTorque.mul(scaleFactor);

        if (this.subLevel.isTrackingIndividualQueuedForces()) {
            final QueuedForceGroup levitationGroup = this.subLevel.getOrCreateQueuedForceGroup(ForceGroups.LEVITATION.get());

            for (final FloatingClusterContainer container : this.containers) {

                for (final FloatingBlockCluster cluster : container.clusters) {
                    final FloatingBlockMaterial material = cluster.getMaterial();

                    final Vector3d force = new Vector3d(localGravity).mul(timeStep * -cluster.getBlockData().totalScale * material.liftStrength());
                    force.mul(scaleFactor);

                    this.recordForce(container, cluster, levitationGroup, force);
                }
            }
        }

        //this.liftForceCombinator.apply(scaleFactor);

        linearImpulse.fma(timeStep, liftingForce);
        angularImpulse.fma(timeStep, liftingTorque);
    }

    private void recordForce(final FloatingClusterContainer container, final FloatingBlockCluster cluster, final QueuedForceGroup forceGroup, final Vector3d force) {
        forceGroup.recordPointForce(this.getTrueWeightedClusterPosition(container, cluster, new Vector3d()).div(cluster.getBlockData().totalScale).add(this.subLevel.getMassTracker().getCenterOfMass()), force);
    }

    private Vector3d getTrueWeightedClusterPosition(final FloatingClusterContainer container, final FloatingBlockCluster cluster, final Vector3d pos) {
        container.rotationOffset.transform(cluster.getBlockData().weightedPosition, pos);
        return pos.fma(cluster.getBlockData().totalScale, container.positionOffset);
    }

    private static final Matrix3d containerRotation = new Matrix3d();
    private static final Vector3d clusterCenter = new Vector3d();
    private static final Vector3d totalAngularVelocity = new Vector3d();
    private static final Vector3d rotatedPos = new Vector3d();
    private static final Matrix3d slowDragMatrix = new Matrix3d();
    private static final Matrix3d fastDragMatrix = new Matrix3d();
    private static final Matrix3d averagePositionMatrix = new Matrix3d();
    private static final Matrix3d averagePositionMatrixInverse = new Matrix3d();
    private static final Matrix3d shiftedPositionMatrix = new Matrix3d();
    private static final Matrix3d shiftedPositionMatrixInverse = new Matrix3d();
    private static final Matrix3d tempTorqueMatrix = new Matrix3d();
    private static final Vector3d meanVelocity = new Vector3d();
    private static final Vector3d shiftedCenter = new Vector3d();
    private static final Vector3d linearSlowDrag = new Vector3d();

    private void applyFriction(final FloatingClusterContainer container, final FloatingBlockCluster cluster, final Vector3dc localGravity, final Vector3dc linearVelocity, final Vector3dc angularVelocity, final Vector3d frictionForce, final Vector3d frictionTorque) {

        double frictionScale = 1;
        if (cluster.getMaterial().scaleWithGravity())
            frictionScale = localGravity.length();
        if (cluster.getMaterial().scaleWithPressure())
            frictionScale *= cluster.getBlockData().getPressureScale();

        double speedScale = 3 / (cluster.getMaterial().transitionSpeed() * cluster.getMaterial().transitionSpeed());
        if (cluster.getMaterial().transitionSpeed() == 0)
            speedScale = 0;

        totalAngularVelocity.set(angularVelocity).add(container.angularVelocity);
        this.getTrueWeightedClusterPosition(container, cluster, clusterCenter).div(cluster.getBlockData().totalScale);

        cluster.getBlockData().outerProduct.scale(1 / cluster.getBlockData().totalScale, averagePositionMatrix);

        //rotate averagePositionMatrix by container.rotationOffset
        container.rotationOffset.get(containerRotation);
        averagePositionMatrix.mulLocal(containerRotation);
        averagePositionMatrix.mul(containerRotation.transpose());

        //set up matrices
        averagePositionMatrix.invert(averagePositionMatrixInverse);
        shiftedPositionMatrixInverse.set(averagePositionMatrixInverse);
        SableMathUtils.fmaInertiaTensor(totalAngularVelocity, speedScale, shiftedPositionMatrixInverse);
        shiftedPositionMatrixInverse.invert(shiftedPositionMatrix);

        //velocity of the center of lift in local space
        angularVelocity.cross(clusterCenter, meanVelocity);
        container.rotationOffset.transform(cluster.getBlockData().weightedPosition, rotatedPos).div(cluster.getBlockData().totalScale);
        final Vector3d extraContainerVelocity = container.angularVelocity.cross(rotatedPos, rotatedPos);
        meanVelocity.add(linearVelocity).add(container.velocity).add(extraContainerVelocity);

        //center of the shifted position distribution relative to clusterCenter, variance is shiftedPositionMatrix
        totalAngularVelocity.cross(meanVelocity, shiftedCenter).mul(speedScale);
        shiftedPositionMatrix.transform(shiftedCenter);

        double slowDragScale = Math.sqrt(shiftedPositionMatrix.determinant() / averagePositionMatrix.determinant());
        slowDragScale *= Math.exp(-0.5 * (speedScale * meanVelocity.dot(meanVelocity) - SableMathUtils.multiplyInnerProduct(shiftedCenter, shiftedPositionMatrixInverse, shiftedCenter)));
        if (cluster.getMaterial().transitionSpeed() == 0)
            slowDragScale = 0;

        //the drag matrices are for scaling forces in their horizontal and vertical components
        this.getGravityMatrix(localGravity, cluster.getMaterial().slowVerticalFriction(), cluster.getMaterial().slowHorizontalFriction(), slowDragMatrix)
                .scale(cluster.getBlockData().totalScale * frictionScale * slowDragScale);

        this.getGravityMatrix(localGravity, cluster.getMaterial().fastVerticalFriction(), cluster.getMaterial().fastHorizontalFriction(), fastDragMatrix)
                .scale(cluster.getBlockData().totalScale * frictionScale);

        slowDragMatrix.transform(totalAngularVelocity.cross(shiftedCenter, linearSlowDrag).add(meanVelocity));
        fastDragMatrix.transform(meanVelocity, frictionForce).add(linearSlowDrag);

        clusterCenter.cross(frictionForce, frictionTorque);
        final Vector3d torqueTemp = shiftedCenter.cross(linearSlowDrag, linearSlowDrag);
        frictionTorque.add(torqueTemp);
        tempTorqueMatrix.zero();
        this.matrixThingy(averagePositionMatrix, fastDragMatrix, tempTorqueMatrix);
        this.matrixThingy(shiftedPositionMatrix, slowDragMatrix, tempTorqueMatrix);
        tempTorqueMatrix.transform(totalAngularVelocity, torqueTemp);
        frictionTorque.add(torqueTemp);
    }

    private static final Matrix3d X2 = new Matrix3d();
    private static final Matrix3d Y2 = new Matrix3d();
    private static final Matrix3d YX = new Matrix3d();
    private static final Matrix3d traceMatrix = new Matrix3d();

    //idk what to name this, sorry
    private void matrixThingy(final Matrix3dc X, final Matrix3dc Y, final Matrix3d out) {
        Y.mul(X, YX);
        final double traceX = X.m00() + X.m11() + X.m22();
        final double traceY = Y.m00() + Y.m11() + Y.m22();
        final double traceYX = YX.m00() + YX.m11() + YX.m22();
        traceMatrix.identity().scale(traceX).sub(X, X2);
        traceMatrix.identity().scale(traceY).sub(Y, Y2);
        traceMatrix.identity().scale(traceYX).sub(YX, YX);
        X2.mul(Y2);
        out.add(X2).sub(YX);
    }

    private Matrix3d getGravityMatrix(final Vector3dc g, final double verticalDrag, final double horizontalDrag, final Matrix3d target) {
        if (g.lengthSquared() > 0.00001)
            SableMathUtils.setOuterProduct(g, g, (horizontalDrag - verticalDrag) / g.dot(g), target);
        else
            target.identity();
        target.m00 -= horizontalDrag;
        target.m11 -= horizontalDrag;
        target.m22 -= horizontalDrag;
        return target;
    }

    private double getClampingFactor(final Vector3dc currentVelocity, final Vector3dc expectedVelocityChange) {
        final double k = -currentVelocity.dot(expectedVelocityChange);
        final double v = currentVelocity.lengthSquared();
        if (k < 0) //don't apply friction that increases velocity
            return 0;
        if (10 * k < v) //if the expected velocity is 10 times smaller than the actual velocity, dont bother with clamping it
            return 1;
        if (v < 1E-10) //simpler clamping for tiny values to avoid inaccuracies and numerical explosion
            return v / (k + 1E-10);
        return v * (1 - Math.exp(-k / v)) / k;
    }

    private double getKineticClampingFactor(final Vector3dc currentLinearVelocity, final Vector3dc currentAngularVelocity, final Vector3d frictionForce, final Vector3d frictionTorque, final double timestep) {

        final double numerator = currentLinearVelocity.dot(frictionForce) + currentAngularVelocity.dot(frictionTorque);
        double denominator = frictionForce.dot(frictionForce) * this.subLevel.getMassTracker().getInverseMass() +
                SableMathUtils.multiplyInnerProduct(frictionTorque, this.subLevel.getMassTracker().getInverseInertiaTensor(), frictionTorque);
        denominator *= timestep;
        if (denominator < 1E-10)
            return 1;
        final double t = -numerator / denominator;
        return Math.max(Math.min(t, 1), 0);
    }

    public void addFloatingBlock(final BlockState state, final Vector3d pos) {
        this.sublevelContainer.addFloatingBlock(state, pos);
    }

    public void removeFloatingBlock(final BlockState state, final Vector3d pos) {
        this.sublevelContainer.removeFloatingBlock(state, pos);
    }

    public void queueAddFloatingBlock(final BlockState state, final BlockPos pos) {
        this.sublevelContainer.queueAddFloatingBlock(state, pos);
    }

    public void queueRemoveFloatingBlock(final BlockState state, final BlockPos pos) {
        this.sublevelContainer.queueRemoveFloatingBlock(state, pos);
    }
}
