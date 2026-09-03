package dev.ryanhcode.sable.neoforge.mixin.compatibility.flywheel;

import dev.engine_room.flywheel.backend.engine.embed.EnvironmentStorage;
import dev.ryanhcode.sable.neoforge.compatibility.flywheel.SableFlywheelMatrixBuffer;
import org.spongepowered.asm.mixin.Debug;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Mixin into {@link dev.engine_room.flywheel.backend.engine.embed.EnvironmentStorage} to enlarge element size to hold sub-level scene info.
 */

@Mixin(EnvironmentStorage.class)
public class EnvironmentStorageMixin {

    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Ldev/engine_room/flywheel/backend/engine/CpuArena;<init>(JI)V"), index = 0)
    private long sable$overrideMatrixSize(final long elementSizeBytes) {
        return SableFlywheelMatrixBuffer.INFO_SIZE_BYTES;
    }

}
