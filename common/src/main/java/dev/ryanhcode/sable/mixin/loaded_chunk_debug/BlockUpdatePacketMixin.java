package dev.ryanhcode.sable.mixin.loaded_chunk_debug;

import dev.ryanhcode.sable.mixinterface.loaded_chunk_debug.DebugLevelChunkExtension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientboundBlockUpdatePacket.class)
public class BlockUpdatePacketMixin {

    @Shadow
    @Final
    private BlockPos pos;

    @Inject(at = @At("HEAD"), method = "handle(Lnet/minecraft/network/protocol/game/ClientGamePacketListener;)V")
    public void preHandle(final ClientGamePacketListener pHandler, final CallbackInfo ci) {
        final ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            final LevelChunk chunk = level.getChunkSource().getChunk(
                    this.pos.getX() >> SectionPos.SECTION_BITS,
                    this.pos.getZ() >> SectionPos.SECTION_BITS,
                    false
            );
            if (chunk instanceof final DebugLevelChunkExtension ext) {
                ext.sable$setUpdated();
            }
        }
    }
}