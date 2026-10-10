package dev.ryanhcode.sable.neoforge.mixin.compatibility.flywheel;

import dev.engine_room.flywheel.backend.engine.indirect.MatrixBuffer;
import dev.ryanhcode.sable.neoforge.compatibility.flywheel.SableFlywheelMatrixBuffer;
import org.spongepowered.asm.mixin.Debug;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Mixin into {@link dev.engine_room.flywheel.backend.engine.indirect.MatrixBuffer} to enlarge the matrix buffer stride to hold sub-level scene info.
 */

@Debug(export = true)
@Mixin(MatrixBuffer.class)
public class MatrixBufferMixin {

    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Ldev/engine_room/flywheel/backend/engine/indirect/ResizableStorageArray;<init>(J)V"), index = 0)
    private long sable$overrideMatrixSize(final long stride) {
        return SableFlywheelMatrixBuffer.INFO_SIZE_BYTES;
    }

}
