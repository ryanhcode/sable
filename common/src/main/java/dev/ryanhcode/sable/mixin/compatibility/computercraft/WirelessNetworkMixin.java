package dev.ryanhcode.sable.mixin.compatibility.computercraft;

import dan200.computercraft.api.network.Packet;
import dan200.computercraft.api.network.PacketReceiver;
import dan200.computercraft.api.network.PacketSender;
import dan200.computercraft.shared.peripheral.modem.wireless.WirelessNetwork;
import dev.ryanhcode.sable.Sable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adjusts CC: Tweaked wireless modem range calculations to account for sub-level distances.
 *
 * Previously, this mixin targeted an internal `Vec3.distanceToSqr` call via `@Redirect`.
 * Changes in newer CC: Tweaked versions caused an `InjectionError` (`Scanned 0 target(s)`).
 *
 * This implementation injects at `@At("HEAD")` of `tryTransmit`, reimplementing the method
 * body using Sable's sub-level distance helper and cancelling the original execution.
 */
@Mixin(WirelessNetwork.class)
public class WirelessNetworkMixin {

    @Inject(remap = false, method = "tryTransmit", at = @At("HEAD"), cancellable = true)
    private static void sable$tryTransmit(final PacketReceiver receiver,
                                          final Packet packet,
                                          final double range,
                                          final boolean interdimensional,
                                          final CallbackInfo ci) {
        final PacketSender sender = packet.sender();

        if (receiver.getLevel() == sender.getLevel()) {
            final double receiveRange = Math.max(range, receiver.getRange());
            final double distanceSq = Sable.HELPER.distanceSquaredWithSubLevels(
                    receiver.getLevel(), receiver.getPosition(), sender.getPosition());

            if (interdimensional || receiver.isInterdimensional() || distanceSq <= receiveRange * receiveRange) {
                receiver.receiveSameDimension(packet, Math.sqrt(distanceSq));
            }
        } else if (interdimensional || receiver.isInterdimensional()) {
            receiver.receiveDifferentDimension(packet);
        }

        ci.cancel();
    }
}
