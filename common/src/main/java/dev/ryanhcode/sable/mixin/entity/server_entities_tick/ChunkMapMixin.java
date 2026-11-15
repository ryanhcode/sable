package dev.ryanhcode.sable.mixin.entity.server_entities_tick;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Mixin into {@link net.minecraft.server.level.ChunkMap} to keep chunks inside plots within entity ticking range at all times.
 */
@Mixin(ChunkMap.class)
public class ChunkMapMixin {

    @Shadow @Final private ServerLevel level;

    @WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkMap$DistanceManager;inEntityTickingRange(J)Z"))
    private boolean sable$wrapEntityTickingRange(final ChunkMap.DistanceManager instance, final long l, final Operation<Boolean> original) {
        final ChunkPos chunkPos = new ChunkPos(l);
        final SubLevelContainer container = SubLevelContainer.getContainer(this.level);

        final PlotChunkHolder chunkHolder = container.getChunkHolder(chunkPos);

        if (chunkHolder != null) {
            return true;
        }

        return original.call(instance, l);
    }
}
