package dev.ryanhcode.sable.mixin.plot;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.SableCommonEvents;
import dev.ryanhcode.sable.render.light_bridge.ServerSubLevelLightInjector;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
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

    @Unique
    private BlockPos sable$blockSet = null;

    @Unique
    private boolean sable$lightEmitterChanged = false;

    @Inject(method = "setBlockState", at = @At("HEAD"))
    private void sable$preSetBlockState(final BlockPos pPos, final BlockState pState, final boolean pIsMoving,
                                        final CallbackInfoReturnable<BlockState> cir) {
        this.sable$blockSet = pPos;
    }

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void sable$postSetBlockState(final BlockPos pPos, final BlockState pState, final boolean pIsMoving,
                                         final CallbackInfoReturnable<BlockState> cir) {
        if (this.sable$blockSet != null) {
            final SubLevel subLevel = Sable.HELPER.getContaining(this.level, this.sable$blockSet);

            if (subLevel != null) {
                subLevel.getPlot().onBlockChange(this.sable$blockSet, pState);

                if (this.sable$lightEmitterChanged
                        && this.level instanceof final ServerLevel serverLevel
                        && subLevel instanceof final ServerSubLevel serverSubLevel) {
                    ServerSubLevelLightInjector.onPlotBlockLightChanged(serverLevel, serverSubLevel);
                }
            }
        }
        this.sable$blockSet = null;
        this.sable$lightEmitterChanged = false;
    }

    @WrapOperation(method = "setBlockState", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/LevelChunkSection;setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState sable$setBlockState(final LevelChunkSection instance, int pX, int pY, int pZ, final BlockState newState, final Operation<BlockState> original) {
        final BlockState oldState = original.call(instance, pX, pY, pZ, newState);

        if (oldState != newState) {
            if (oldState.getLightEmission() != newState.getLightEmission()) {
                this.sable$lightEmitterChanged = true;
            }

            if (this.level instanceof final ServerLevel serverLevel) {
                pX = this.sable$blockSet.getX();
                pY = this.sable$blockSet.getY();
                pZ = this.sable$blockSet.getZ();

                SableCommonEvents.handleBlockChange(serverLevel, (LevelChunk) (Object) this, pX, pY, pZ, oldState, newState);
            }
        }

        return oldState;
    }


}
