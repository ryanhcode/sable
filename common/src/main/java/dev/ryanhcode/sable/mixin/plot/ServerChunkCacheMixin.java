package dev.ryanhcode.sable.mixin.plot;

import com.mojang.datafixers.DataFixer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.*;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.entity.ChunkStatusUpdateListener;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Makes the chunk access methods in server chunk caches use the plot system.
 */
@Mixin(ServerChunkCache.class)
public class ServerChunkCacheMixin {

    @Shadow
    @Final
    public ChunkMap chunkMap;
    @Shadow
    @Final
    private ServerLevel level;
    @Unique
    private EmptyLevelChunk sable$emptyChunk;

    @Inject(method = "<init>", at = @At("RETURN"))
    public void init(final ServerLevel serverLevel, final LevelStorageSource.LevelStorageAccess levelStorageAccess, final DataFixer dataFixer, final StructureTemplateManager structureTemplateManager, final Executor executor, final ChunkGenerator chunkGenerator, final int i, final int j, final boolean bl, final ChunkProgressListener chunkProgressListener, final ChunkStatusUpdateListener chunkStatusUpdateListener, final Supplier supplier, final CallbackInfo ci) {
        this.sable$emptyChunk = new EmptyLevelChunk(serverLevel, new ChunkPos(0, 0), serverLevel.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS));
    }

    @Unique
    private @NotNull SubLevelContainer sable$getPlotContainer() {
        final SubLevelContainer container = SubLevelContainer.getContainer(this.level);

        if (container == null) {
            throw new IllegalStateException("Plot container not found in level");
        }
        return container;
    }

    @Inject(method = "getChunkNow", at = @At("HEAD"), cancellable = true)
    private void getChunkNow(final int x, final int z, final CallbackInfoReturnable<LevelChunk> cir) {
        final SubLevelContainer container = this.sable$getPlotContainer();
        if (container.inBounds(x, z)) {
            final LevelChunk chunk = container.getChunk(new ChunkPos(x, z));

            cir.setReturnValue(chunk);
        }
    }

    @Inject(method = "getChunkFutureMainThread", at = @At("HEAD"), cancellable = true)
    private void getChunkFutureMainThread(final int x, final int z, final ChunkStatus chunkStatus, final boolean bl, final CallbackInfoReturnable<CompletableFuture<ChunkResult<ChunkAccess>>> cir) {
        final SubLevelContainer container = this.sable$getPlotContainer();

        if (container.inBounds(x, z)) {
            final ChunkPos chunkPos = new ChunkPos(x, z);
            final LevelChunk chunk = container.getChunk(chunkPos);

            if (chunk != null) {
                cir.setReturnValue(CompletableFuture.completedFuture(ChunkResult.of(chunk)));
            } else {
                cir.setReturnValue(CompletableFuture.completedFuture(ChunkResult.of(this.sable$emptyChunk)));
            }
        }
    }

    @Inject(method = "hasChunk", at = @At("HEAD"), cancellable = true)
    private void hasChunk(final int x, final int z, final CallbackInfoReturnable<Boolean> cir) {
        final SubLevelContainer container = this.sable$getPlotContainer();
        if (container.inBounds(x, z)) {
            final ChunkAccess chunk = container.getChunk(new ChunkPos(x, z));

            cir.setReturnValue(chunk != null);
        }
    }


    @Inject(method = "getChunkForLighting", at = @At("HEAD"), cancellable = true)
    private void getChunkForLighting(final int x, final int z, final CallbackInfoReturnable<LightChunk> cir) {
        final SubLevelContainer container = this.sable$getPlotContainer();
        if (container.inBounds(x, z)) {
            final LevelChunk chunk = container.getChunk(new ChunkPos(x, z));

            cir.setReturnValue(chunk);
        }
    }

    @Inject(method = "isPositionTicking", at = @At("HEAD"), cancellable = true)
    private void isPositionTicking(final long pos, final CallbackInfoReturnable<Boolean> cir) {
        final SubLevelContainer container = this.sable$getPlotContainer();
        if (container.inBounds(ChunkPos.getX(pos), ChunkPos.getZ(pos))) {
            final ChunkPos chunkPos = new ChunkPos(pos);
            final LevelChunk chunk = container.getChunk(chunkPos);

            cir.setReturnValue(chunk != null);
        }
    }

    @Inject(method = "getFullChunk", at = @At("HEAD"), cancellable = true)
    private void getFullChunk(final long pos, final Consumer<LevelChunk> consumer, final CallbackInfo ci) {
        final SubLevelContainer container = this.sable$getPlotContainer();
        if (container.inBounds(ChunkPos.getX(pos), ChunkPos.getZ(pos))) {
            final ChunkPos chunkPos = new ChunkPos(pos);
            final LevelChunk chunk = container.getChunk(chunkPos);

            if (chunk != null) {
                consumer.accept(chunk);
            }

            ci.cancel();
        }
    }

    @Inject(method = "blockChanged", at = @At("HEAD"), cancellable = true)
    private void blockChanged(final BlockPos blockPos, final CallbackInfo ci) {
        final SubLevelContainer container = this.sable$getPlotContainer();

        final ChunkPos pos = new ChunkPos(blockPos);
        if (container.inBounds(pos)) {
            final PlotChunkHolder holder = container.getChunkHolder(pos);

            if (holder == null) {
                throw new UnsupportedOperationException("Cannot change blocks in nonexistent plot holder");
            }

            holder.blockChanged(blockPos);
            ci.cancel();
        }
    }

    @Inject(method = "getVisibleChunkIfPresent", at = @At("HEAD"), cancellable = true)
    private void getVisibleChunkIfPresent(final long l, final CallbackInfoReturnable<ChunkHolder> cir) {
        final int x = ChunkPos.getX(l);
        final int z = ChunkPos.getZ(l);

        final SubLevelContainer container = this.sable$getPlotContainer();
        if (container.inBounds(x, z)) {
            final ChunkPos chunkPos = new ChunkPos(x, z);
            final PlotChunkHolder holder = container.getChunkHolder(chunkPos);

            cir.setReturnValue(holder);
        }
    }
}
