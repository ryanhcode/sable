package dev.ryanhcode.sable.mixin.entity.entities_stick_sublevels.packet_mixin;

import dev.ryanhcode.sable.mixinterface.entity.entities_stick_sublevels.packet_mixin.PacketActuallyInSubLevelExtension;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into {@link net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket} to add a flag marking whether the entity is actually inside a sub-level.
 */
@Mixin(ClientboundTeleportEntityPacket.class)
public class ClientboundTeleportEntityPacketMixin implements PacketActuallyInSubLevelExtension {
    /**
     * If the entity is actually in a sub-level, and not just networked aside one
     */
    @Unique
    private boolean sable$actuallyInSubLevel;

    @Inject(method = "<init>(Lnet/minecraft/network/FriendlyByteBuf;)V", at = @At("RETURN"))
    private void sable$readActuallyInSubLevel(final FriendlyByteBuf friendlyByteBuf, final CallbackInfo ci) {
        this.sable$setActuallyInSubLevel(friendlyByteBuf.readBoolean());
    }

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
}
