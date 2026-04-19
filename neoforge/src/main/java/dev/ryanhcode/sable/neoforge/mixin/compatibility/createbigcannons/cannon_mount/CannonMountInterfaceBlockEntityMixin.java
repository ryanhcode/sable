package dev.ryanhcode.sable.neoforge.mixin.compatibility.createbigcannons.cannon_mount;

import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Pseudo
@Mixin(targets = {
        "rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountInterfaceBlockEntity$PitchInterface",
        "rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountInterfaceBlockEntity$YawInterface"
})
public class CannonMountInterfaceBlockEntityMixin {

    @SuppressWarnings("unused")
    @Inject(method = "addPropagationLocations", at = @At("RETURN"), cancellable = true)
    private void sable$makePropagationLocationsMutable(final CallbackInfoReturnable<List<BlockPos>> cir) {
        cir.setReturnValue(new ArrayList<>(cir.getReturnValue()));
    }
}