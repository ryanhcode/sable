package dev.ryanhcode.sable.mixin.respawn_point.sleeping;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity {

    public LivingEntityMixin(final EntityType<?> entityType, final Level level) {
        super(entityType, level);
    }

    /**
     * @author RyanH
     * @reason Sleeping on sub-levels
     */
    @Overwrite
    private void setPosToBed(final BlockPos blockPos) {
        final Vector3d coords = JOMLConversion.upFromBottomCenterOf(blockPos, 0.6875);
        this.setPos(JOMLConversion.toMojang(Sable.HELPER.projectOutOfSubLevel(this.level(), coords)));
    }

    @SuppressWarnings("UnresolvedMixinReference")
    @WrapOperation(method = {"method_18404", "lambda$stopSleeping$12"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;setPos(DDD)V"), expect = 1, require = 1)
    private void sable$stopSleeping(final LivingEntity instance, final double x, final double y, final double z, Operation<Void> original) {
        final double halfHeight = this.getBoundingBox().getYsize() / 2.0;

        final Vector3d coords = new Vector3d(x, y + halfHeight, z);
        Sable.HELPER.projectOutOfSubLevel(this.level(), coords).sub(0.0, halfHeight, 0.0);
        Vec3 pos = JOMLConversion.toMojang(coords);
        original.call(instance, pos.x, pos.y, pos.z);
    }
}
