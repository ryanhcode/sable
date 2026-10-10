package dev.ryanhcode.sable.mixin.entity.entities_stick_sublevels.packet_mixin;

import dev.ryanhcode.sable.mixinterface.entity.entities_stick_sublevels.packet_mixin.PacketActuallyInSubLevelExtension;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin into {@link net.minecraft.network.protocol.game.ClientboundMoveEntityPacket.Pos} to add a flag marking whether the entity is actually inside a sub-level.
 */
@Mixin(ClientboundMoveEntityPacket.Pos.class)
public class ClientboundMoveEntityPacketPosMixin implements PacketActuallyInSubLevelExtension {
    /**
     * If the entity is actually in a sub-level, and not just networked aside one
     */
    @Unique
    private boolean sable$actuallyInSubLevel;

    @Inject(method = "write", at = @At("TAIL"))
    private void sable$writeActuallyInSubLevel(final FriendlyByteBuf friendlyByteBuf, final CallbackInfo ci) {
        friendlyByteBuf.writeBoolean(this.sable$actuallyInSubLevel);
    }

    @Override
    public void sable$setActuallyInSubLevel(final boolean actuallyInSubLevel) {
        this.sable$actuallyInSubLevel = actuallyInSubLevel;
    }

    @Override
    public boolean sable$isActuallyInSubLevel() {
        return this.sable$actuallyInSubLevel;
    }

    @Inject(method = "read", at = @At("RETURN"))
    private static void sable$readActuallyInSubLevel(final FriendlyByteBuf friendlyByteBuf, final CallbackInfoReturnable<ClientboundMoveEntityPacket.Pos> cir) {
        ((PacketActuallyInSubLevelExtension) cir.getReturnValue()).sable$setActuallyInSubLevel(friendlyByteBuf.readBoolean());
    }

}
