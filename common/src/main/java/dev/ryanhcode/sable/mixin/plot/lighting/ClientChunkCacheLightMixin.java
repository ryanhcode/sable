package dev.ryanhcode.sable.mixin.plot.lighting;

import dev.ryanhcode.sable.render.light_bridge.VirtualLightManager;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts world light updates and notifies sub-levels whose visual bounding
 * box overlaps the changed section, so they rebuild with fresh world light data.
 */
@Mixin(ClientChunkCache.class)
public class ClientChunkCacheLightMixin {

    @Shadow @Final private ClientLevel level;

    @Inject(method = "onLightUpdate", at = @At("RETURN"))
    private void sable$onWorldLightUpdate(final LightLayer type, final SectionPos pos, final CallbackInfo ci) {
        if (type != LightLayer.BLOCK) return;

        VirtualLightManager.get().onWorldLightUpdate(this.level, pos.getX(), pos.getY(), pos.getZ());
    }
}
