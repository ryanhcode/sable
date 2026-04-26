package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.frogports;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
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

@Mixin(PackagePortTargetSelectionHandler.class)
public class PackagePortTargetSelectionHandlerMixin {

    @WrapMethod(method = "validateDiff")
    private static String projectOutOfSB(Vec3 target, BlockPos placedPos, Operation<String> original) {
        final ActiveSableCompanion helper = Sable.HELPER;
        final Minecraft mc = Minecraft.getInstance();
        final LocalPlayer player = mc.player;
        final Level level = player.level();
        target = JOMLConversion.toMojang(helper.projectOutOfSubLevel(level, JOMLConversion.toJOML(target)));
        final SubLevel frogSubLevel = helper.getContaining(level, placedPos);

        if (frogSubLevel != null) {
            frogSubLevel.logicalPose().transformPositionInverse(target);
        }

        return original.call(target, placedPos);
    }
}
