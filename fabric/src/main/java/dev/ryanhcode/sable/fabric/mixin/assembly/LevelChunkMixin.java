package dev.ryanhcode.sable.fabric.mixin.assembly;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.fabric.mixinterface.LevelExtension;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Fabric mixin into {@link LevelChunk} to skip {@link BlockState#onPlace} events while blocks are being assembled
 * into a sub-level.
 */
@Mixin(LevelChunk.class)
public class LevelChunkMixin {

    @WrapOperation(method = "setBlockState", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;onPlace(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)V"))
    public void cancelOnPlace(final BlockState instance, final Level level, final BlockPos pos, final BlockState state, final boolean isMoving, final Operation<Void> original) {
        if (!((LevelExtension) level).sable$isIgnoreOnPlace()) {
            original.call(instance, level, pos, state, isMoving);
        }
    }
}
