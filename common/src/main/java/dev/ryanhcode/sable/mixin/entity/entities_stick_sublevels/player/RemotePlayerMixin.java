package dev.ryanhcode.sable.mixin.entity.entities_stick_sublevels.player;

import dev.ryanhcode.sable.mixinterface.entity.entities_stick_sublevels.LivingEntityStickExtension;
import net.minecraft.client.player.RemotePlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into {@link net.minecraft.client.player.RemotePlayer} to apply the sub-level position interpolation updates to remote players.
 */
@Mixin(RemotePlayer.class)
public abstract class RemotePlayerMixin implements LivingEntityStickExtension {

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void sable$updateRotLerp(final CallbackInfo ci) {
        this.sable$setupLerp();
    }

    @Inject(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/RemotePlayer;updateSwingTime()V", shift = At.Shift.BEFORE))
    private void sable$updateSubLevelPosition(final CallbackInfo ci) {
        this.sable$applyLerp();
    }
}
