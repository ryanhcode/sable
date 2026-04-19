package dev.ryanhcode.sable.neoforge.mixinhelper.compatibility.create.redstone_links;

import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.infrastructure.config.AllConfigs;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;

public class SubLevelRedstoneLinkUtility {
    
    public static boolean projectRedstoneLinkComparison(final IRedstoneLinkable from, final IRedstoneLinkable to, final Level level) {
        if (from == to) return true;
        
        final Vector3d fromPos = JOMLConversion.atCenterOf(from.getLocation());
        final Vector3d toPos = JOMLConversion.atCenterOf(to.getLocation());
        
        final ActiveSableCompanion helper = Sable.HELPER;
        final SubLevel fromSublevel = helper.getContaining(level, fromPos);
        if (fromSublevel != null) {
            fromSublevel.logicalPose().transformPosition(fromPos);
        }
        
        final SubLevel toSublevel = helper.getContaining(level, toPos);
        if (toSublevel != null) {
            toSublevel.logicalPose().transformPosition(toPos);
        }
        
        final int linkRange = AllConfigs.server().logistics.linkRange.get();
        return fromPos.distanceSquared(toPos) < linkRange * linkRange;
    }
    
}
