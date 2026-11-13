package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.frogports;

import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorPackage;
import com.simibubi.create.content.kinetics.chainConveyor.ChainPackageInteractionPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into {@link com.simibubi.create.content.kinetics.chainConveyor.ChainPackageInteractionPacket} to initialise package world positions for sub-level distance checks.
 */

@Mixin(ChainPackageInteractionPacket.class)
public class ChainPackageInteractionPacketMixin {
    @Shadow @Final private BlockPos selectedConnection;

    @Shadow @Final private float chainPosition;

    @Inject(method = "applySettings(Lnet/minecraft/server/level/ServerPlayer;Lcom/simibubi/create/content/kinetics/chainConveyor/ChainConveyorBlockEntity;)V", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/kinetics/chainConveyor/ChainConveyorBlockEntity;addLoopingPackage(Lcom/simibubi/create/content/kinetics/chainConveyor/ChainConveyorPackage;)Z"))
    private void sable$initialiseLoopingWorldPosition(final ServerPlayer player, final ChainConveyorBlockEntity be, final CallbackInfo ci, @Local(name = "chainConveyorPackage") final ChainConveyorPackage chainConveyorPackage) {
        chainConveyorPackage.worldPosition = be.getPackagePosition(this.chainPosition, null);
    }

    @Inject(method = "applySettings(Lnet/minecraft/server/level/ServerPlayer;Lcom/simibubi/create/content/kinetics/chainConveyor/ChainConveyorBlockEntity;)V", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/kinetics/chainConveyor/ChainConveyorBlockEntity;addTravellingPackage(Lcom/simibubi/create/content/kinetics/chainConveyor/ChainConveyorPackage;Lnet/minecraft/core/BlockPos;)Z"))
    private void sable$initialiseTravellingWorldPosition(final ServerPlayer player, final ChainConveyorBlockEntity be, final CallbackInfo ci, @Local(name = "chainConveyorPackage") final ChainConveyorPackage chainConveyorPackage) {
        chainConveyorPackage.worldPosition = be.getPackagePosition(this.chainPosition, this.selectedConnection);
    }
}
