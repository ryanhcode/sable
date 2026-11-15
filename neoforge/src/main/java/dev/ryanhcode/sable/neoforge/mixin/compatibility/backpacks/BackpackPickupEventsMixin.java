package dev.ryanhcode.sable.neoforge.mixin.compatibility.backpacks;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.spydnel.backpacks.events.BackpackPickupEvents;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

/**
 * Compatibility mixin for {@link com.spydnel.backpacks.events.BackpackPickupEvents} (Backpacks for Dummies), making backpack pickup checks account for sub-levels.
 */

@Mixin(BackpackPickupEvents.class)
public class BackpackPickupEventsMixin {

    @Inject(method = "onRightClickBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;isUnobstructed(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Z"), locals = LocalCapture.CAPTURE_FAILEXCEPTION)
    private static void sable$onRightClickBlock(final PlayerInteractEvent.RightClickBlock event, final CallbackInfo ci, @Local(name = "isAbove") final LocalBooleanRef isAbove) {
        final Player player = event.getEntity();
        final BlockPos pos = event.getPos();
        final SubLevel containing = Sable.HELPER.getContaining(player.level(), pos);
        if (containing != null) {
            final Vec3 world = containing.logicalPose().transformPosition(pos.above().getBottomCenter());
            isAbove.set(world.y - 0.1 > player.getEyeY());
        }
    }

}
