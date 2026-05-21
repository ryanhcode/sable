package dev.ryanhcode.sable.mixin.plot;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.platform.SablePlatform;
import dev.ryanhcode.sable.sublevel.storage.SubLevelOccupancySavedData;
import dev.ryanhcode.sable.sublevel.storage.SubLevelTicketsSavedData;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProgressListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.WritableLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Ticks the sub-level container stored in the {@link LevelsMixin} for server levels
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin extends Level {

    protected ServerLevelMixin(final WritableLevelData writableLevelData, final ResourceKey<Level> resourceKey, final RegistryAccess registryAccess, final Holder<DimensionType> holder, final Supplier<ProfilerFiller> supplier, final boolean bl, final boolean bl2, final long l, final int i) {
        super(writableLevelData, resourceKey, registryAccess, holder, supplier, bl, bl2, l, i);
    }

    @Shadow
    public abstract TickRateManager tickRateManager();

    @Shadow
    public abstract ChunkSource getChunkSource();

    @Inject(method = "<init>", at = @At("TAIL"))
    private void sable$init(final CallbackInfo ci) {
        // Load occupancy and ticket data if it isn't already
        if (!SablePlatform.INSTANCE.isWrappedLevel((ServerLevel) (Object) this)) {
            SubLevelOccupancySavedData.getOrLoad((ServerLevel) (Object) this);
            SubLevelTicketsSavedData.getOrLoad((ServerLevel) (Object) this);
        }

        final ServerSubLevelContainer container = (ServerSubLevelContainer) SubLevelContainer.getContainer(this);
        if (container != null) {
            container.initialize();
        }
    }

    /**
     * high up injection so we're before normal chunk saving
     */
    @Inject(method = "save", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;saveLevelData()V", shift = At.Shift.BEFORE))
    public void sable$saveSubLevels(final ProgressListener progressListener, final boolean bl, final boolean bl2, final CallbackInfo ci) {
        final ServerLevel self = (ServerLevel) (Object) this;
        if (progressListener != null) {
            progressListener.progressStartNoAbort(Component.translatable("menu.savingSubLevels"));
        }

        final ServerSubLevelContainer container = SubLevelContainer.getContainer(self);
        assert container != null : "No sub-level container";

        final SubLevelHoldingChunkMap holdingChunkMap = container.getHoldingChunkMap();
        holdingChunkMap.saveAll();
    }

    @Inject(method = "tick(Ljava/util/function/BooleanSupplier;)V", at = @At("HEAD"))
    private void sable$tickPlotContainer(final BooleanSupplier booleanSupplier, final CallbackInfo ci) {
        final TickRateManager tickRateManager = this.tickRateManager();
        final boolean runNormally = tickRateManager.runsNormally();

        final ServerSubLevelContainer plotContainer = SubLevelContainer.getContainer((ServerLevel) (Object) this);
        assert plotContainer != null : "SubLevelContainer is null when ticking";

        if (runNormally) {
            plotContainer.tick();
        }
    }

    @Inject(method = "shouldTickBlocksAt", at = @At("HEAD"), cancellable = true)
    private void sable$shouldTickBlocksAt(final long l, final CallbackInfoReturnable<Boolean> cir) {
        final SubLevelContainer plotContainer = SubLevelContainer.getContainer((ServerLevel) (Object) this);
        assert plotContainer != null;

        if (plotContainer.getPlot(new ChunkPos(l)) != null) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "isNaturalSpawningAllowed(Lnet/minecraft/world/level/ChunkPos;)Z", at = @At("HEAD"), cancellable = true)
    private void sable$isNaturalSpawningAllowed(final ChunkPos chunkPos, final CallbackInfoReturnable<Boolean> cir) {
        final SubLevelContainer plotContainer = SubLevelContainer.getContainer((ServerLevel) (Object) this);
        assert plotContainer != null;

        if (plotContainer.getPlot(chunkPos) != null) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "close", at = @At("TAIL"))
    private void sable$close(final CallbackInfo ci) {
        final ServerSubLevelContainer container = (ServerSubLevelContainer) SubLevelContainer.getContainer(this);

        if (container != null) {
            container.close();
        }
    }
}
