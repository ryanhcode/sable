package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.tracks;

import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.trains.track.TrackBlockItem;
import com.simibubi.create.content.trains.track.TrackTargetingBlockItem;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Mixin into {@link com.simibubi.create.content.trains.track.TrackBlockItem} and {@link com.simibubi.create.content.trains.track.TrackTargetingBlockItem} to account for sub-level orientation when placing tracks.
 */

@Mixin({ TrackBlockItem.class, TrackTargetingBlockItem.class })
public class TrackBlockItemMixin {

    @Redirect(method = "useOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getLookAngle()Lnet/minecraft/world/phys/Vec3;"))
    public Vec3 sable$getLookAngle(final Player instance, @Local(argsOnly = true) final UseOnContext context) {
        final Level level = context.getLevel();
        final BlockPos clickedPos = context.getClickedPos();
        final SubLevel subLevel = Sable.HELPER.getContaining(level, clickedPos);

        Vec3 lookAngle = instance.getLookAngle();
        if (subLevel != null) {
            lookAngle = subLevel.logicalPose().transformNormalInverse(lookAngle);
        }

        return lookAngle;
    }

}
