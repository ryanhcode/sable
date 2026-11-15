package dev.ryanhcode.sable.mixin.punching;

import dev.ryanhcode.sable.network.client.ClientSubLevelPunchHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin into {@link net.minecraft.client.multiplayer.MultiPlayerGameMode} to also try punching sub-level blocks when starting to destroy a block.
 */
@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "startDestroyBlock", at = @At("HEAD"))
    private void onBlockBreakStart(final BlockPos blockPos, final Direction direction, final CallbackInfoReturnable<Boolean> cir) {
        assert this.minecraft.player != null;

        if (this.minecraft.hitResult instanceof final BlockHitResult blockHitResult) {
            ClientSubLevelPunchHelper.clientTryPunch(blockHitResult, this.minecraft.level, true);
        }
    }

}
