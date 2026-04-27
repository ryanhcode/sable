package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.tracks;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.trains.track.TrackPlacement;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(TrackPlacement.class)
public class TrackPlacementMixin {

    @WrapOperation(method = "tryConnect", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getLookAngle()Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 sable$getLookAngle(final Player instance, Operation<Vec3> original, @Local(argsOnly = true) final BlockPos blockPos) {
        final Level level = instance.level();
        final BlockPos clickedPos = blockPos;
        final SubLevel subLevel = Sable.HELPER.getContaining(level, clickedPos);

        Vec3 lookAngle = original.call(instance);
        if (subLevel != null) {
            lookAngle = subLevel.logicalPose().transformNormalInverse(lookAngle);
        }

        return lookAngle;
    }

}
