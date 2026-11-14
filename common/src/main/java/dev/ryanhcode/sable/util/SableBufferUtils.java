package dev.ryanhcode.sable.util;

import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.joml.*;

public class SableBufferUtils {

    public static final StreamCodec<? super RegistryFriendlyByteBuf, Pose3d> POSE3D_STREAM_CODEC = StreamCodec.of(SableBufferUtils::write,
            (buf) -> SableBufferUtils.read(buf, new Pose3d()));

    public static void write(final ByteBuf buf, final Vector3dc vec) {
        buf.writeDouble(vec.x());
        buf.writeDouble(vec.y());
        buf.writeDouble(vec.z());
    }

    public static void write(final ByteBuf buf, final Vector3fc vec) {
        buf.writeFloat(vec.x());
        buf.writeFloat(vec.y());
        buf.writeFloat(vec.z());
    }

    public static Vector3d read(final ByteBuf buf, final Vector3d dest) {
        return dest.set(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public static Vector3f read(final ByteBuf buf, final Vector3f dest) {
        return dest.set(buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    public static void write(final ByteBuf buf, final Quaterniondc quat) {
        buf.writeFloat((float) quat.x());
        buf.writeFloat((float) quat.y());
        buf.writeFloat((float) quat.z());
        buf.writeFloat((float) quat.w());
    }

    public static Quaterniond read(final ByteBuf buf, final Quaterniond dest) {
        return dest.set(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    public static void write(final ByteBuf buf, final Pose3dc pose) {
        SableBufferUtils.write(buf, pose.position());
        SableBufferUtils.write(buf, pose.orientation());
        SableBufferUtils.write(buf, pose.rotationPoint());
        SableBufferUtils.write(buf, pose.scale());
    }

    public static Pose3d read(final ByteBuf buf, final Pose3d pose) {
        SableBufferUtils.read(buf, pose.position());
        SableBufferUtils.read(buf, pose.orientation());
        SableBufferUtils.read(buf, pose.rotationPoint());
        SableBufferUtils.read(buf, pose.scale());
        return pose;
    }

    public static void write(final ByteBuf buf, final BoundingBox3ic bounds) {
        buf.writeInt(bounds.minX());
        buf.writeInt(bounds.minY());
        buf.writeInt(bounds.minZ());
        buf.writeInt(bounds.maxX());
        buf.writeInt(bounds.maxY());
        buf.writeInt(bounds.maxZ());
    }

    public static BoundingBox3i read(final ByteBuf buf, final BoundingBox3i dest) {
        return dest.set(buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt());
    }

}
