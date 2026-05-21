package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.ejector;

import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.logistics.depot.EjectorBlock;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EjectorBlock.class)
public class EjectorBlockMixin {

    @Redirect(method = "updateEntityAfterFallOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;distanceTo(Lnet/minecraft/world/phys/Vec3;)D"))
    public double distanceTo(final Vec3 instance, final Vec3 vec, @Local(argsOnly = true) final Entity entity) {
        return Math.sqrt(Sable.HELPER.distanceSquaredWithSubLevels(entity.level(), instance, vec));
    }

    @Redirect(method = "updateEntityAfterFallOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;add(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"))
    public Vec3 setPos(final Vec3 instance, final Vec3 vec, @Local(argsOnly = true) final Entity entity) {
        final Vector3d projected = Sable.HELPER.projectOutOfSubLevel(entity.level(), JOMLConversion.toJOML(instance)).add(vec.x, vec.y, vec.z);
        return JOMLConversion.toMojang(projected);
    }
}
