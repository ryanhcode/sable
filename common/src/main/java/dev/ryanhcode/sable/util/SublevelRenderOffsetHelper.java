package dev.ryanhcode.sable.util;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;
import org.joml.Quaternionf;
import org.joml.Vector3dc;

public class SublevelRenderOffsetHelper {
    /**
     * @param subLevel Sub-level to move PoseStack from
     * @param ps       Pose stack to be manipulated
     * @usage Called before the PoseStack is translated to its plot position, or before anything is rendered with the plot position
     */
    public static void posePlotToProjected(final SubLevel subLevel, final PoseStack ps) {
        if (subLevel != null) {
            final Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();

            final Pose3dc pose = ((ClientSubLevel) subLevel).renderPose();

            final Vector3dc pos = pose.position();
            final Vector3dc scale = pose.scale();
            final Quaterniondc orientation = pose.orientation();

            ps.translate(pos.x() - camera.x, pos.y() - camera.y, pos.z() - camera.z);
            ps.mulPose(new Quaternionf(orientation));
            ps.scale((float) scale.x(), (float) scale.y(), (float) scale.z());
            ps.translate(camera.x, camera.y, camera.z);
        }
    }

    public static Vec3 translation(final Vec3 center) {
        final ClientSubLevel subLevel = Sable.HELPER.getContainingClient(center);
        if (subLevel != null) {
            final Pose3dc pose = subLevel.renderPose();
            return JOMLConversion.toMojang(pose.rotationPoint()).scale(1.0);
        }
        return Vec3.ZERO;
    }
}
