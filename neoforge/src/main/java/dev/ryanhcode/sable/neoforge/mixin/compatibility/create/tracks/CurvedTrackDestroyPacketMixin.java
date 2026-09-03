package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.tracks;

import com.simibubi.create.content.trains.track.CurvedTrackDestroyPacket;
import com.simibubi.create.content.trains.track.TrackBlockEntity;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Mixin into {@link com.simibubi.create.content.trains.track.CurvedTrackDestroyPacket} so curved track destruction operates in world coordinates.
 */

@Mixin(CurvedTrackDestroyPacket.class)
public class CurvedTrackDestroyPacketMixin {

    @Redirect(method = "applySettings(Lnet/minecraft/server/level/ServerPlayer;Lcom/simibubi/create/content/trains/track/TrackBlockEntity;)V", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/trains/track/TrackBlockEntity;getBlockPos()Lnet/minecraft/core/BlockPos;"))
    protected BlockPos sable$getWorldBlockPos(final TrackBlockEntity instance) {
        return BlockPos.containing(Sable.HELPER.projectOutOfSubLevel(instance.getLevel(), instance.getBlockPos().getCenter()));
    }

}
