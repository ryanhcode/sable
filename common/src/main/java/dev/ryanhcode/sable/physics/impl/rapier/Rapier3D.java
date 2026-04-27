package dev.ryanhcode.sable.physics.impl.rapier;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.mixinterface.physics.ServerLevelSceneExtension;
import dev.ryanhcode.sable.physics.impl.rapier.collider.RapierVoxelColliderData;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FrameInputStream;
import net.jpountz.xxhash.XXHashFactory;
import net.minecraft.Util;
import net.minecraft.Util.OS;
import net.minecraft.server.level.ServerLevel;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.jetbrains.annotations.ApiStatus;
import org.joml.Matrix3dc;
import org.joml.Vector3dc;
import org.tukaani.xz.XZInputStream;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Java side of the sable_rapier bridge for using the Rapier 3D physics engine.
 */
@ApiStatus.Internal
public class Rapier3D {

    private static final String NATIVE_DIR = ".sable/natives";
    private static final String LIB_NAME = "sable_rapier";
    private static final String LIB_TMP_DIR_PREFIX = LIB_NAME + "_natives";
    public static boolean ENABLED = false;

    private static int countingSceneID = 0;
    private static int countingObjectID = 0;

    static {
        loadLibrary();
    }

    private static String getNativeName() {
        final String arch;
        if (System.getProperty("os.arch").equals("arm") || System.getProperty("os.arch").startsWith("aarch64")) {
            arch = "aarch64";
        } else {
            arch = "x86_64";
        }

        final OS os = Util.getPlatform();
        if (os == OS.WINDOWS) {
            return LIB_NAME + "_" + arch + "_windows.dll";
        } else if (os == OS.OSX) {
            return LIB_NAME + "_" + arch + "_macos.dylib";
        } else {
            if (os != OS.LINUX)
                Sable.LOGGER.error("Unknown platform '{}' detected, sable will attempt to use linux natives, this may or may not work.", System.getProperty("os.name"));
            return LIB_NAME + "_" + arch + "_linux.so";
        }
    }

    private static void loadLibrary() {
        final String nativeName = getNativeName();
        try (final InputStream is = Rapier3D.class.getResourceAsStream("/natives/" + LIB_NAME + "/sable_rapier_binaries.tar.l4z")) {
            if (is == null) {
                throw new FileNotFoundException("sable_rapier_binaries.tar.l4z");
            }

            final Path dir = Paths.get(NATIVE_DIR);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }

            try (final LZ4FrameInputStream is2 = new LZ4FrameInputStream(is);
                 final TarArchiveInputStream ti = new TarArchiveInputStream(is2)) {

                TarArchiveEntry entry;
                while ((entry = ti.getNextEntry()) != null) {
                    if (entry.getName().equals(nativeName)) {
                        final Path tempFile = dir.resolve(nativeName);
                        Files.copy(ti, tempFile, StandardCopyOption.REPLACE_EXISTING);
                        System.load(tempFile.toAbsolutePath().toString());
                        ENABLED = true;
                        return;
                    }
                }

                throw new FileNotFoundException(nativeName);
            }
        } catch (final Throwable t) {
            ENABLED = false;

            Sable.LOGGER.error(
                    "Sable has failed to load the natives needed for its Rapier pipeline. Native library name {}. Please report with system details and logs to {}",
                    nativeName, Sable.ISSUE_TRACKER_URL, t);
        }
    }

    /**
     * Retrieves the body ID for a given server sub level
     *
     * @return the ID
     */
    @ApiStatus.Internal
    public static int getID(final PhysicsPipelineBody body) {
        return body.getRuntimeId();
    }

    @ApiStatus.Internal
    public static synchronized int nextBodyID() {
        return countingObjectID++;
    }

    /**
     * Retrieves the dimension / scene ID for a given server level
     *
     * @return the dimension ID
     */
    @ApiStatus.Internal
    public static synchronized int getID(final ServerLevel level) {
        if (!(level instanceof final ServerLevelSceneExtension extension)) {
            throw new IllegalArgumentException("ServerLevel must implement ServerLevelSceneExtension to be used with Rapier");
        }

        if (extension.sable$getSceneID() == -1) {
            extension.sable$setSceneID(countingSceneID++);
            Sable.LOGGER.info("Assigned physics scene ID {} to {}", extension.sable$getSceneID(), level.dimension().location());
        }

        return extension.sable$getSceneID();
    }

    @ApiStatus.Internal
    public static native void initialize(final int dimensionID, double gravityX, double gravityY, double gravityZ, double universalDrag);

    @ApiStatus.Internal
    public static native void tick(final int dimensionID, double timeStep);


    @ApiStatus.Internal
    public static native void step(final int dimensionID, double timeStep);

    /**
     * All poses are formatted in a double array as:
     * [x, y, z, qx, qy, qz, qw]
     */

    @ApiStatus.Internal
    public static native void createSubLevel(final int dimensionID, int id, double[] pose);

    /**
     * Removes an object from the physics world.
     */
    @ApiStatus.Internal
    public static native void removeSubLevel(final int dimensionID, int id);

    /**
     * All poses are formatted in a double array as:
     * [x, y, z, qx, qy, qz, qw]
     */
    @ApiStatus.Internal
    public static native void createBox(final int dimensionID, int id, double mass, double halfExtentsX, double halfExtentsY, double halfExtentsZ, double[] pose);

    /**
     * All poses are formatted in a double array as:
     * [x, y, z, qx, qy, qz, qw]
     */
    @ApiStatus.Internal
    public static native void removeBox(final int dimensionID, int id);

    /**
     * Gets the pose of an object.
     *
     * @param id    the object ID
     * @param store The array to store pose of the object in the format [x, y, z, qx, qy, qz, qw]
     */
    @ApiStatus.Internal
    public static native void getPose(final int dimensionID, int id, double[] store);

    /**
     * Sets the center of mass in block coordinates.
     *
     * @param id the object ID
     * @param x  the x position of the center of mass
     * @param y  the y position of the center of mass
     * @param z  the z position of the center of mass
     */
    @ApiStatus.Internal
    public static native void setCenterOfMass(final int dimensionID, int id, double x, double y, double z);

    /**
     * Sets the local block bounds of an object.
     *
     * @param id   the object ID
     * @param minX the minimum x bound (inclusive)
     * @param minY the minimum y bound (inclusive)
     * @param minZ the minimum z bound (inclusive)
     * @param maxX the maximum x bound (inclusive)
     * @param maxY the maximum y bound (inclusive)
     * @param maxZ the maximum z bound (inclusive)
     */
    @ApiStatus.Internal
    public static native void setLocalBounds(final int dimensionID, int id, int minX, int minY, int minZ, int maxX, int maxY, int maxZ);

    /**
     * Sets a chunk at given chunk coordinates.
     *
     * @param x      the chunk x coordinate
     * @param y      the chunk y coordinate
     * @param z      the chunk z coordinate
     * @param chunk  a 4096-long (16x16x16) integer array     stored in xzy order, with x fastest changing.
     * @param global if the chunk is a part of the global world
     * @param id     the object ID the chunk is in, if not global
     */
    @ApiStatus.Internal
    public static native void addChunk(final int dimensionID, int x, int y, int z, int[] chunk, boolean global, int id);

    /**
     * Removes a chunk at given chunk coordinates.
     *
     * @param x      the chunk x coordinate
     * @param y      the chunk y coordinate
     * @param z      the chunk z coordinate
     * @param global if the chunk is a part of the global world
     */
    @ApiStatus.Internal
    public static native void removeChunk(final int dimensionID, int x, int y, int z, boolean global);

    /**
     * Sets a block if it is inside a tracked chunk.
     *
     * @param x        the block x coordinate
     * @param y        the block y coordinate
     * @param z        the block z coordinate
     * @param newState the new physics block ID + 1 of the block, or 0 for empty
     */
    @ApiStatus.Internal
    public static native void changeBlock(final int dimensionID, int x, int y, int z, int newState);

    /**
     * Adds a new voxel collider data entry.
     *
     * @param frictionMultiplier the friction multiplier
     * @param isFluid            if the block should be treated as a fluid
     * @param contactEvents      if the block has special contact event behavior
     * @return the ID of the new block collider data entry
     */
    @ApiStatus.Internal
    protected static native int newVoxelCollider(double frictionMultiplier, double volume, double restitution, boolean isFluid, BlockSubLevelCollisionCallback contactEvents);

    /**
     * Adds a new box to a voxel collider data entry.
     *
     * @param index  the ID of the block physics data entry from {@link Rapier3D#newVoxelCollider(double, double, double, boolean, BlockSubLevelCollisionCallback)}}
     * @param bounds a 6-long double array, formatted [minX, minY, minZ, maxX, maxY, maxZ]
     */
    @ApiStatus.Internal
    public static native void addVoxelColliderBox(int index, double[] bounds);

    /**
     * Clears all boxes from a voxel collider data entry.
     *
     * @param index the ID of the block physics data entry from {@link Rapier3D#newVoxelCollider(double, double, double, boolean, BlockSubLevelCollisionCallback)}}
     */
    @ApiStatus.Internal
    public static native void clearVoxelColliderBoxes(int index);

    /**
     * Sets the mass, center of mass, and inertia tensor of a block physics data entry.
     *
     * @param index the ID of the physics object
     */
    @ApiStatus.Internal
    protected static native void setMassProperties(final int dimensionID, int index, double mass, double[] centerOfMass, double[] inertiaTensor);

    /**
     * Allocates a new block physics data entry
     *
     * @param frictionMultiplier the friction multiplier
     * @param isFluid            if the block should be treated as a fluid
     * @param contactEvents      if the block has special contact event behavior
     * @return the handle of the new block physics data entry
     */
    @ApiStatus.Internal
    public static RapierVoxelColliderData createVoxelColliderEntry(final double frictionMultiplier, final double volume, final double restitution, final boolean isFluid, final BlockSubLevelCollisionCallback contactEvents) {
        return new RapierVoxelColliderData(Rapier3D.newVoxelCollider(frictionMultiplier, volume, restitution, isFluid, contactEvents));
    }

    /**
     * Teleports an object to a new position.
     *
     * @param id the object ID
     * @param x  the new x position
     * @param y  the new y position
     * @param z  the new z position
     */
    @ApiStatus.Internal
    public static native void teleportObject(final int dimensionID, int id, double x, double y, double z, double i, double j, double k, double r);

    /**
     * "Wakes up" an object, indicating environmental or other changes have occurred that should resume physics if idled or sleeping
     *
     * @param id the object ID
     */
    @ApiStatus.Internal
    public static native void wakeUpObject(final int dimensionID, int id);

    /**
     * Adds a rotational constraint between two objects.
     *
     * @param id            the object ID
     * @param otherId       the other object ID
     * @param localAnchorXA the local anchor X on the first object
     * @param localAnchorYA the local anchor Y on the first object
     * @param localAnchorZA the local anchor Z on the first object
     * @param localAnchorXB the local anchor X on the second object
     * @param localAnchorYB the local anchor Y on the second object
     * @param localAnchorZB the local anchor Z on the second object
     * @param localAxisXA   the local axis X on the first object
     * @param localAxisYA   the local axis Y on the first object
     * @param localAxisZA   the local axis Z on the first object
     * @param localAxisXB   the local axis X on the second object
     * @param localAxisYB   the local axis Y on the second object
     * @param localAxisZB   the local axis Z on the second object
     */
    @ApiStatus.Internal
    public static native long addRotaryConstraint(final int dimensionID,
                                                  int id,
                                                  int otherId,
                                                  double localAnchorXA,
                                                  double localAnchorYA,
                                                  double localAnchorZA,
                                                  double localAnchorXB,
                                                  double localAnchorYB,
                                                  double localAnchorZB,
                                                  double localAxisXA,
                                                  double localAxisYA,
                                                  double localAxisZA,
                                                  double localAxisXB,
                                                  double localAxisYB,
                                                  double localAxisZB);

    /**
     * Adds a fixed constraint between two objects.
     *
     * @param id                 the object ID
     * @param otherId            the other object ID
     * @param localAnchorXA      the local anchor X on the first object
     * @param localAnchorYA      the local anchor Y on the first object
     * @param localAnchorZA      the local anchor Z on the first object
     * @param localAnchorXB      the local anchor X on the second object
     * @param localAnchorYB      the local anchor Y on the second object
     * @param localAnchorZB      the local anchor Z on the second object
     * @param localOrientationXB the local orientation X of the second object relative to the first
     * @param localOrientationYB the local orientation Y of the second object relative to the first
     * @param localOrientationZB the local orientation Z of the second object relative to the first
     * @param localOrientationWB the local orientation W of the second object relative to the first
     */
    @ApiStatus.Internal
    public static native long addFixedConstraint(final int dimensionID,
                                                 int id,
                                                 int otherId,
                                                 double localAnchorXA,
                                                 double localAnchorYA,
                                                 double localAnchorZA,
                                                 double localAnchorXB,
                                                 double localAnchorYB,
                                                 double localAnchorZB,
                                                 double localOrientationXB,
                                                 double localOrientationYB,
                                                 double localOrientationZB,
                                                 double localOrientationWB);

    /**
     * Adds a free constraint between two objects.
     *
     * @param id      the object ID
     * @param otherId the other object ID
     */
    @ApiStatus.Internal
    public static native long addFreeConstraint(final int dimensionID,
                                                int id,
                                                int otherId,
                                                double localAnchorXA,
                                                double localAnchorYA,
                                                double localAnchorZA,
                                                double localAnchorXB,
                                                double localAnchorYB,
                                                double localAnchorZB,
                                                double localOrientationXB,
                                                double localOrientationYB,
                                                double localOrientationZB,
                                                double localOrientationWB);

    /**
     * Adds a generic constraint between two objects.
     *
     * @param id                 the object ID
     * @param otherId            the other object ID
     * @param localAnchorXA      the local anchor X on the first object
     * @param localAnchorYA      the local anchor Y on the first object
     * @param localAnchorZA      the local anchor Z on the first object
     * @param localOrientationXA the local orientation X of the first object
     * @param localOrientationYA the local orientation Y of the first object
     * @param localOrientationZA the local orientation Z of the first object
     * @param localOrientationWA the local orientation W of the first object
     * @param localAnchorXB      the local anchor X on the second object
     * @param localAnchorYB      the local anchor Y on the second object
     * @param localAnchorZB      the local anchor Z on the second object
     * @param localOrientationXB the local orientation X of the second object
     * @param localOrientationYB the local orientation Y of the second object
     * @param localOrientationZB the local orientation Z of the second object
     * @param localOrientationWB the local orientation W of the second object
     * @param lockedAxesMask     bit mask of locked axes; bit {@code n} corresponds to {@link dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis#ordinal()}
     */
    @ApiStatus.Internal
    public static native long addGenericConstraint(final int dimensionID,
                                                   int id,
                                                   int otherId,
                                                   double localAnchorXA,
                                                   double localAnchorYA,
                                                   double localAnchorZA,
                                                   double localOrientationXA,
                                                   double localOrientationYA,
                                                   double localOrientationZA,
                                                   double localOrientationWA,
                                                   double localAnchorXB,
                                                   double localAnchorYB,
                                                   double localAnchorZB,
                                                   double localOrientationXB,
                                                   double localOrientationYB,
                                                   double localOrientationZB,
                                                   double localOrientationWB,
                                                   int lockedAxesMask);

    /**
     * Sets the local frame on one side of a constraint.
     *
     * @param handle the handle of the constraint
     * @param side   {@code 0} for the first body, {@code 1} for the second body
     */
    @ApiStatus.Internal
    public static native void setConstraintFrame(final int dimensionID, long handle, int side, double localPosX, double localPosY, double localPosZ, double localOrientationX, double localOrientationY, double localOrientationZ, double localOrientationW);

    /**
     * Sets if contacts are enabled between the two bodies in the constraint
     *
     * @param handle the handle of the constraint
     */
    @ApiStatus.Internal
    public static native void setConstraintContactsEnabled(final int dimensionID, long handle, boolean contactsEnabled);

    /**
     * Gets the latest joint impulses
     *
     * @param handle the handle of the constraint
     */
    @ApiStatus.Internal
    public static native void getConstraintImpulses(final int dimensionID, long handle, final double[] store);

    /**
     * Checks if a constraint is valid
     *
     * @param handle the handle of the constraint
     */
    @ApiStatus.Internal
    public static native boolean isConstraintValid(final int dimensionID, long handle);

    /**
     * Removes a constraint with a handle
     *
     * @param handle the handle of the constraint
     */
    @ApiStatus.Internal
    public static native void removeConstraint(final int dimensionID, long handle);


    /**
     * Sets a constraint to a servo, with a desired angle and PD controller coefficients.
     *
     * @param dimensionID the ID of the dimension
     * @param handle      the handle of the constraint
     */
    @ApiStatus.Internal
    public static native void setConstraintMotor(final int dimensionID, long handle, int axis, double desiredPosition, double stiffness, double damping, boolean hasForceLimit, double maxForce);

    /**
     * Adds linear and angular velocities
     *
     * @param bodyId   the ID of an already created rigid-body
     * @param linearX  x component of the linear velocity to add [m/s]
     * @param linearY  y component of the linear velocity to add [m/s]
     * @param linearZ  z component of the linear velocity to add [m/s]
     * @param angularX x component of the angular velocity to add [rad/s]
     * @param angularY y component of the angular velocity to add [rad/s]
     * @param angularZ z component of the angular velocity to add [rad/s]
     */
    @ApiStatus.Internal
    public static native void addLinearAngularVelocities(final int dimensionID, int bodyId, double linearX, double linearY, double linearZ, double angularX, double angularY, double angularZ, final boolean wakeUp);

    /**
     * Reads & clears all reported collisions from the physics engine.
     * <p>
     * Each collision is formatted as:
     * [body_a, body_b, force_amount, local_normal_a, local_normal_b, local_point_a, local_point_b]
     */
    @ApiStatus.Internal
    public static native double[] clearCollisions(int dimensionID);

    /**
     * Applies a force to a given body
     *
     * @param bodyID the ID of an already created rigid-body
     * @param x      the x position of the force relative to the center of mass
     * @param y      the y position of the force relative to the center of mass
     * @param z      the z position of the force relative to the center of mass
     * @param fx     the x component of the force to apply [N]
     * @param fy     the y component of the force to apply [N]
     * @param fz     the z component of the force to apply [N]
     */
    @ApiStatus.Internal
    public static native void applyForce(final int dimensionID, final int bodyID, final double x, final double y, final double z, final double fx, final double fy, final double fz, final boolean wakeUp);

    /**
     * Applies a force to a given body
     *
     * @param bodyID the ID of an already created rigid-body
     * @param fx     the x component of the force to apply [N]
     * @param fy     the y component of the force to apply [N]
     * @param fz     the z component of the force to apply [N]
     * @param tx     the x component of the torque to apply [Nm]
     * @param ty     the y component of the torque to apply [Nm]
     * @param tz     the z component of the torque to apply [Nm]
     */
    @ApiStatus.Internal
    public static native void applyForceAndTorque(final int dimensionID, final int bodyID, final double fx, final double fy, final double fz, final double tx, final double ty, final double tz, final boolean wakeUp);

    /**
     * Gets the linear velocity of a given body
     *
     * @param bodyID the ID of an already created rigid-body
     * @param store  The array to store the linear velocity of the body in the format [x, y, z]
     */
    @ApiStatus.Internal
    public static native void getLinearVelocity(final int dimensionID, final int bodyID, final double[] store);

    /**
     * Gets the angular velocity of a given body
     *
     * @param bodyID the ID of an already created rigid-body
     * @param store  The array to store the angular velocity of the body in the format [x, y, z]
     */
    @ApiStatus.Internal
    public static native void getAngularVelocity(final int dimensionID, final int bodyID, final double[] store);

    /**
     * Creates a kinematic sub-level within a scene.
     *
     * @param sceneId the scene ID
     * @param mountId the mount rigid body ID (or -1 for ground)
     * @param id      the kinematic sub-level ID
     * @param pose    a 7-long double array, formatted [x, y, z, qx, qy, qz, qw] for position and quaternion
     */
    @ApiStatus.Internal
    public static native void createKinematicContraption(final int sceneId, int mountId, int id, double[] pose);

    /**
     * Removes a kinematic sub-level from a scene.
     *
     * @param sceneId the scene ID
     * @param id      the kinematic sub-level ID to remove
     */
    @ApiStatus.Internal
    public static native void removeKinematicContraption(final int sceneId, int id);

    /**
     * Sets the transform (position/quaternion) of a kinematic sub-level's center of mass relative to its parent.
     *
     * @param sceneId the scene ID
     * @param id      the kinematic sub-level ID
     * @param pose    a 7-long double array, formatted [x, y, z, qx, qy, qz, qw] for position and quaternion
     */
    @ApiStatus.Internal
    public static native void setKinematicContraptionTransform(final int sceneId, int id, double[] centerOfMass, double[] pose, double[] velocities);

    /**
     * Adds a chunk to a kinematic sub-level (4096 blocks, each as packed int).
     *
     * @param sceneId the scene ID
     * @param id      the kinematic sub-level ID
     * @param x       the chunk x coordinate
     * @param y       the chunk y coordinate
     * @param z       the chunk z coordinate
     * @param data    a 4096-long int array containing packed block data (block_collider_id << 16 | voxel_state_id)
     */
    @ApiStatus.Internal
    public static native void addKinematicContraptionChunkSection(final int sceneId, int id, int x, int y, int z, int[] data);

    /**
     * Creates a rope
     *
     * @return a rope id
     */
    @ApiStatus.Internal
    public static native long createRope(final int dimensionID, final double pointRadius, final double firstJointLength, final double[] points, final int pointCount);

    /**
     * Removes a rope
     *
     * @param ropeId a rope id
     */
    @ApiStatus.Internal
    public static native long removeRope(final int dimensionID, final long ropeId);

    @ApiStatus.Internal
    public static native void setRopeAttachment(final int dimensionID, final long ropeId, final int subLevelId, final double x, final double y, final double z, final boolean end);

    @ApiStatus.Internal
    public static native void addRopePointAtStart(final int dimensionID, final long ropeId, final double x, final double y, final double z);

    @ApiStatus.Internal
    public static native void removeRopePointAtStart(final int dimensionID, final long ropeId);

    @ApiStatus.Internal
    public static native void wakeUpRope(final int dimensionID, final long ropeId);

    @ApiStatus.Internal
    public static native void setRopeFirstSegmentLength(final int dimensionID, final long ropeId, final double firstSegmentLength);


    /**
     * Queries a rope
     *
     * @param ropeId a rope id
     */
    @ApiStatus.Internal
    public static native double[] queryRope(final int dimensionID, final long ropeId);

    @ApiStatus.Internal
    public static native void configFrequencyAndDamping(
            double contactNaturalFrequency,
            double contactDampingRatio);

    @ApiStatus.Internal
    public static native void configSolverIterations(int solverIterations, int pgsIterations, int stabilizationIterations);

    @ApiStatus.Internal
    public static native void configMinIslandSize(int islandSize);

    public static native void dispose();

    public static void setMassPropertiesFrom(final int dimensionID, final int id, final MassData massTracker) {
        final Matrix3dc inertiaTensor = massTracker.getInertiaTensor();
        final Vector3dc centerOfMass = massTracker.getCenterOfMass();
        final double mass = massTracker.getMass();

        final double[] centerOfMassArray = new double[]{centerOfMass.x(), centerOfMass.y(), centerOfMass.z()};
        final double[] inertiaTensorArray = new double[]{
                inertiaTensor.m00(), inertiaTensor.m01(), inertiaTensor.m02(),
                inertiaTensor.m10(), inertiaTensor.m11(), inertiaTensor.m12(),
                inertiaTensor.m20(), inertiaTensor.m21(), inertiaTensor.m22()
        };

        Rapier3D.setMassProperties(dimensionID, id, mass, centerOfMassArray, inertiaTensorArray);
    }
}
