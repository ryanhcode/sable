package dev.ryanhcode.sable.api.math;

import org.jetbrains.annotations.NotNull;
import org.joml.*;

import java.lang.Math;
import java.util.Objects;

/**
 * Represents an oriented bounding box with extents, orientation, and positioning.
 * The box is expected to be centered on the position.
 */
public class OrientedBoundingBox3d {
    public static final Vector3dc UNIT_X = new Vector3d(1, 0, 0);
    public static final Vector3dc UNIT_Y = new Vector3d(0, 1, 0);
    public static final Vector3dc UNIT_Z = new Vector3d(0, 0, 1);

    public static final Vector3dc RIGHT = UNIT_X;
    public static final Vector3dc UP = UNIT_Y;
    public static final Vector3dc FORWARD = UNIT_Z;

    private final Vector3d position = new Vector3d();
    private final Vector3d dimensions = new Vector3d();
    private final Quaterniond orientation = new Quaterniond();
    private final LevelReusedVectors sink;


    /**
     * Creates a new oriented bounding box.
     */
    public OrientedBoundingBox3d(@NotNull final LevelReusedVectors sink) {
        this.sink = sink;
    }

    /**
     * Creates a new oriented bounding box.
     *
     * @param position    The center in global space
     * @param dimensions  The total dimensions
     * @param orientation The unit quaternion rotation
     */
    public OrientedBoundingBox3d(@NotNull final Vector3dc position,
                                 @NotNull final Vector3dc dimensions,
                                 @NotNull final Quaterniondc orientation,
                                 @NotNull final LevelReusedVectors sink) {
        this.position.set(position);
        this.dimensions.set(dimensions);
        this.orientation.set(orientation);
        this.sink = sink;
    }

    /**
     * Creates a new oriented bounding box.
     *
     * @param x           The center X in global space
     * @param y           The center Y in global space
     * @param z           The center Z in global space
     * @param sizeX       The total dimensions in the x-axis
     * @param sizeY       The total dimensions in the y-axis
     * @param sizeZ       The total dimensions in the z-axis
     * @param orientation The unit quaternion rotation
     */
    public OrientedBoundingBox3d(final double x,
                                 final double y,
                                 final double z,
                                 final double sizeX,
                                 final double sizeY,
                                 final double sizeZ,
                                 @NotNull final Quaterniondc orientation,
                                 @NotNull final LevelReusedVectors sink) {
        this.position.set(x, y, z);
        this.dimensions.set(sizeX, sizeY, sizeZ);
        this.orientation.set(orientation);
        this.sink = sink;
    }

    public void set(final Vector3dc position, final Vector3dc dimensions, final Quaterniondc orientation) {
        this.position.set(position);
        this.dimensions.set(dimensions);
        this.orientation.set(orientation);
    }

    public OrientedBoundingBox3d setPosition(final Vector3dc position) {
        this.position.set(position);
        return this;
    }

    public OrientedBoundingBox3d setDimensions(final Vector3dc dimensions) {
        this.dimensions.set(dimensions);
        return this;
    }

    public OrientedBoundingBox3d setOrientation(final Quaterniondc orientation) {
        this.orientation.set(orientation);
        return this;
    }

    public Quaterniond getOrientation() {
        return this.orientation;
    }

    public Vector3d getPosition() {
        return this.position;
    }

    public Vector3d getDimensions() {
        return this.dimensions;
    }

    /**
     * Computes all global vertices of this box.
     */
    public Vector3d @NotNull [] vertices(final Vector3d[] result) {
        this.dimensions.mul(0.5, this.sink.tempmin);
        this.dimensions.mul(-0.5, this.sink.tempmax);

        this.orientation.transform(this.sink.tempmin, result[0]).add(this.position);
        this.orientation.transform(this.sink.tempVert1.set(this.sink.tempmax.x, this.sink.tempmin.y, this.sink.tempmin.z), result[1]).add(this.position);
        this.orientation.transform(this.sink.tempVert4.set(this.sink.tempmin.x, this.sink.tempmin.y, this.sink.tempmax.z), result[4]).add(this.position);
        this.orientation.transform(this.sink.tempVert5.set(this.sink.tempmax.x, this.sink.tempmin.y, this.sink.tempmax.z), result[5]).add(this.position);
        this.orientation.transform(this.sink.tempVert3.set(this.sink.tempmax.x, this.sink.tempmax.y, this.sink.tempmin.z), result[3]).add(this.position);
        this.orientation.transform(this.sink.tempVert2.set(this.sink.tempmin.x, this.sink.tempmax.y, this.sink.tempmin.z), result[2]).add(this.position);
        this.orientation.transform(this.sink.tempVert6.set(this.sink.tempmin.x, this.sink.tempmax.y, this.sink.tempmax.z), result[6]).add(this.position);
        this.orientation.transform(this.sink.tempmax, result[7]).add(this.position);

        return result;
    }


    /**
     * Rotates a vector from local space in this OBB to global space.
     */
    public Vector3d rotate(@NotNull final Vector3d vec) {
        return this.orientation.transform(vec);
    }

    /**
     * Checks if two intervals intersect.
     */
    private static boolean doesOverlap(@NotNull final Vector2d a, @NotNull final Vector2d b) {
        return a.x <= b.y && a.y >= b.x;
    }

    /**
     * @return The overlap of the two intervals.
     */
    public static double getOverlap(@NotNull final Vector2d a, @NotNull final Vector2d b) {
        if (!OrientedBoundingBox3d.doesOverlap(a, b)) {
            return 0.f;
        }

        return Math.min(a.y, b.y) - Math.max(a.x, b.x);
    }

    /**
     * Computes the MTV, or Minimum Translation Vector between the vertices of two OBBs.
     */
    public static @NotNull Vector3d sat(@NotNull final OrientedBoundingBox3d obbA, @NotNull final OrientedBoundingBox3d obbB) {
        return sat(obbA, obbB, new Vector3d());
    }

    /**
     * Computes the MTV, or Minimum Translation Vector between the vertices of two OBBs.
     */
    public static @NotNull Vector3d sat(@NotNull final OrientedBoundingBox3d obbA, @NotNull final OrientedBoundingBox3d obbB, @NotNull final Vector3d dest) {
        Objects.requireNonNull(obbA, "obbA");
        Objects.requireNonNull(obbB, "obbB");
        Objects.requireNonNull(dest, "dest");

        final LevelReusedVectors context = obbA.sink;

        final Vector3d[] verticesA = obbA.vertices(context.a);
        final Vector3d[] verticesB = obbB.vertices(context.b);

        final Vector3d checker = obbA.position.sub(obbB.position, obbA.sink.checker).normalize();

        final Vector3d aRight = obbA.rotate(context.obbARight.set(OrientedBoundingBox3d.UNIT_X));
        final Vector3d aUp = obbA.rotate(context.obbAUp.set(OrientedBoundingBox3d.UNIT_Y));
        final Vector3d aForward = obbA.rotate(context.obbAForward.set(OrientedBoundingBox3d.UNIT_Z));

        final Vector3d bRight = obbB.rotate(context.obbBRight.set(OrientedBoundingBox3d.UNIT_X));
        final Vector3d bUp = obbB.rotate(context.obbBUp.set(OrientedBoundingBox3d.UNIT_Y));
        final Vector3d bForward = obbB.rotate(context.obbBForward.set(OrientedBoundingBox3d.UNIT_Z));

        final Vector3d mtv = dest.set(Double.MAX_VALUE);

        OrientedBoundingBox3d.genChecks(aRight, aUp, aForward, bRight, bUp, bForward, context.checks);

        double minOverlap = Double.MAX_VALUE;

        for (final Vector3d check : context.checks) {
            if (check.lengthSquared() <= 0) {
                continue;
            }

            check.normalize();

            OrientedBoundingBox3d.checkSeparation(verticesA, check, context.proj1);
            OrientedBoundingBox3d.checkSeparation(verticesB, check, context.proj2);

            if (check.dot(checker) > 0) {
                check.mul(-1.0);
            }

            final double overlap = OrientedBoundingBox3d.getOverlap(context.proj1, context.proj2);

            if (overlap == 0.f) { // shapes are not overlapping
                return dest.zero();
            } else {
                if (overlap < minOverlap) {
                    minOverlap = overlap;
                    mtv.set(check.mul(minOverlap));
                }
            }
        }

        final boolean facingOpposite = obbA.position.sub(obbB.position, context.oppo).dot(mtv) < 0;

        if (facingOpposite) {
            mtv.mul(-1);
        }

        return mtv;
    }

    public static @NotNull SweepResult sweep(@NotNull final OrientedBoundingBox3d obbA, @NotNull final OrientedBoundingBox3d obbB, @NotNull final Vector3d sweep, @NotNull final SweepResult dest) {
        final LevelReusedVectors context = obbA.sink;

        final Vector3d aX = obbA.rotate(context.obbARight.set(OrientedBoundingBox3d.UNIT_X));
        final Vector3d aY = obbA.rotate(context.obbAUp.set(OrientedBoundingBox3d.UNIT_Y));
        final Vector3d aZ = obbA.rotate(context.obbAForward.set(OrientedBoundingBox3d.UNIT_Z));

        final Vector3d bX = obbB.rotate(context.obbBRight.set(OrientedBoundingBox3d.UNIT_X));
        final Vector3d bY = obbB.rotate(context.obbBUp.set(OrientedBoundingBox3d.UNIT_Y));
        final Vector3d bZ = obbB.rotate(context.obbBForward.set(OrientedBoundingBox3d.UNIT_Z));

        OrientedBoundingBox3d.genChecks(aX, aY, aZ, bX, bY, bZ, context.checks);

        double tFirst = 0.0;
        double tLast = Double.POSITIVE_INFINITY;
        double minOverlap = Double.POSITIVE_INFINITY;

        final Vector3d hitNormal = context.hitNormal.zero();
        final Vector3d exitNormal = context.exitNormal.zero();
        final Vector3d overlapNormal = context.overlapNormal.zero();

        final Vector3d relativePos = context.relativePos.set(obbB.position).sub(obbA.position);

        final double epsilon = 1e-5;
        for (final Vector3d check : context.checks) {
            if (check.lengthSquared() < epsilon) {
                continue;
            }

            check.normalize();

            final double rA = 0.5 * obbA.dimensions.x * Math.abs(aX.dot(check)) +
                    0.5 * obbA.dimensions.y * Math.abs(aY.dot(check)) +
                    0.5 * obbA.dimensions.z * Math.abs(aZ.dot(check));

            final double rB = 0.5 * obbB.dimensions.x * Math.abs(bX.dot(check)) +
                    0.5 * obbB.dimensions.y * Math.abs(bY.dot(check)) +
                    0.5 * obbB.dimensions.z * Math.abs(bZ.dot(check));

            final double r = rA + rB;
            final double distance = relativePos.dot(check);
            final double speed = sweep.dot(check);

            final double overlap = r - Math.abs(distance);
            if (overlap >= 0 && overlap < minOverlap) {
                minOverlap = overlap;
                overlapNormal.set(check);

                if (distance > 0) {
                    overlapNormal.negate();
                }
            }

            if (Math.abs(speed) < epsilon) {
                if (Math.abs(distance) > r) {
                    dest.reset();
                    return dest;
                }

                continue;
            }

            final double t1 = (distance - r) / speed;
            final double t2 = (distance + r) / speed;
            final double tNear = Math.min(t1, t2);
            final double tFar = Math.max(t1, t2);

            if (tNear > tFirst) {
                tFirst = tNear;
                hitNormal.set(check);
                if (distance > 0) {
                    hitNormal.negate();
                }
            }
            if (tFar < tLast) {
                tLast = tFar;
                exitNormal.set(check);

                if (speed <= 0) {
                    exitNormal.negate();
                }
            }

            if (tFirst > tLast) {
                dest.reset();
                return dest;
            }
        }

        if (!(tFirst <= tLast) || tFirst > 1.0) {
            dest.reset();
            return dest;
        }

        if (hitNormal.lengthSquared() <= 0 && minOverlap != Double.POSITIVE_INFINITY) {
            hitNormal.set(overlapNormal);
        }

        dest.reset();
        dest.hit = true;
        dest.entryT = tFirst;
        dest.exitT = tLast;
        dest.exitNormal.set(exitNormal);
        dest.normal.set(hitNormal);
        return dest;
    }

    public static Vector3d[] genChecks(final Vector3d aRight, final Vector3d aUp, final Vector3d aForward, final Vector3d bRight, final Vector3d bUp, final Vector3d bForward, final Vector3d[] checks) {
        checks[0].set(aRight);
        checks[1].set(aUp);
        checks[2].set(aForward);
        checks[3].set(bRight);
        checks[4].set(bUp);
        checks[5].set(bForward);
        aRight.cross(bRight, checks[6]);
        aRight.cross(bUp, checks[7]);
        aRight.cross(bForward, checks[8]);
        aUp.cross(bRight, checks[9]);
        aUp.cross(bUp, checks[10]);
        aUp.cross(bForward, checks[11]);
        aForward.cross(bRight, checks[12]);
        aForward.cross(bUp, checks[13]);
        aForward.cross(bForward, checks[14]);

        return checks;
    }

    /**
     * Check separation along an axis for Separating Axis Theorem.
     *
     * @return a 2d vector with the first component representing minimum and second component maximum
     */
    public static @NotNull Vector2d checkSeparation(final Vector3d @NotNull [] self, @NotNull final Vector3d axis, final Vector2d result) {
        if (axis.lengthSquared() <= 0.0) {
            return result.set(0, 0);
        }

        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;

        for (final Vector3d vec : self) {
            final double dot = vec.dot(axis);
            min = Math.min(dot, min);
            max = Math.max(dot, max);
        }

        return result.set(min, max);
    }

    public static class SweepResult {
        public boolean hit = false;
        public double entryT;
        public double exitT;
        public final Vector3d exitNormal = new Vector3d();
        public final Vector3d normal = new Vector3d();

        public void reset() {
            this.hit = false;
            this.exitNormal.zero();
            this.normal.zero();
            this.entryT = 0.0;
            this.exitT = 0.0;
        }
    }
}
