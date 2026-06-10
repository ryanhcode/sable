package dev.ryanhcode.sable.mixin.entity.teleport_players;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.authlib.GameProfile;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.Set;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin extends Player {

    public ServerPlayerMixin(final Level level, final BlockPos pos, final float yRot, final GameProfile gameProfile) {
        super(level, pos, yRot, gameProfile);
    }

    @Shadow
    public abstract ServerLevel serverLevel();

    @Shadow
    public ServerGamePacketListenerImpl connection;

    @WrapMethod(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FF)Z")
    public boolean sable$teleportTo(final ServerLevel serverLevel, final double x, final double y, final double z, final Set<RelativeMovement> set, final float g, final float h, final Operation<Boolean> original) {
        final Vector3d globalPos = Sable.HELPER.projectOutOfSubLevel(serverLevel, new Vector3d(x, y, z));
        if (Sable.HELPER.isInPlotGrid(this.level(), globalPos)) {
            this.sable$logPlotGridTeleportError(globalPos);
            return false;
        }
        return original.call(serverLevel, globalPos.x, globalPos.y, globalPos.z, set, g, h);
    }
    
    @WrapMethod(method = "teleportTo(DDD)V")
    public void sable$onTeleport(final double x, final double y, final double z, final Operation<Void> original) {
        final Vector3d globalPos = Sable.HELPER.projectOutOfSubLevel(this.serverLevel(), new Vector3d(x, y, z));
        if (Sable.HELPER.isInPlotGrid(this.level(), globalPos)) {
            this.sable$logPlotGridTeleportError(globalPos);
            return;
        }
        original.call(globalPos.x, globalPos.y, globalPos.z);
    }

    @WrapMethod(method = "teleportRelative")
    public void sable$onTeleportRelative(final double dx, final double dy, final double dz, final Operation<Void> original) {
        final Vector3d globalPos = Sable.HELPER.projectOutOfSubLevel(this.serverLevel(), new Vector3d(this.getX() + dx, this.getY() + dy, this.getZ() + dz));
        if (Sable.HELPER.isInPlotGrid(this.level(), globalPos)) {
	        this.sable$logPlotGridTeleportError(globalPos);
            return;
        }
        this.connection.teleport(globalPos.x, globalPos.y, globalPos.z, this.getYRot(), this.getXRot(), RelativeMovement.ALL);
    }

    @Unique
    private void sable$logPlotGridTeleportError(final Vector3d pos) {
        Sable.LOGGER.error("Player {} tried to teleport to a position in the plot grid. From {} to {}", this.getName().getString(), this.position(), pos);
    }

}
