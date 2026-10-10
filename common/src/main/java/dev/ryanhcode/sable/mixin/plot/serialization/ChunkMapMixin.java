package dev.ryanhcode.sable.mixin.plot.serialization;

import com.mojang.datafixers.DataFixer;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.entity.ChunkStatusUpdateListener;
import net.minecraft.world.level.entity.Visibility;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Makes the chunk map use a concurrent unload queue and syncs chunk status changes to the sub-level storage.
 */
@Mixin(ChunkMap.class)
public class ChunkMapMixin {

    @Mutable
    @Shadow
    @Final
    private Queue<Runnable> unloadQueue;

    @Shadow
    @Final
    private ServerLevel level;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void sable$init(final ServerLevel serverLevel, final LevelStorageSource.LevelStorageAccess levelStorageAccess, final DataFixer dataFixer, final StructureTemplateManager structureTemplateManager, final Executor executor, final BlockableEventLoop blockableEventLoop, final LightChunkGetter lightChunkGetter, final ChunkGenerator chunkGenerator, final ChunkProgressListener chunkProgressListener, final ChunkStatusUpdateListener chunkStatusUpdateListener, final Supplier supplier, final int i, final boolean bl, final CallbackInfo ci) {
        this.unloadQueue = new ConcurrentLinkedDeque<>();
    }

    @Inject(method = "onFullChunkStatusChange", at = @At("TAIL"))
    private void sable$onStatusChange(final ChunkPos chunkPos, final FullChunkStatus fullChunkStatus, final CallbackInfo ci) {
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(this.level);
        assert container != null : "Sub-level container is null";

        if (container.inBounds(chunkPos)) {
            return;
        }

        final SubLevelHoldingChunkMap holdingChunkMap = container.getHoldingChunkMap();
        holdingChunkMap.updateChunkStatus(chunkPos, Visibility.fromFullChunkStatus(fullChunkStatus) != Visibility.HIDDEN);

    }
}
