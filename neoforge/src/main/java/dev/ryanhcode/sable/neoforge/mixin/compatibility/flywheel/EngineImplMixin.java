package dev.ryanhcode.sable.neoforge.mixin.compatibility.flywheel;

import dev.engine_room.flywheel.backend.engine.DrawManager;
import dev.engine_room.flywheel.backend.engine.EngineImpl;
import dev.engine_room.flywheel.backend.engine.LightStorage;
import dev.ryanhcode.sable.neoforge.compatibility.flywheel.SableFlywheelLightStorage;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into {@link dev.engine_room.flywheel.backend.engine.EngineImpl} to replace the default light storage with Sable's sub-level aware implementation.
 */

@Mixin(EngineImpl.class)
public class EngineImplMixin {

    @Shadow
    @Final
    @Mutable
    private LightStorage lightStorage;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void sable$replaceLightStorage(final LevelAccessor level, final DrawManager drawManager, final int maxOriginDistance, final CallbackInfo ci) {
        this.lightStorage.delete();
        this.lightStorage = new SableFlywheelLightStorage(level);
    }
}
