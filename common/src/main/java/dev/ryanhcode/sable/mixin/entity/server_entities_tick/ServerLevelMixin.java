package dev.ryanhcode.sable.mixin.entity.server_entities_tick;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Mixin into {@link net.minecraft.server.level.ServerLevel} to keep chunks inside plots within entity ticking range at all times.
 */
@Mixin(ServerLevel.class)
public class ServerLevelMixin {

    @WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/DistanceManager;inEntityTickingRange(J)Z"))
    private boolean sable$wrapEntityTickingRange(final DistanceManager instance, final long l, final Operation<Boolean> original) {
        final ChunkPos chunkPos = new ChunkPos(l);
        final SubLevelContainer container = SubLevelContainer.getContainer((Level) (Object) this);

        final PlotChunkHolder chunkHolder = container.getChunkHolder(chunkPos);

        if (chunkHolder != null) {
            return true;
        }

        return original.call(instance, l);
    }

}
