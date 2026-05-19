package dev.ryanhcode.sable.mixin.compatibility.shouldersurfing;

import com.github.exopandora.shouldersurfing.api.model.PickContext;
import com.github.exopandora.shouldersurfing.client.ObjectPicker;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.Sable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ObjectPicker.class)
public class ObjectPickerMixin {

    @Redirect(method = "pick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;distanceTo(Lnet/minecraft/world/phys/Vec3;)D"))
    public double distanceTo(final Vec3 instance, final Vec3 vec, @Local(argsOnly = true) final Player player) {
        return Math.sqrt(Sable.HELPER.distanceSquaredWithSubLevels(player.level(), instance, vec));
    }

    @Redirect(method = "pickEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"))
    public double distanceToSq(final Vec3 instance, final Vec3 vec, @Local(argsOnly = true) final PickContext context) {
        return Sable.HELPER.distanceSquaredWithSubLevels(context.entity().level(), instance, vec);
    }
}
