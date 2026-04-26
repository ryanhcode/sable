package dev.ryanhcode.sable.mixin.plot;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.platform.SableChunkEventPlatform;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Consumer;

/**
 * Makes the chunk access methods in the client chunk cache use the plot system.
 */
@Mixin(value = ClientChunkCache.class, priority = 900)
public abstract class ClientChunkCacheMixin {

    @Shadow
    @Final
    private static Logger LOGGER;
    @Shadow
    @Final
    private ClientLevel level;
    @Shadow
    @Final
    private LevelChunk emptyChunk;

    @Shadow
    private static boolean isValidChunk(@Nullable final LevelChunk levelChunk, final int i, final int j) {
        return false;
    }

    @Unique
    private @NotNull SubLevelContainer sable$getPlotContainer() {
        final SubLevelContainer container = SubLevelContainer.getContainer(this.level);

        if (container == null) {
            throw new IllegalStateException("Plot container not found in level");
        }
        return container;
    }

    @Inject(method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/LevelChunk;", at = @At("HEAD"), cancellable = true)
    private void getChunk(final int x, final int z, final ChunkStatus status, final boolean create, final CallbackInfoReturnable<LevelChunk> cir) {
        final SubLevelContainer container = this.sable$getPlotContainer();

        if (container.inBounds(x, z)) {
            final ChunkPos chunkPos = new ChunkPos(x, z);
            final LevelChunk chunk = container.getChunk(chunkPos);

            if (chunk != null) {
                cir.setReturnValue(chunk);
            } else {
                cir.setReturnValue(this.emptyChunk);
            }
        }
    }

    @Inject(method = "drop", at = @At("HEAD"), cancellable = true)
    private void drop(final ChunkPos chunkPos, final CallbackInfo ci) {
        final SubLevelContainer container = this.sable$getPlotContainer();

        if (container.inBounds(chunkPos)) {
            ci.cancel();
            throw new UnsupportedOperationException("Cannot drop chunks in plot");
        }
    }

    @Inject(method = "replaceBiomes", at = @At("HEAD"), cancellable = true)
    private void replaceBiomes(final int x, final int z, final FriendlyByteBuf friendlyByteBuf, final CallbackInfo ci) {
        final SubLevelContainer container = this.sable$getPlotContainer();

        if (container.inBounds(x, z)) {
            final ChunkPos chunkPos = new ChunkPos(x, z);
            final LevelChunk levelChunk = container.getChunk(chunkPos);

            if (levelChunk == null || !isValidChunk(levelChunk, x, z)) {
                LOGGER.warn("Ignoring chunk since it's not present: {}, {}", x, z);
            } else {
                levelChunk.replaceBiomes(friendlyByteBuf);
            }
        }
    }

    @Inject(method = "replaceWithPacketData", at = @At("HEAD"), cancellable = true)
    private void replaceWithPacketData(final int x, final int z, final FriendlyByteBuf friendlyByteBuf, final CompoundTag compoundTag, final Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> consumer, final CallbackInfoReturnable<LevelChunk> cir) {
        final SubLevelContainer container = this.sable$getPlotContainer();

        if (container.inBounds(x, z)) {
            final ChunkPos chunkPos = new ChunkPos(x, z);
            LevelChunk levelChunk = container.getChunk(chunkPos);
            if (!isValidChunk(levelChunk, x, z)) {
                if (levelChunk != null) {
                    SableChunkEventPlatform.INSTANCE.onOldChunkInvalid(levelChunk);
                    this.level.unload(levelChunk);
                }
                levelChunk = new LevelChunk(this.level, chunkPos);
                levelChunk.replaceWithPacketData(friendlyByteBuf, compoundTag, consumer);
                container.newPopulatedChunk(chunkPos, levelChunk);
            } else {
                levelChunk.replaceWithPacketData(friendlyByteBuf, compoundTag, consumer);
            }

            this.level.onChunkLoaded(chunkPos);
            this.level.getLightEngine().setLightEnabled(chunkPos, true);

            SableChunkEventPlatform.INSTANCE.onChunkPacketReplaced(levelChunk);
            cir.setReturnValue(levelChunk);
        }
    }


}
