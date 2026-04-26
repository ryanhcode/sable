package dev.ryanhcode.sable.mixin.plot;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Hooks into getPlayers so that packets sent regarding plot chunks are also sent to players tracking the sub-level containing the plot.
 */
@Mixin(ChunkMap.class)
public class ChunkMapMixin {

    @Shadow
    @Final
    private ServerLevel level;

    @Inject(method = "getPlayers", at = @At("HEAD"), cancellable = true)
    private void sable$getPlayers(final ChunkPos chunkPos, final boolean bl, final CallbackInfoReturnable<List<ServerPlayer>> cir) {
        final SubLevelContainer container = SubLevelContainer.getContainer(this.level);

        if (container.inBounds(chunkPos)) {
            final List<ServerPlayer> players = container.getPlayersTracking(chunkPos);
            cir.setReturnValue(players);
        }
    }

    @Inject(method = "saveChunkIfNeeded", at = @At("HEAD"), cancellable = true)
    private void sable$saveChunkIfNeeded(final ChunkHolder chunkHolder, final CallbackInfoReturnable<Boolean> cir) {
        if (chunkHolder instanceof PlotChunkHolder) {
            cir.setReturnValue(false);
        }
    }

    /**
     * Instead of only letting the server stop when the updating chunk map is empty, we stop when it is empty of **plot chunks**, because plot chunks do not unload through vanilla means.
     * TODO: Remove when plot chunks are unloaded with their plots
     */
    @WrapOperation(method = "hasWork", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/longs/Long2ObjectLinkedOpenHashMap;isEmpty()Z", ordinal = 1, remap = false))
    private boolean sable$hasWork(final Long2ObjectLinkedOpenHashMap<ChunkHolder> updatingChunkMap, Operation<Boolean> original) {
        return original.call(updatingChunkMap) || !updatingChunkMap.values().stream().anyMatch(chunkHolder -> !(chunkHolder instanceof PlotChunkHolder));
    }

    @Inject(method = "isChunkTracked", at = @At(value = "HEAD"), cancellable = true)
    private void sable$isChunkTracked(final ServerPlayer serverPlayer, final int i, final int j, final CallbackInfoReturnable<Boolean> cir) {
        final SubLevelContainer container = SubLevelContainer.getContainer(this.level);

        final LevelPlot plot = container.getPlot(new ChunkPos(i, j));
        if (plot != null) {
            final ServerSubLevel subLevel = (ServerSubLevel) plot.getSubLevel();
            cir.setReturnValue(subLevel.getTrackingPlayers().contains(serverPlayer.getGameProfile().getId()));
        }
    }

    @Inject(method = "anyPlayerCloseEnoughForSpawning", at = @At("HEAD"), cancellable = true)
    private void sable$anyPlayerCloseEnoughForSpawning(final ChunkPos chunkPos, final CallbackInfoReturnable<Boolean> cir) {
        final SubLevelContainer container = SubLevelContainer.getContainer(this.level);
        assert container != null;

        if (container.inBounds(chunkPos)) {
            final LevelPlot plot = container.getPlot(chunkPos);
            if (plot != null) {
                final ServerSubLevel subLevel = (ServerSubLevel) plot.getSubLevel();
                cir.setReturnValue(!subLevel.getTrackingPlayers().isEmpty());
            }
        }
    }

}
