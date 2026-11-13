package dev.ryanhcode.sable.mixin.plot;

import dev.ryanhcode.sable.SableClient;
import dev.ryanhcode.sable.mixinterface.plot.SubLevelContainerHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Runs the network event queue and advances the plot container during the client tick.
 */
@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Shadow @Nullable public ClientLevel level;

    @Shadow private volatile boolean pause;

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/tutorial/Tutorial;onLookAt(Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/world/phys/HitResult;)V"))
    private void sable$tickPlotContainer(final CallbackInfo ci) {
        if (this.level != null) {
            SableClient.NETWORK_EVENT_LOOP.runAllTasks();

            if (!this.pause) {
                ((SubLevelContainerHolder) this.level).sable$getPlotContainer().tick();
            }
        } else {
            SableClient.NETWORK_EVENT_LOOP.clear();
        }
    }
}
