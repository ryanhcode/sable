package dev.ryanhcode.sable.mixin.compatibility.shouldersurfing;

import com.github.exopandora.shouldersurfing.api.util.EntityHelper;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.Sable;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(EntityHelper.class)
public class EntityHelperMixin {

    @ModifyVariable(method = "lookAtTarget", at = @At("HEAD"), index = 1, argsOnly = true)
    private static Vec3 modifyTarget(final Vec3 original, @Local(argsOnly = true) final LocalPlayer player) {
        return Sable.HELPER.projectOutOfSubLevel(player.level(), original);
    }
}
