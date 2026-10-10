package dev.ryanhcode.sable.neoforge.mixin.compatibility.flywheel;

import dev.engine_room.flywheel.backend.engine.LightDataCollector;
import dev.engine_room.flywheel.backend.engine.LightStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Mixin into {@link dev.engine_room.flywheel.backend.engine.LightStorage} to expose its collector and LUT rebuild flag.
 */

@Mixin(LightStorage.class)
public interface LightStorageAccessor {

    @Accessor
    LightDataCollector getCollector();

    @Accessor
    void setNeedsLutRebuild(boolean needsLutRebuild);

}
