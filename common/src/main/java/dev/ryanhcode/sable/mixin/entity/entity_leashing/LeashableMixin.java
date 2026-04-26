package dev.ryanhcode.sable.mixin.entity.entity_leashing;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Leashable.class)
public interface LeashableMixin {

    @WrapMethod(method = "legacyElasticRangeLeashBehaviour")
    private static <E extends Entity & Leashable> void getSubHandlerAndLeashedPos(E leashedEntity, Entity handlerEntity,
                                                                                  float distance, Operation<Void> original,
                                                                                  @Share("handlerPos") LocalRef<Vec3> handlerPos,
                                                                                  @Share("leashedPos") LocalRef<Vec3> leashedPos,
                                                                                  @Share("leashedSubLevel") LocalRef<SubLevel> leashedSubLevel) {
        final ActiveSableCompanion helper = Sable.HELPER;
        SubLevel subLevel = helper.getContaining(handlerEntity);

        if (subLevel != null)
            handlerPos.set(helper.projectOutOfSubLevel(handlerEntity.level(), handlerEntity.position()));

        leashedSubLevel.set(helper.getContaining(leashedEntity));
        if (leashedSubLevel.get() != null)
            leashedPos.set(helper.projectOutOfSubLevel(leashedEntity.level(), leashedEntity.position()));

        original.call(leashedEntity, handlerEntity, distance);
    }

    @WrapOperation(method = "legacyElasticRangeLeashBehaviour", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getX()D", ordinal = 0))
    private static double getXHandler(Entity instance, Operation<Double> original, @Share("handlerPos") LocalRef<Vec3> handlerPos) {
        if (handlerPos.get() != null)
            return handlerPos.get().x;
        return original.call(instance);
    }

    @WrapOperation(method = "legacyElasticRangeLeashBehaviour", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getX()D", ordinal = 1))
    private static double getXLeashed(Entity instance, Operation<Double> original, @Share("leashedPos") LocalRef<Vec3> leashedPos) {
        if (leashedPos.get() != null)
            return leashedPos.get().x;
        return original.call(instance);
    }

    @WrapOperation(method = "legacyElasticRangeLeashBehaviour", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getY()D", ordinal = 0))
    private static double getYHandler(Entity instance, Operation<Double> original, @Share("handlerPos") LocalRef<Vec3> handlerPos) {
        if (handlerPos.get() != null)
            return handlerPos.get().x;
        return original.call(instance);
    }

    @WrapOperation(method = "legacyElasticRangeLeashBehaviour", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getY()D", ordinal = 1))
    private static double getYLeashed(Entity instance, Operation<Double> original, @Share("leashedPos") LocalRef<Vec3> leashedPos) {
        if (leashedPos.get() != null)
            return leashedPos.get().x;
        return original.call(instance);
    }

    @WrapOperation(method = "legacyElasticRangeLeashBehaviour", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getZ()D", ordinal = 0))
    private static double getZHandler(Entity instance, Operation<Double> original, @Share("handlerPos") LocalRef<Vec3> handlerPos) {
        if (handlerPos.get() != null)
            return handlerPos.get().x;
        return original.call(instance);
    }

    @WrapOperation(method = "legacyElasticRangeLeashBehaviour", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getZ()D", ordinal = 1))
    private static double getZLeashed(Entity instance, Operation<Double> original, @Share("leashedPos") LocalRef<Vec3> leashedPos) {
        if (leashedPos.get() != null)
            return leashedPos.get().x;
        return original.call(instance);
    }

    @WrapOperation(method = "legacyElasticRangeLeashBehaviour", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"))
    private static void tranformMovment(Entity instance, Vec3 deltaMovement, Operation<Void> original, @Share("leashedSubLevel") LocalRef<SubLevel> leashedSubLevel) {
        if (leashedSubLevel.get() != null)
            deltaMovement = leashedSubLevel.get().logicalPose().transformNormalInverse(deltaMovement);
        original.call(instance, deltaMovement);
    }
}
