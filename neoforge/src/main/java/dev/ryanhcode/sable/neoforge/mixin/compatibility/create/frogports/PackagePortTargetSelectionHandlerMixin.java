package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.frogports;

import com.simibubi.create.content.logistics.packagePort.PackagePortTargetSelectionHandler;
import com.simibubi.create.infrastructure.config.AllConfigs;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Mixin into {@link com.simibubi.create.content.logistics.packagePort.PackagePortTargetSelectionHandler} so package port target validation accounts for sub-levels.
 */

@Mixin(PackagePortTargetSelectionHandler.class)
public class PackagePortTargetSelectionHandlerMixin {
    @Shadow
    public static boolean isPostbox;

    /**
     * @author RyanH
     * @reason Take into account sub-level
     */
    @Overwrite
    public static String validateDiff(final Vec3 nonProjectedTarget, final BlockPos placedPos) {
        final ActiveSableCompanion helper = Sable.HELPER;
        final Minecraft mc = Minecraft.getInstance();
        final LocalPlayer player = mc.player;
        final Level level = player.level();

        final Vector3d target = helper.projectOutOfSubLevel(level, JOMLConversion.toJOML(nonProjectedTarget));
        final SubLevel frogSubLevel = helper.getContaining(level, placedPos);

        if (frogSubLevel != null) {
            frogSubLevel.logicalPose().transformPositionInverse(target);
        }

        final Vector3d localDiff = target.sub(placedPos.getX() + 0.5, placedPos.getY(), placedPos.getZ() + 0.5);
        if (localDiff.y < 0.0 && !isPostbox) {
            return "package_port.cannot_reach_down";
        }

        final double packagePortRange = AllConfigs.server().logistics.packagePortRange.get();
        if (localDiff.lengthSquared() > packagePortRange * packagePortRange) {
            return "package_port.too_far";
        }

        return null;
    }
}
