package dev.ryanhcode.sable.mixin.compatibility.computercraft;

import com.llamalad7.mixinextras.sugar.Local;
import dan200.computercraft.api.network.PacketReceiver;
import dan200.computercraft.shared.peripheral.modem.wireless.WirelessNetwork;
import dev.ryanhcode.sable.Sable;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * ComputerCraft compatibility: make the wireless transmission distance calculation in {@link WirelessNetwork#tryTransmit} account for sub-levels.
 */
@Mixin(WirelessNetwork.class)
public class WirelessNetworkMixin {

    @Redirect(remap = false, method = "tryTransmit", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"))
    private static double getPosition(final Vec3 a, final Vec3 b, @Local(ordinal = 0, argsOnly = true) final PacketReceiver packetReceiver) {
        return Sable.HELPER.distanceSquaredWithSubLevels(packetReceiver.getLevel(), a, b);
    }

}
