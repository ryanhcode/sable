package dev.ryanhcode.sable.mixin.sculk_vibrations;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(VibrationSystem.Listener.class)
public class VibrationSystemListenerMixin {

    @WrapMethod(method = "scheduleVibration")
    private void sable$useGlobalPos(final ServerLevel level, final VibrationSystem.Data data, final Holder<GameEvent> gameEvent, final GameEvent.Context context, final Vec3 pos, final Vec3 sensorPos, final Operation<Void> original) {
        original.call(level, data, gameEvent, context, Sable.HELPER.projectOutOfSubLevel(level, pos), Sable.HELPER.projectOutOfSubLevel(level, sensorPos));
    }

    @WrapMethod(method = "isOccluded")
    private static boolean sable$occlusionChecks(final Level level, final Vec3 pos1, final Vec3 pos2, final Operation<Boolean> original) {
        final ActiveSableCompanion helper = Sable.HELPER;

        // Check occlusion in global space
        final Vec3 global1 = helper.projectOutOfSubLevel(level, pos1);
        final Vec3 global2 = helper.projectOutOfSubLevel(level, pos2);
        if (original.call(level, global1, global2)) return true;

        // Check if in same sub-level
        final SubLevel l1 = helper.getContaining(level, pos1);
        final SubLevel l2 = helper.getContaining(level, pos2);
        if (l1 == l2) {
            // Was a global space interaction, already checked
            if (l1 == null) {
                return false;
            }
            return original.call(level, pos1, pos2);
        }

        // Different sub-levels, check event transformed to user and vice versa
        if (l2 != null && original.call(level, l2.logicalPose().transformPositionInverse(global1), pos2)) {
            return true;
        }
        return l1 != null && original.call(level, l1.logicalPose().transformPositionInverse(global2), pos2);
    }
}
