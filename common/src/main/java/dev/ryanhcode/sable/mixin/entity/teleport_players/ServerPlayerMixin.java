package dev.ryanhcode.sable.mixin.entity.teleport_players;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.ryanhcode.sable.Sable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.RelativeMovement;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Set;

/**
 * Mixin into {@link net.minecraft.server.level.ServerPlayer} to project coordinates from sub-levels to the global frame when the player teleports.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {

    @Shadow
    public abstract ServerLevel serverLevel();

    @WrapMethod(method = "teleportTo(DDD)V")
    public void sable$teleportTo(final double x, final double y, final double z, final Operation<Void> original) {
        final Vector3d globalPos = Sable.HELPER.projectOutOfSubLevel(this.serverLevel(), new Vector3d(x, y, z));
        original.call(globalPos.x, globalPos.y, globalPos.z);
    }

    @WrapMethod(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FF)Z")
    public boolean sable$teleportTo(final ServerLevel serverLevel, final double x, final double y, final double z, final Set<RelativeMovement> set, final float g, final float h, final Operation<Boolean> original) {
        final Vector3d globalPos = Sable.HELPER.projectOutOfSubLevel(serverLevel, new Vector3d(x, y, z));
        return original.call(serverLevel, globalPos.x, globalPos.y, globalPos.z, set, g, h);
    }
}
