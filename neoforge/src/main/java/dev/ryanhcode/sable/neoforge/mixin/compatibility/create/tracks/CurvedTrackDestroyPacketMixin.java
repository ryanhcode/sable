package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.tracks;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.trains.track.CurvedTrackDestroyPacket;
import com.simibubi.create.content.trains.track.TrackBlockEntity;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(CurvedTrackDestroyPacket.class)
public class CurvedTrackDestroyPacketMixin {

    @WrapOperation(method = "applySettings(Lnet/minecraft/server/level/ServerPlayer;Lcom/simibubi/create/content/trains/track/TrackBlockEntity;)V", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/trains/track/TrackBlockEntity;getBlockPos()Lnet/minecraft/core/BlockPos;"))
    protected BlockPos sable$getWorldBlockPos(final TrackBlockEntity instance, Operation<BlockPos> original) {
        return BlockPos.containing(Sable.HELPER.projectOutOfSubLevel(instance.getLevel(), original.call(instance).getCenter()));
    }

}
