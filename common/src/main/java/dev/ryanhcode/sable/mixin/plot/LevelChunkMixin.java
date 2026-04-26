package dev.ryanhcode.sable.mixin.plot;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.SableCommonEvents;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks into setBlockState to notify plots & plot chunk holders of block changes.
 */
@Mixin(LevelChunk.class)
public class LevelChunkMixin {

    @Shadow
    @Final
    private Level level;

    @Inject(method = "setBlockState", at = @At("HEAD"))
    private void sable$preSetBlockState(final BlockPos pPos, final BlockState pState, final boolean pIsMoving,
                                        final CallbackInfoReturnable<BlockState> cir, @Share("sable$blockSet") LocalRef<BlockPos> pos) {
        pos.set(pPos);
    }

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void sable$postSetBlockState(final BlockPos pPos, final BlockState pState, final boolean pIsMoving,
                                         final CallbackInfoReturnable<BlockState> cir, @Share("sable$blockSet") LocalRef<BlockPos> pos) {
        if (pos.get() != null) {
            final SubLevel subLevel = Sable.HELPER.getContaining(this.level, pos.get());

            if (subLevel != null) {
                subLevel.getPlot().onBlockChange(pos.get(), pState);
            }
        }
        pos.set(null);
    }

    @WrapOperation(method = "setBlockState", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/LevelChunkSection;setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState sable$setBlockState(final LevelChunkSection instance, int pX, int pY, int pZ, final BlockState newState, final Operation<BlockState> original, @Share("sable$blockSet") LocalRef<BlockPos> pos) {
        final BlockState oldState = original.call(instance, pX, pY, pZ, newState);

        if (this.level instanceof final ServerLevel serverLevel && oldState != newState) {
            pX = pos.get().getX();
            pY = pos.get().getY();
            pZ = pos.get().getZ();

            SableCommonEvents.handleBlockChange(serverLevel, (LevelChunk) (Object) this, pX, pY, pZ, oldState, newState);
        }

        return oldState;
    }


}
