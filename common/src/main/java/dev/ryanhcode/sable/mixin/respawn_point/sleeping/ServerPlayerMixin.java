package dev.ryanhcode.sable.mixin.respawn_point.sleeping;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin extends Entity {

    public ServerPlayerMixin(final EntityType<?> entityType, final Level level) {
        super(entityType, level);
    }

    @WrapMethod(method = "isReachableBedBlock")
    private boolean getPos(BlockPos blockPos, Operation<Boolean> original, @Share("pos") LocalRef<Vec3> pos) {
        final SubLevel subLevel = Sable.HELPER.getContaining(this.level(), blockPos);

        if (subLevel != null) {
            pos.set(subLevel.logicalPose().transformPositionInverse(this.position()));
        }
        return original.call(blockPos);
    }

    @WrapOperation(method = "isReachableBedBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;getX()D"))
    private double getX(ServerPlayer instance, Operation<Double> original, @Share("pos") LocalRef<Vec3> pos) {
        if (pos.get() != null)
            return pos.get().x;
        return original.call(instance);
    }

    @WrapOperation(method = "isReachableBedBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;getY()D"))
    private double getY(ServerPlayer instance, Operation<Double> original, @Share("pos") LocalRef<Vec3> pos) {
        if (pos.get() != null)
            return pos.get().y;
        return original.call(instance);
    }

    @WrapOperation(method = "isReachableBedBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;getZ()D"))
    private double getZ(ServerPlayer instance, Operation<Double> original, @Share("pos") LocalRef<Vec3> pos) {
        if (pos.get() != null)
            return pos.get().z;
        return original.call(instance);
    }
}
