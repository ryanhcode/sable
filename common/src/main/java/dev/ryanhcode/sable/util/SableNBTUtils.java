package dev.ryanhcode.sable.util;


import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.nbt.CompoundTag;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public class SableNBTUtils {

    public static CompoundTag writePose3d(final Pose3d pose) {
        final CompoundTag tag = new CompoundTag();

        tag.put("position", writeVector3d(pose.position()));
        tag.put("orientation", writeQuaternion(pose.orientation()));
        tag.put("rotation_point", writeVector3d(pose.rotationPoint()));
        tag.put("scale", writeVector3d(pose.scale()));

        return tag;
    }

    public static Pose3d readPose3d(final CompoundTag tag) {
        return new Pose3d(
                readVector3d(tag.getCompound("position")),
                readQuaternion(tag.getCompound("orientation")),
                readVector3d(tag.getCompound("rotation_point")),
                tag.contains("scale") ? readVector3d(tag.getCompound("scale")) : new Vector3d(1.0)
        );
    }

    public static CompoundTag writeQuaternion(final Quaterniondc quat) {
        final CompoundTag tag = new CompoundTag();

        tag.putDouble("x", quat.x());
        tag.putDouble("y", quat.y());
        tag.putDouble("z", quat.z());
        tag.putDouble("w", quat.w());

        return tag;
    }

    public static Quaterniond readQuaternion(final CompoundTag tag) {
        return new Quaterniond(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"), tag.getDouble("w"));
    }

    public static CompoundTag writeVector3d(final Vector3dc vector) {
        final CompoundTag tag = new CompoundTag();

        tag.putDouble("x", vector.x());
        tag.putDouble("y", vector.y());
        tag.putDouble("z", vector.z());

        return tag;
    }

    public static Vector3d readVector3d(final CompoundTag tag) {
        return new Vector3d(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"));
    }

    public static CompoundTag writeBoundingBox(final BoundingBox3dc bounds) {
        final CompoundTag tag = new CompoundTag();

        tag.putDouble("minX", bounds.minX());
        tag.putDouble("minY", bounds.minY());
        tag.putDouble("minZ", bounds.minZ());
        tag.putDouble("maxX", bounds.maxX());
        tag.putDouble("maxY", bounds.maxY());
        tag.putDouble("maxZ", bounds.maxZ());

        return tag;
    }

    public static BoundingBox3d readBoundingBox(final CompoundTag tag) {
        return new BoundingBox3d(
                tag.getDouble("minX"),
                tag.getDouble("minY"),
                tag.getDouble("minZ"),
                tag.getDouble("maxX"),
                tag.getDouble("maxY"),
                tag.getDouble("maxZ")
        );
    }
}
